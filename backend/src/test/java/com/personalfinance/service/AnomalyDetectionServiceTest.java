package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.request.AnomalyFeedbackRequest;
import com.personalfinance.dto.response.AnomalyResponse;
import com.personalfinance.model.entity.AnomalyFeedback;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.AnomalyFeedbackRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class AnomalyDetectionServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private AnomalyFeedbackRepository anomalyFeedbackRepository;

  @InjectMocks private AnomalyDetectionService service;

  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).name("T").email("t@e.com").build();
  }

  private Transaction expense(String norm, String amount, LocalDate date) {
    return Transaction.builder()
        .id(UUID.randomUUID())
        .user(user)
        .description(norm)
        .normalizedDescription(norm)
        .amount(new BigDecimal(amount))
        .type(TransactionType.EXPENSE)
        .date(date)
        .build();
  }

  private void stubTransactions(List<Transaction> txs) {
    when(transactionRepository.findExpensesWithCategoryInPeriod(eq(userId), any(), any()))
        .thenReturn(txs);
  }

  @Test
  void detects_amount_outlier_against_merchant_median() {
    LocalDate now = LocalDate.now();
    Transaction outlier = expense("iFood", "380.00", now.minusDays(1));
    List<Transaction> txs =
        List.of(
            expense("iFood", "45.00", now.minusDays(40)),
            expense("iFood", "50.00", now.minusDays(30)),
            expense("iFood", "42.00", now.minusDays(20)),
            expense("iFood", "48.00", now.minusDays(10)),
            outlier);
    stubTransactions(txs);
    when(anomalyFeedbackRepository.findByUserId(userId)).thenReturn(List.of());

    List<AnomalyResponse> result = service.findAnomalies(user, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("AMOUNT_OUTLIER");
    assertThat(result.get(0).transactionId()).isEqualTo(outlier.getId());
    assertThat(result.get(0).status()).isEqualTo("OPEN");
  }

  @Test
  void no_outlier_when_history_is_too_small() {
    LocalDate now = LocalDate.now();
    List<Transaction> txs =
        List.of(
            expense("iFood", "45.00", now.minusDays(20)),
            expense("iFood", "380.00", now.minusDays(1)));
    stubTransactions(txs);
    when(anomalyFeedbackRepository.findByUserId(userId)).thenReturn(List.of());

    assertThat(service.findAnomalies(user, false)).isEmpty();
  }

  @Test
  void detects_duplicate_charge_within_window() {
    LocalDate now = LocalDate.now();
    Transaction first = expense("Netflix", "39.90", now.minusDays(10));
    Transaction dup = expense("Netflix", "39.90", now.minusDays(8));
    stubTransactions(List.of(first, dup));
    when(anomalyFeedbackRepository.findByUserId(userId)).thenReturn(List.of());

    List<AnomalyResponse> result = service.findAnomalies(user, false);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).type()).isEqualTo("DUPLICATE_CHARGE");
    assertThat(result.get(0).transactionId()).isEqualTo(dup.getId());
    assertThat(result.get(0).relatedTransactionId()).isEqualTo(first.getId());
  }

  @Test
  void no_duplicate_when_gap_exceeds_window() {
    LocalDate now = LocalDate.now();
    stubTransactions(
        List.of(
            expense("Netflix", "39.90", now.minusDays(40)),
            expense("Netflix", "39.90", now.minusDays(10))));
    when(anomalyFeedbackRepository.findByUserId(userId)).thenReturn(List.of());

    assertThat(service.findAnomalies(user, false)).isEmpty();
  }

  @Test
  void false_positive_feedback_hides_from_default_but_shows_with_includeResolved() {
    LocalDate now = LocalDate.now();
    Transaction dup1 = expense("Netflix", "39.90", now.minusDays(10));
    Transaction dup2 = expense("Netflix", "39.90", now.minusDays(9));
    stubTransactions(List.of(dup1, dup2));
    when(anomalyFeedbackRepository.findByUserId(userId))
        .thenReturn(
            List.of(
                AnomalyFeedback.builder()
                    .user(user)
                    .transactionId(dup2.getId())
                    .type("DUPLICATE_CHARGE")
                    .status("FALSE_POSITIVE")
                    .build()));

    assertThat(service.findAnomalies(user, false)).isEmpty();

    List<AnomalyResponse> withResolved = service.findAnomalies(user, true);
    assertThat(withResolved).hasSize(1);
    assertThat(withResolved.get(0).status()).isEqualTo("FALSE_POSITIVE");
  }

  @Test
  void submitFeedback_upserts_for_ownedTransaction() {
    Transaction tx = expense("iFood", "45.00", LocalDate.now());
    when(transactionRepository.findById(tx.getId())).thenReturn(Optional.of(tx));
    when(anomalyFeedbackRepository.findByTransactionIdAndType(tx.getId(), "AMOUNT_OUTLIER"))
        .thenReturn(Optional.empty());

    service.submitFeedback(
        new AnomalyFeedbackRequest(tx.getId(), "AMOUNT_OUTLIER", "ACKNOWLEDGED"), user);

    verify(anomalyFeedbackRepository).save(any(AnomalyFeedback.class));
  }

  @Test
  void submitFeedback_onForeignTransaction_isForbidden() {
    User other = User.builder().id(UUID.randomUUID()).build();
    Transaction foreign =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(other)
            .description("x")
            .amount(BigDecimal.TEN)
            .type(TransactionType.EXPENSE)
            .date(LocalDate.now())
            .build();
    when(transactionRepository.findById(foreign.getId())).thenReturn(Optional.of(foreign));

    assertThatThrownBy(
            () ->
                service.submitFeedback(
                    new AnomalyFeedbackRequest(foreign.getId(), "AMOUNT_OUTLIER", "FALSE_POSITIVE"),
                    user))
        .isInstanceOf(AccessDeniedException.class);
    verify(anomalyFeedbackRepository, never()).save(any());
  }

  @Test
  void reopen_deletes_the_feedback() {
    UUID txId = UUID.randomUUID();
    AnomalyFeedback fb =
        AnomalyFeedback.builder()
            .user(user)
            .transactionId(txId)
            .type("DUPLICATE_CHARGE")
            .status("FALSE_POSITIVE")
            .build();
    when(anomalyFeedbackRepository.findByTransactionIdAndType(txId, "DUPLICATE_CHARGE"))
        .thenReturn(Optional.of(fb));

    service.reopen(txId, "DUPLICATE_CHARGE", user);

    verify(anomalyFeedbackRepository).delete(fb);
  }
}
