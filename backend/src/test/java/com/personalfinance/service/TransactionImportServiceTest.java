package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.personalfinance.dto.response.ImportPreviewResponse;
import com.personalfinance.dto.response.ParsedTransactionDTO;
import com.personalfinance.model.entity.ImportSession;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.*;
import com.personalfinance.service.parser.DocumentTypeDetector;
import com.personalfinance.service.parser.NubankExtratoParser;
import com.personalfinance.service.parser.NubankFaturaParser;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TransactionImportServiceTest {

  @Mock private DocumentTypeDetector documentTypeDetector;
  @Mock private NubankExtratoParser extratoParser;
  @Mock private NubankFaturaParser faturaParser;
  @Mock private IncomeClassificationService incomeClassifier;
  @Mock private MerchantNormalizationService normalizationService;
  @Mock private MerchantClassificationService classificationService;
  @Mock private ImportSessionRepository importSessionRepository;
  @Mock private ReviewQueueRepository reviewQueueRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private MerchantDisplayNameRepository merchantDisplayNameRepository;

  @InjectMocks private TransactionImportService service;

  private User user;
  private ImportSession session;

  @BeforeEach
  void setUp() {
    user = User.builder().id(UUID.randomUUID()).name("Teste").build();
    session =
        ImportSession.builder()
            .id(UUID.randomUUID())
            .user(user)
            .documentType("EXTRATO")
            .status("PENDING")
            .build();
    lenient().when(importSessionRepository.findById(session.getId())).thenReturn(Optional.of(session));
    lenient().when(importSessionRepository.save(any())).thenReturn(session);
    lenient()
        .when(merchantDisplayNameRepository.findByUserIdAndNormalizedName(any(), any()))
        .thenReturn(Optional.empty());
  }

  @Test
  void confirm_persists_only_included_transactions() {
    ParsedTransactionDTO included =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 1))
            .description("Supermercado")
            .amount(BigDecimal.valueOf(150))
            .type("EXPENSE")
            .included(true)
            .needsReview(false)
            .build();
    ParsedTransactionDTO excluded =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 1))
            .description("Pagamento de fatura")
            .amount(BigDecimal.valueOf(800))
            .type("EXPENSE")
            .included(false)
            .autoClassification("INTERNAL")
            .needsReview(false)
            .build();

    service.confirm(session.getId(), List.of(included, excluded), user);

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository, times(1)).save(txCaptor.capture());
    assertThat(txCaptor.getValue().getDescription()).isEqualTo("Supermercado");
  }

  @Test
  void confirm_uses_client_data_not_cache() {
    ParsedTransactionDTO clientVersion =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 10))
            .description("Pix recebido")
            .amount(BigDecimal.valueOf(3000))
            .type("INCOME")
            .incomeType("REIMBURSEMENT")
            .included(true)
            .needsReview(false)
            .build();

    service.confirm(session.getId(), List.of(clientVersion), user);

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    assertThat(txCaptor.getValue().getIncomeType()).isEqualTo("REIMBURSEMENT");
  }

  @Test
  void confirm_does_not_persist_own_transfer_when_excluded() {
    ParsedTransactionDTO ownTransfer =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 5))
            .description("Transferência Open Banking João Silva")
            .amount(BigDecimal.valueOf(2000))
            .type("INCOME")
            .incomeType("OWN_TRANSFER")
            .autoClassification("INTERNAL")
            .included(false)
            .needsReview(false)
            .build();

    service.confirm(session.getId(), List.of(ownTransfer), user);

    verify(transactionRepository, never()).save(any());
  }

  @Test
  void confirm_creates_review_queue_entry_for_included_needs_review() {
    ParsedTransactionDTO tx =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 15))
            .description("Loja Desconhecida")
            .normalizedDescription("Loja Desconhecida")
            .amount(BigDecimal.valueOf(50))
            .type("EXPENSE")
            .included(true)
            .needsReview(true)
            .build();

    service.confirm(session.getId(), List.of(tx), user);

    verify(reviewQueueRepository, times(1)).save(any());
  }

  @Test
  void confirm_skips_review_queue_for_excluded_needs_review() {
    ParsedTransactionDTO tx =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 15))
            .description("Transação interna")
            .amount(BigDecimal.valueOf(1000))
            .type("EXPENSE")
            .included(false)
            .needsReview(true)
            .build();

    service.confirm(session.getId(), List.of(tx), user);

    verify(transactionRepository, never()).save(any());
    verify(reviewQueueRepository, never()).save(any());
  }

  @Test
  void confirm_fatura_removes_matching_bill_payment_from_extrato() {
    ImportSession faturaSession =
        ImportSession.builder()
            .id(UUID.randomUUID())
            .user(user)
            .documentType("FATURA")
            .status("PENDING")
            .periodEnd(LocalDate.of(2026, 7, 5))
            .build();
    when(importSessionRepository.findById(faturaSession.getId()))
        .thenReturn(Optional.of(faturaSession));

    Transaction billPayment =
        Transaction.builder()
            .id(UUID.randomUUID())
            .description("Pagamento de fatura 1.200,00")
            .amount(BigDecimal.valueOf(1200))
            .source("EXTRATO")
            .build();
    when(transactionRepository.findBillPaymentsByUserAndDateBetween(
            eq(user.getId()),
            eq(LocalDate.of(2026, 7, 5)),
            eq(LocalDate.of(2026, 8, 19))))
        .thenReturn(List.of(billPayment));

    ParsedTransactionDTO faturaItem =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 6, 15))
            .description("Supermercado")
            .amount(BigDecimal.valueOf(300))
            .type("EXPENSE")
            .included(true)
            .needsReview(false)
            .build();

    service.confirm(faturaSession.getId(), List.of(faturaItem), user);

    verify(transactionRepository).deleteAll(List.of(billPayment));
    verify(transactionRepository, atLeastOnce()).save(any());
  }

  @Test
  void confirm_fatura_without_bill_payment_works_normally() {
    ImportSession faturaSession =
        ImportSession.builder()
            .id(UUID.randomUUID())
            .user(user)
            .documentType("FATURA")
            .status("PENDING")
            .periodEnd(LocalDate.of(2026, 7, 5))
            .build();
    when(importSessionRepository.findById(faturaSession.getId()))
        .thenReturn(Optional.of(faturaSession));
    when(transactionRepository.findBillPaymentsByUserAndDateBetween(any(), any(), any()))
        .thenReturn(List.of());

    ParsedTransactionDTO faturaItem =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 6, 10))
            .description("iFood")
            .amount(BigDecimal.valueOf(50))
            .type("EXPENSE")
            .included(true)
            .needsReview(false)
            .build();

    service.confirm(faturaSession.getId(), List.of(faturaItem), user);

    verify(transactionRepository, never()).deleteAll(anyList());
    verify(transactionRepository, atLeastOnce()).save(any());
  }

  @Test
  void getPreview_returns_persisted_transactions_for_pending_session() throws Exception {
    ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
    ParsedTransactionDTO tx =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 6, 10))
            .description("Loja Desconhecida")
            .amount(BigDecimal.valueOf(50))
            .type("EXPENSE")
            .needsReview(true)
            .build();
    session.setPeriodStart(LocalDate.of(2026, 6, 1));
    session.setPeriodEnd(LocalDate.of(2026, 6, 30));
    session.setPreviewJson(mapper.writeValueAsString(List.of(tx)));

    ImportPreviewResponse preview = service.getPreview(session.getId(), user);

    assertThat(preview.sessionId()).isEqualTo(session.getId());
    assertThat(preview.transactions()).hasSize(1);
    assertThat(preview.transactions().get(0).getDescription()).isEqualTo("Loja Desconhecida");
    assertThat(preview.transactions().get(0).getDate()).isEqualTo(LocalDate.of(2026, 6, 10));
    assertThat(preview.reviewQueueCount()).isEqualTo(1);
  }

  @Test
  void getPreview_throws_when_session_not_pending() {
    session.setStatus("CONFIRMED");
    session.setPreviewJson("[]");

    assertThatThrownBy(() -> service.getPreview(session.getId(), user))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void getPreview_throws_when_preview_json_missing() {
    session.setStatus("PENDING");
    session.setPreviewJson(null);

    assertThatThrownBy(() -> service.getPreview(session.getId(), user))
        .isInstanceOf(IllegalStateException.class);
  }
}
