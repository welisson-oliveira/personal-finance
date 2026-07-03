package com.personalfinance.service;

import com.personalfinance.dto.response.ImportPreviewResponse;
import com.personalfinance.dto.response.ParsedTransactionDTO;
import com.personalfinance.model.entity.*;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.*;
import com.personalfinance.service.parser.NubankExtratoParser;
import com.personalfinance.service.parser.NubankFaturaParser;
import java.io.IOException;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
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

  private final NubankExtratoParser extratoParser;
  private final NubankFaturaParser faturaParser;
  private final IncomeClassificationService incomeClassifier;
  private final MerchantNormalizationService normalizationService;
  private final MerchantClassificationService classificationService;
  private final ImportSessionRepository importSessionRepository;
  private final ReviewQueueRepository reviewQueueRepository;
  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;

  private final ConcurrentHashMap<UUID, List<ParsedTransactionDTO>> previewCache =
      new ConcurrentHashMap<>();

  @Transactional
  public ImportPreviewResponse parseAndPreview(MultipartFile file, String documentType, User user)
      throws IOException {
    String text = extractText(file.getBytes());
    String holderName = user.getName();

    List<ParsedTransactionDTO> rawTx;
    LocalDate[] period = new LocalDate[2];

    if ("EXTRATO".equals(documentType)) {
      var result = extratoParser.parse(text, holderName);
      rawTx = result.transactions();
      period[0] = result.periodStart();
      period[1] = result.periodEnd();
    } else {
      var result = faturaParser.parse(text);
      rawTx = result.transactions();
      period[0] = result.periodStart();
      period[1] = result.periodEnd();
    }

    for (ParsedTransactionDTO tx : rawTx) {
      if ("INCOME".equals(tx.getType())) {
        incomeClassifier.classify(tx, user.getId(), holderName);
      } else {
        tx.setIncomeType(null);
      }
      String normalized = normalizationService.normalize(tx.getDescription());
      tx.setNormalizedDescription(normalized);
      ClassificationResult cr = classificationService.classify(normalized, user.getId());
      if (cr.isKnown()) {
        tx.setCategoryId(cr.getCategoryId());
        tx.setCategoryName(cr.getCategoryName());
        tx.setBudgetGroup(cr.getExpenseType());
        tx.setNeedsReview(!cr.isAutoClassifiable());
      } else {
        tx.setNeedsReview(true);
      }
    }

    List<ParsedTransactionDTO> visible =
        rawTx.stream()
            .filter(tx -> !"OWN_TRANSFER".equals(tx.getIncomeType()))
            .filter(tx -> !"INVESTMENT".equals(tx.getIncomeType()))
            .toList();

    ImportSession session =
        importSessionRepository.save(
            ImportSession.builder()
                .user(user)
                .documentType(documentType)
                .fileName(
                    file.getOriginalFilename() != null ? file.getOriginalFilename() : "upload.pdf")
                .periodStart(period[0])
                .periodEnd(period[1])
                .status("PENDING")
                .build());

    previewCache.put(session.getId(), visible);

    int reviewCount = (int) visible.stream().filter(ParsedTransactionDTO::isNeedsReview).count();

    return new ImportPreviewResponse(
        session.getId(), documentType, period[0], period[1], visible, reviewCount);
  }

  @Transactional
  public void confirm(UUID sessionId, User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));

    List<ParsedTransactionDTO> txList = previewCache.getOrDefault(sessionId, List.of());

    for (ParsedTransactionDTO dto : txList) {
      Category category = null;
      if (dto.getCategoryId() != null) {
        category = categoryRepository.findById(dto.getCategoryId()).orElse(null);
      }

      Transaction tx =
          Transaction.builder()
              .user(user)
              .importSession(session)
              .description(dto.getDescription())
              .normalizedDescription(dto.getNormalizedDescription())
              .amount(dto.getAmount())
              .type(TransactionType.valueOf(dto.getType()))
              .incomeType(dto.getIncomeType())
              .budgetGroup(dto.getBudgetGroup())
              .date(dto.getDate())
              .notes(dto.getNotes())
              .category(category)
              .source(session.getDocumentType().equals("EXTRATO") ? "EXTRATO" : "FATURA")
              .cardHolder(dto.getCardHolder())
              .installmentInfo(dto.getInstallmentInfo())
              .build();
      transactionRepository.save(tx);

      if (dto.isNeedsReview()) {
        reviewQueueRepository.save(
            ReviewQueue.builder()
                .user(user)
                .importSession(session)
                .rawDescription(dto.getDescription())
                .normalizedDescription(dto.getNormalizedDescription())
                .amount(dto.getAmount())
                .transactionDate(dto.getDate())
                .status("PENDING")
                .build());
      }
    }

    session.setStatus("CONFIRMED");
    importSessionRepository.save(session);
    previewCache.remove(sessionId);
  }

  @Transactional
  public void cancel(UUID sessionId, User user) {
    ImportSession session =
        importSessionRepository
            .findById(sessionId)
            .filter(s -> s.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Import session not found"));
    session.setStatus("CANCELLED");
    importSessionRepository.save(session);
    previewCache.remove(sessionId);
  }

  public List<ImportSession> getHistory(UUID userId) {
    return importSessionRepository.findByUserIdOrderByCreatedAtDesc(userId);
  }

  private String extractText(byte[] pdfBytes) throws IOException {
    try (PDDocument doc = Loader.loadPDF(pdfBytes)) {
      return new PDFTextStripper().getText(doc);
    }
  }
}
