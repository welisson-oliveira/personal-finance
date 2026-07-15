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
    lenient()
        .when(importSessionRepository.findById(session.getId()))
        .thenReturn(Optional.of(session));
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
  void confirm_uses_client_data_not_stored_preview() {
    ParsedTransactionDTO clientVersion =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 10))
            .description("Mercado")
            .amount(BigDecimal.valueOf(3000))
            .type("EXPENSE")
            .budgetGroup("ESSENTIAL")
            .included(true)
            .needsReview(false)
            .build();

    service.confirm(session.getId(), List.of(clientVersion), user);

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    assertThat(txCaptor.getValue().getBudgetGroup()).isEqualTo("ESSENTIAL");
  }

  @Test
  void confirm_persists_competence_date_falling_back_to_purchase_date() {
    ParsedTransactionDTO withCompetence =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 20))
            .competenceDate(LocalDate.of(2026, 6, 7))
            .description("Compra no crédito")
            .amount(BigDecimal.valueOf(100))
            .type("EXPENSE")
            .included(true)
            .build();
    ParsedTransactionDTO withoutCompetence =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 21))
            .description("Pix")
            .amount(BigDecimal.valueOf(50))
            .type("EXPENSE")
            .included(true)
            .build();

    service.confirm(session.getId(), List.of(withCompetence, withoutCompetence), user);

    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository, times(2)).save(captor.capture());
    assertThat(captor.getAllValues())
        .anySatisfy(t -> assertThat(t.getCompetenceDate()).isEqualTo(LocalDate.of(2026, 6, 7)))
        // no competence on the DTO → falls back to the purchase date
        .anySatisfy(t -> assertThat(t.getCompetenceDate()).isEqualTo(LocalDate.of(2026, 5, 21)));
  }

  @Test
  void confirm_persists_investment_direction_and_ignored_flag() {
    ParsedTransactionDTO aporte =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 5))
            .description("Aplicação RDB")
            .amount(BigDecimal.valueOf(200))
            .type("INVESTMENT")
            .investmentDirection("CONTRIBUTION")
            .included(true)
            .build();
    ParsedTransactionDTO ownTransfer =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 5, 6))
            .description("Transferência Open Banking João")
            .amount(BigDecimal.valueOf(500))
            .type("INCOME")
            .ignored(true)
            .included(true)
            .build();

    service.confirm(session.getId(), List.of(aporte, ownTransfer), user);

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository, times(2)).save(txCaptor.capture());
    assertThat(txCaptor.getAllValues())
        .anySatisfy(t -> assertThat(t.getInvestmentDirection()).isEqualTo("CONTRIBUTION"))
        .anySatisfy(t -> assertThat(t.isIgnored()).isTrue());
  }

  @Test
  void confirm_persists_needs_review_flag_on_transaction() {
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

    ArgumentCaptor<Transaction> txCaptor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(txCaptor.capture());
    assertThat(txCaptor.getValue().isNeedsReview()).isTrue();
  }

  @Test
  void confirm_skips_excluded_transactions() {
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
    // Window is now [periodEnd - 5, periodEnd + 45].
    when(transactionRepository.findBillPaymentsByUserAndDateBetween(
            eq(user.getId()), eq(LocalDate.of(2026, 6, 30)), eq(LocalDate.of(2026, 8, 19))))
        .thenReturn(List.of(billPayment));

    // Fatura items add up to the paid amount (1200) → matched by value.
    ParsedTransactionDTO faturaItem =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 6, 15))
            .description("Supermercado")
            .amount(BigDecimal.valueOf(1200))
            .type("EXPENSE")
            .included(true)
            .needsReview(false)
            .build();

    service.confirm(faturaSession.getId(), List.of(faturaItem), user);

    verify(transactionRepository).deleteAll(List.of(billPayment));
    verify(transactionRepository, atLeastOnce()).save(any());
  }

  @Test
  void confirm_fatura_does_not_remove_bill_payment_with_different_amount() {
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

    Transaction unrelatedPayment =
        Transaction.builder()
            .id(UUID.randomUUID())
            .description("Pagamento de fatura 999,00")
            .amount(BigDecimal.valueOf(999))
            .source("EXTRATO")
            .build();
    when(transactionRepository.findBillPaymentsByUserAndDateBetween(any(), any(), any()))
        .thenReturn(List.of(unrelatedPayment));

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

    // 300 != 999 → the payment is left alone.
    verify(transactionRepository, never()).deleteAll(anyList());
  }

  @Test
  void confirm_extrato_skips_bill_payment_already_covered_by_confirmed_fatura() {
    ImportSession faturaSession =
        ImportSession.builder().id(UUID.randomUUID()).user(user).documentType("FATURA").build();
    when(importSessionRepository.findConfirmedFaturaByUserAndPeriodEndBetween(
            eq(user.getId()), any(), any()))
        .thenReturn(List.of(faturaSession));
    when(transactionRepository.sumNetByImportSession(faturaSession.getId()))
        .thenReturn(BigDecimal.valueOf(1000));

    ParsedTransactionDTO billPayment =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 7, 10))
            .description("Pagamento de fatura 1.000,00")
            .amount(BigDecimal.valueOf(1000))
            .type("EXPENSE")
            .autoClassification("INTERNAL")
            .included(true)
            .build();

    service.confirm(session.getId(), List.of(billPayment), user);

    // The extrato session already exists; the payment must NOT be persisted (fatura represents it).
    verify(transactionRepository, never()).save(any());
  }

  @Test
  void confirm_extrato_keeps_bill_payment_when_no_matching_fatura() {
    when(importSessionRepository.findConfirmedFaturaByUserAndPeriodEndBetween(
            eq(user.getId()), any(), any()))
        .thenReturn(List.of());

    ParsedTransactionDTO billPayment =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 7, 10))
            .description("Pagamento de fatura 1.000,00")
            .amount(BigDecimal.valueOf(1000))
            .type("EXPENSE")
            .autoClassification("INTERNAL")
            .included(true)
            .build();

    service.confirm(session.getId(), List.of(billPayment), user);

    verify(transactionRepository, times(1)).save(any());
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

  @Test
  void updatePreview_stores_edited_transactions_on_pending_session() {
    ParsedTransactionDTO edited =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 6, 10))
            .description("Loja Desconhecida")
            .amount(BigDecimal.valueOf(50))
            .type("EXPENSE")
            .budgetGroup("NON_ESSENTIAL")
            .notes("Meu apelido")
            .included(true)
            .needsReview(false)
            .build();

    service.updatePreview(session.getId(), List.of(edited), user);

    ArgumentCaptor<ImportSession> captor = ArgumentCaptor.forClass(ImportSession.class);
    verify(importSessionRepository).save(captor.capture());
    String json = captor.getValue().getPreviewJson();
    assertThat(json)
        .contains("Loja Desconhecida")
        .contains("NON_ESSENTIAL")
        .contains("Meu apelido");
  }

  @Test
  void updatePreview_round_trips_through_getPreview() {
    ParsedTransactionDTO edited =
        ParsedTransactionDTO.builder()
            .date(LocalDate.of(2026, 6, 12))
            .description("Assinatura")
            .amount(BigDecimal.valueOf(29))
            .type("EXPENSE")
            .budgetGroup("NON_ESSENTIAL")
            .included(true)
            .needsReview(false)
            .build();
    session.setPeriodStart(LocalDate.of(2026, 6, 1));
    session.setPeriodEnd(LocalDate.of(2026, 6, 30));

    service.updatePreview(session.getId(), List.of(edited), user);
    // The saved snapshot is what a later resume reads back.
    ImportPreviewResponse resumed = service.getPreview(session.getId(), user);

    assertThat(resumed.transactions()).hasSize(1);
    assertThat(resumed.transactions().get(0).getBudgetGroup()).isEqualTo("NON_ESSENTIAL");
  }

  @Test
  void updatePreview_throws_when_session_not_pending() {
    session.setStatus("CONFIRMED");

    assertThatThrownBy(() -> service.updatePreview(session.getId(), List.of(), user))
        .isInstanceOf(IllegalStateException.class);
    verify(importSessionRepository, never()).save(any());
  }

  @Test
  void updatePreview_throws_when_session_belongs_to_another_user() {
    User other = User.builder().id(UUID.randomUUID()).name("Outro").build();

    assertThatThrownBy(() -> service.updatePreview(session.getId(), List.of(), other))
        .isInstanceOf(IllegalArgumentException.class);
    verify(importSessionRepository, never()).save(any());
  }
}
