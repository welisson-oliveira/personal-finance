package com.personalfinance.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.personalfinance.dto.response.ImportPreviewResponse;
import com.personalfinance.dto.response.ImportSessionResponse;
import com.personalfinance.dto.response.ParsedTransactionDTO;
import com.personalfinance.dto.response.PendingReconciliationDTO;
import com.personalfinance.dto.response.ReconciliationCandidateDTO;
import com.personalfinance.dto.response.ReconciliationSlotDTO;
import com.personalfinance.model.entity.*;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.*;
import com.personalfinance.service.parser.DocumentTypeDetector;
import com.personalfinance.service.parser.NubankExtratoParser;
import com.personalfinance.service.parser.NubankFaturaParser;
import java.io.IOException;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class TransactionImportService {

  private final DocumentTypeDetector documentTypeDetector;
  private final NubankExtratoParser extratoParser;
  private final NubankFaturaParser faturaParser;
  private final IncomeClassificationService incomeClassifier;
  private final MerchantNormalizationService normalizationService;
  private final MerchantClassificationService classificationService;
  private final ImportSessionRepository importSessionRepository;
  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final MerchantDisplayNameRepository merchantDisplayNameRepository;

  private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

  @Transactional
  public ImportPreviewResponse parseAndPreview(MultipartFile file, String documentType, User user)
      throws IOException {
    String text = extractText(file.getBytes());
    String resolvedType =
        (documentType != null && !documentType.isBlank())
            ? documentType
            : documentTypeDetector.detect(text);
    String holderName = user.getName();

    List<ParsedTransactionDTO> rawTx;
    LocalDate[] period = new LocalDate[2];

    // For faturas, the whole statement shares one competence month = the payment (due) date.
    LocalDate faturaDueDate = null;
    if ("EXTRATO".equals(resolvedType)) {
      var result = extratoParser.parse(text, holderName);
      rawTx = result.transactions();
      period[0] = result.periodStart();
      period[1] = result.periodEnd();
    } else {
      var result = faturaParser.parse(text);
      rawTx = result.transactions();
      period[0] = result.periodStart();
      period[1] = result.periodEnd();
      faturaDueDate = result.dueDate();
    }

    for (ParsedTransactionDTO tx : rawTx) {
      tx.setNormalizedDescription(normalizationService.normalize(tx.getDescription()));
      // Competence: fatura → payment (due) month; extrato/RDB → the purchase date itself.
      tx.setCompetenceDate(faturaDueDate != null ? faturaDueDate : tx.getDate());

      // Investments are fully classified by the parser (RDB aporte/resgate) — keep as-is.
      if ("INVESTMENT".equals(tx.getType())) {
        continue;
      }
      // Income: only known-person / own-transfer handling; income carries no category/budget group.
      if ("INCOME".equals(tx.getType())) {
        incomeClassifier.classify(tx, user.getId(), holderName);
        continue;
      }
      // Expense: classify the merchant into a category + budget group (50/30).
      ClassificationResult cr =
          classificationService.classify(tx.getNormalizedDescription(), user.getId());
      if (cr.isKnown()) {
        tx.setCategoryId(cr.getCategoryId());
        tx.setCategoryName(cr.getCategoryName());
        tx.setBudgetGroup(cr.getExpenseType());
        tx.setNeedsReview(!cr.isAutoClassifiable());
      } else {
        tx.setNeedsReview(true);
      }
    }

    ImportSession session =
        importSessionRepository.save(
            ImportSession.builder()
                .user(user)
                .documentType(resolvedType)
                .fileName(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.pdf")
                .periodStart(period[0])
                .periodEnd(period[1])
                .status("PENDING")
                .previewJson(serializePreview(rawTx))
                .build());

    int reviewCount = (int) rawTx.stream().filter(ParsedTransactionDTO::isNeedsReview).count();

    return new ImportPreviewResponse(
        session.getId(),
        resolvedType,
        period[0],
        period[1],
        rawTx,
        reviewCount,
        buildReconciliation(resolvedType, rawTx, period[1], user.getId()));
  }

  /**
   * Reopens the persisted preview of a still-pending session so the user can resume confirming an
   * import they started earlier. Only {@code PENDING} sessions carry a preview.
   */
  @Transactional(readOnly = true)
  public ImportPreviewResponse getPreview(UUID sessionId, User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));

    if (!"PENDING".equals(session.getStatus()) || session.getPreviewJson() == null) {
      throw new IllegalStateException(
          "This import is no longer pending — re-upload the PDF to review it again.");
    }

    List<ParsedTransactionDTO> txList = deserializePreview(session.getPreviewJson());
    int reviewCount = (int) txList.stream().filter(ParsedTransactionDTO::isNeedsReview).count();
    return new ImportPreviewResponse(
        session.getId(),
        session.getDocumentType(),
        session.getPeriodStart(),
        session.getPeriodEnd(),
        txList,
        reviewCount,
        buildReconciliation(
            session.getDocumentType(), txList, session.getPeriodEnd(), user.getId()));
  }

  /**
   * Persists the user's in-progress edits back onto a still-pending session, so leaving the preview
   * screen (navigating away, refreshing or closing the app) no longer discards them — resuming the
   * session via {@link #getPreview} brings the edited state back. Only {@code PENDING} sessions can
   * be updated.
   */
  @Transactional
  public void updatePreview(UUID sessionId, List<ParsedTransactionDTO> clientList, User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));

    if (!"PENDING".equals(session.getStatus())) {
      throw new IllegalStateException(
          "This import is no longer pending — its edits can no longer be saved.");
    }

    session.setPreviewJson(serializePreview(clientList));
    importSessionRepository.save(session);
  }

  private String serializePreview(List<ParsedTransactionDTO> txList) {
    try {
      return objectMapper.writeValueAsString(txList);
    } catch (Exception e) {
      throw new IllegalStateException("Failed to serialize import preview", e);
    }
  }

  private List<ParsedTransactionDTO> deserializePreview(String json) {
    try {
      return objectMapper.readValue(json, new TypeReference<List<ParsedTransactionDTO>>() {});
    } catch (Exception e) {
      throw new IllegalStateException("Failed to read import preview", e);
    }
  }

  @Transactional
  public void confirm(
      UUID sessionId,
      List<ParsedTransactionDTO> clientList,
      List<UUID> reconcileExtratoPaymentIds,
      User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));

    List<ParsedTransactionDTO> txList =
        clientList.stream().filter(ParsedTransactionDTO::isIncluded).toList();

    boolean isFatura = "FATURA".equals(session.getDocumentType());
    if (isFatura) {
      // Extrato-first order: the lump "Pagamento de fatura" is replaced by the fatura's items. The
      // user's explicit choices win; a null list falls back to automatic value matching.
      if (reconcileExtratoPaymentIds != null) {
        deleteOwnedPayments(reconcileExtratoPaymentIds, user.getId());
      } else {
        reconcileBillPayment(session, user.getId(), netTotal(txList));
      }
    }

    for (ParsedTransactionDTO dto : txList) {
      // Fatura-first order: skip a bill payment the user reconciled to a fatura (don't persist it).
      if (!isFatura && dto.isReconciled()) {
        continue;
      }

      Category category = null;
      if (dto.getCategoryId() != null) {
        category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
      }

      String effectiveName =
          dto.getNormalizedDescription() != null
              ? dto.getNormalizedDescription()
              : dto.getDescription();
      String resolvedNotes =
          merchantDisplayNameRepository
              .findByUserIdAndNormalizedName(user.getId(), effectiveName)
              .map(MerchantDisplayName::getDisplayName)
              .orElse(dto.getNotes());

      Transaction tx =
          Transaction.builder()
              .user(user)
              .importSession(session)
              .description(dto.getDescription())
              .normalizedDescription(dto.getNormalizedDescription())
              .amount(dto.getAmount())
              .type(TransactionType.valueOf(dto.getType()))
              .budgetGroup(dto.getBudgetGroup())
              .investmentDirection(dto.getInvestmentDirection())
              .ignored(dto.isIgnored())
              .needsReview(dto.isNeedsReview())
              .date(dto.getDate())
              .competenceDate(
                  dto.getCompetenceDate() != null ? dto.getCompetenceDate() : dto.getDate())
              .notes(resolvedNotes)
              .category(category)
              .source(session.getDocumentType().equals("EXTRATO") ? "EXTRATO" : "FATURA")
              .cardHolder(dto.getCardHolder())
              .installmentInfo(dto.getInstallmentInfo())
              .build();
      transactionRepository.save(tx);
    }

    session.setStatus("CONFIRMED");
    session.setPreviewJson(null);
    importSessionRepository.save(session);
  }

  @Transactional
  public void cancel(UUID sessionId, User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));
    session.setStatus("CANCELLED");
    session.setPreviewJson(null);
    importSessionRepository.save(session);
  }

  @Transactional
  public void deleteSession(UUID sessionId, User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));
    transactionRepository.deleteByImportSessionId(sessionId);
    importSessionRepository.delete(session);
  }

  public List<ImportSessionResponse> getHistory(UUID userId) {
    return importSessionRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
        .map(
            s ->
                new ImportSessionResponse(
                    s.getId(),
                    s.getDocumentType(),
                    s.getFileName(),
                    s.getPeriodStart(),
                    s.getPeriodEnd(),
                    s.getStatus(),
                    s.getCreatedAt(),
                    transactionRepository.countByImportSessionId(s.getId())))
        .toList();
  }

  /**
   * How close the bill-payment amount must be to the fatura's net total to be considered a match.
   */
  private static final BigDecimal RECONCILE_TOLERANCE = new BigDecimal("0.02");

  /**
   * Bill-payment window relative to the fatura's closing date: a few days before, well past the due
   * date.
   */
  private static final int WINDOW_BEFORE_DAYS = 5;

  private static final int WINDOW_AFTER_DAYS = 45;

  /** Candidate window for manual reconciliation (broader than the automatic match window). */
  private static final int CANDIDATE_WINDOW_DAYS = 60;

  /**
   * Builds the reconciliation suggestions shown on the preview of the second import. Read-only —
   * the user's approval/choice is applied at confirm time. Empty when there is no counterpart yet.
   */
  private List<ReconciliationSlotDTO> buildReconciliation(
      String type, List<ParsedTransactionDTO> transactions, LocalDate periodEnd, UUID userId) {
    if ("FATURA".equals(type)) {
      if (periodEnd == null) return List.of();
      List<Transaction> payments =
          transactionRepository.findBillPaymentsByUserAndDateBetween(
              userId,
              periodEnd.minusDays(CANDIDATE_WINDOW_DAYS),
              periodEnd.plusDays(CANDIDATE_WINDOW_DAYS));
      if (payments.isEmpty()) return List.of();
      BigDecimal net = netTotal(transactions);
      List<ReconciliationCandidateDTO> candidates =
          payments.stream()
              .map(
                  p ->
                      new ReconciliationCandidateDTO(
                          p.getId(), p.getDescription(), p.getAmount(), p.getDate()))
              .toList();
      UUID suggested =
          payments.stream()
              .filter(p -> amountsMatch(p.getAmount(), net))
              .map(Transaction::getId)
              .findFirst()
              .orElse(null);
      return List.of(
          new ReconciliationSlotDTO("FATURA", null, net, periodEnd, suggested, candidates));
    }

    // EXTRATO: one slot per parsed bill payment, candidates = confirmed faturas nearby.
    List<ReconciliationSlotDTO> slots = new ArrayList<>();
    for (int i = 0; i < transactions.size(); i++) {
      ParsedTransactionDTO dto = transactions.get(i);
      if (!isBillPayment(dto) || dto.getDate() == null) continue;
      List<ImportSession> faturas =
          importSessionRepository.findConfirmedFaturaByUserAndPeriodEndBetween(
              userId,
              dto.getDate().minusDays(CANDIDATE_WINDOW_DAYS),
              dto.getDate().plusDays(CANDIDATE_WINDOW_DAYS));
      if (faturas.isEmpty()) continue;
      List<ReconciliationCandidateDTO> candidates = new ArrayList<>();
      UUID suggested = null;
      for (ImportSession s : faturas) {
        BigDecimal total = transactionRepository.sumNetByImportSession(s.getId());
        candidates.add(
            new ReconciliationCandidateDTO(s.getId(), s.getFileName(), total, s.getPeriodEnd()));
        if (suggested == null && amountsMatch(dto.getAmount(), total)) suggested = s.getId();
      }
      slots.add(
          new ReconciliationSlotDTO(
              "EXTRATO", i, dto.getAmount(), dto.getDate(), suggested, candidates));
    }
    return slots;
  }

  /** Deletes the user's own extrato bill payments by id (used to apply approved fatura links). */
  private void deleteOwnedPayments(List<UUID> paymentIds, UUID userId) {
    for (UUID id : paymentIds) {
      transactionRepository
          .findById(id)
          .filter(t -> t.getUser().getId().equals(userId))
          .ifPresent(transactionRepository::delete);
    }
  }

  /** Lists still-unreconciled extrato bill payments with fatura candidates (dedicated screen). */
  @Transactional(readOnly = true)
  public List<PendingReconciliationDTO> getReconciliation(UUID userId) {
    List<PendingReconciliationDTO> result = new ArrayList<>();
    for (Transaction p : transactionRepository.findBillPaymentsByUser(userId)) {
      List<ImportSession> faturas =
          importSessionRepository.findConfirmedFaturaByUserAndPeriodEndBetween(
              userId,
              p.getDate().minusDays(CANDIDATE_WINDOW_DAYS),
              p.getDate().plusDays(CANDIDATE_WINDOW_DAYS));
      List<ReconciliationCandidateDTO> candidates = new ArrayList<>();
      UUID suggested = null;
      for (ImportSession s : faturas) {
        BigDecimal total = transactionRepository.sumNetByImportSession(s.getId());
        candidates.add(
            new ReconciliationCandidateDTO(s.getId(), s.getFileName(), total, s.getPeriodEnd()));
        if (suggested == null && amountsMatch(p.getAmount(), total)) suggested = s.getId();
      }
      result.add(
          new PendingReconciliationDTO(
              p.getId(), p.getDate(), p.getAmount(), p.getDescription(), suggested, candidates));
    }
    return result;
  }

  /**
   * Manually reconciles (substitutes) an extrato bill payment: deletes it after validating owner.
   */
  @Transactional
  public void reconcile(UUID extratoPaymentId, User user) {
    Transaction payment =
        transactionRepository
            .findById(extratoPaymentId)
            .filter(t -> t.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Payment not found"));
    transactionRepository.delete(payment);
  }

  private boolean isBillPayment(ParsedTransactionDTO dto) {
    return dto.getDescription() != null
        && dto.getDescription().toLowerCase().startsWith("pagamento de fatura");
  }

  /** Net amount an import session's items add up to (expenses minus estornos) ≈ what was paid. */
  private BigDecimal netTotal(List<ParsedTransactionDTO> items) {
    BigDecimal net = BigDecimal.ZERO;
    for (ParsedTransactionDTO dto : items) {
      if ("EXPENSE".equals(dto.getType())) net = net.add(dto.getAmount());
      else if ("INCOME".equals(dto.getType())) net = net.subtract(dto.getAmount());
    }
    return net;
  }

  private boolean amountsMatch(BigDecimal a, BigDecimal b) {
    return a != null && b != null && a.subtract(b).abs().compareTo(RECONCILE_TOLERANCE) <= 0;
  }

  /**
   * Deletes extrato bill payments matching this fatura by amount within a window around its
   * closing.
   */
  private void reconcileBillPayment(ImportSession session, UUID userId, BigDecimal faturaNet) {
    if (session.getPeriodEnd() == null) return;
    LocalDate windowStart = session.getPeriodEnd().minusDays(WINDOW_BEFORE_DAYS);
    LocalDate windowEnd = session.getPeriodEnd().plusDays(WINDOW_AFTER_DAYS);
    List<Transaction> payments =
        transactionRepository.findBillPaymentsByUserAndDateBetween(userId, windowStart, windowEnd);
    List<Transaction> matching =
        payments.stream().filter(p -> amountsMatch(p.getAmount(), faturaNet)).toList();
    if (!matching.isEmpty()) {
      transactionRepository.deleteAll(matching);
    }
  }

  private String extractText(byte[] pdfBytes) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(doc);
    }
  }
}
