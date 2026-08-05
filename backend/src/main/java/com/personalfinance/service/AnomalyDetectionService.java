package com.personalfinance.service;

import com.personalfinance.dto.request.AnomalyFeedbackRequest;
import com.personalfinance.dto.response.AnomalyResponse;
import com.personalfinance.model.entity.AnomalyFeedback;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.AnomalyFeedbackRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Stateless, deterministic anomaly detection over recent expenses. Two lean detectors:
 *
 * <ul>
 *   <li><b>AMOUNT_OUTLIER</b> — a charge much larger than the merchant's typical amount.
 *   <li><b>DUPLICATE_CHARGE</b> — the same merchant and amount charged again within a few days.
 * </ul>
 *
 * Only the user's verdict (false positive / acknowledged) is persisted; anomalies are recomputed on
 * every read, so edits and deletions are reflected automatically.
 */
@Service
@RequiredArgsConstructor
public class AnomalyDetectionService {

  static final String AMOUNT_OUTLIER = "AMOUNT_OUTLIER";
  static final String DUPLICATE_CHARGE = "DUPLICATE_CHARGE";
  static final String STATUS_OPEN = "OPEN";

  private static final int ANALYSIS_WINDOW_DAYS = 90;
  private static final int BASELINE_WINDOW_DAYS = 365;
  private static final int MIN_HISTORY = 4;
  private static final BigDecimal OUTLIER_RATIO = new BigDecimal("2.5");
  private static final BigDecimal MIN_ABS_DELTA = new BigDecimal("50");
  private static final int DUP_WINDOW_DAYS = 3;

  private final TransactionRepository transactionRepository;
  private final AnomalyFeedbackRepository anomalyFeedbackRepository;

  @Transactional(readOnly = true)
  public List<AnomalyResponse> findAnomalies(User user, boolean includeResolved) {
    UUID userId = user.getId();
    LocalDate today = LocalDate.now();
    List<Transaction> candidates =
        transactionRepository.findExpensesWithCategoryInPeriod(
            userId, today.minusDays(ANALYSIS_WINDOW_DAYS), today);
    List<Transaction> baseline =
        transactionRepository.findExpensesWithCategoryInPeriod(
            userId, today.minusDays(BASELINE_WINDOW_DAYS), today);

    Map<String, String> statusByKey =
        anomalyFeedbackRepository.findByUserId(userId).stream()
            .collect(
                Collectors.toMap(
                    f -> key(f.getTransactionId(), f.getType()),
                    AnomalyFeedback::getStatus,
                    (a, b) -> a));

    List<AnomalyResponse> anomalies = new ArrayList<>();
    anomalies.addAll(detectAmountOutliers(candidates, baseline, statusByKey));
    anomalies.addAll(detectDuplicates(candidates, statusByKey));

    return anomalies.stream()
        .filter(a -> includeResolved || STATUS_OPEN.equals(a.status()))
        .sorted(
            Comparator.comparing((AnomalyResponse a) -> STATUS_OPEN.equals(a.status()) ? 0 : 1)
                .thenComparing(AnomalyResponse::date, Comparator.reverseOrder()))
        .toList();
  }

  private List<AnomalyResponse> detectAmountOutliers(
      List<Transaction> candidates, List<Transaction> baseline, Map<String, String> statusByKey) {
    Map<String, List<Transaction>> byName =
        baseline.stream()
            .filter(t -> effectiveName(t) != null)
            .collect(Collectors.groupingBy(this::effectiveName));

    List<AnomalyResponse> out = new ArrayList<>();
    for (Transaction tx : candidates) {
      String name = effectiveName(tx);
      if (name == null) continue;
      List<BigDecimal> priors =
          byName.getOrDefault(name, List.of()).stream()
              .filter(t -> !t.getId().equals(tx.getId()))
              .map(this::effectiveAmount)
              .sorted()
              .toList();
      if (priors.size() < MIN_HISTORY) continue;

      BigDecimal median = median(priors);
      BigDecimal amount = effectiveAmount(tx);
      boolean bigEnough = amount.subtract(median).compareTo(MIN_ABS_DELTA) >= 0;
      boolean outlier = amount.compareTo(median.multiply(OUTLIER_RATIO)) >= 0;
      if (bigEnough && outlier) {
        out.add(
            build(
                tx,
                AMOUNT_OUTLIER,
                statusByKey,
                "Valor atípico",
                "Bem acima do habitual para \""
                    + displayName(tx)
                    + "\" (típico ~R$ "
                    + median.stripTrailingZeros().toPlainString()
                    + ").",
                median,
                null,
                null));
      }
    }
    return out;
  }

  private List<AnomalyResponse> detectDuplicates(
      List<Transaction> candidates, Map<String, String> statusByKey) {
    Map<String, List<Transaction>> groups =
        candidates.stream()
            .filter(t -> effectiveName(t) != null)
            .collect(
                Collectors.groupingBy(
                    t -> effectiveName(t) + "|" + effectiveAmount(t).stripTrailingZeros()));

    List<AnomalyResponse> out = new ArrayList<>();
    for (List<Transaction> group : groups.values()) {
      if (group.size() < 2) continue;
      List<Transaction> sorted =
          group.stream().sorted(Comparator.comparing(Transaction::getDate)).toList();
      for (int i = 1; i < sorted.size(); i++) {
        Transaction later = sorted.get(i);
        Transaction earlier = sorted.get(i - 1);
        long days = ChronoUnit.DAYS.between(earlier.getDate(), later.getDate());
        if (days >= 0 && days <= DUP_WINDOW_DAYS && !later.getId().equals(earlier.getId())) {
          out.add(
              build(
                  later,
                  DUPLICATE_CHARGE,
                  statusByKey,
                  "Cobrança repetida",
                  "Mesmo valor e estabelecimento cobrados novamente em até "
                      + DUP_WINDOW_DAYS
                      + " dias.",
                  null,
                  earlier.getId(),
                  earlier.getDate()));
        }
      }
    }
    return out;
  }

  @Transactional
  public void submitFeedback(AnomalyFeedbackRequest request, User user) {
    Transaction tx =
        transactionRepository
            .findById(request.transactionId())
            .orElseThrow(() -> new IllegalArgumentException("Transação não encontrada."));
    if (!tx.getUser().getId().equals(user.getId())) {
      throw new AccessDeniedException("Você não pode agir sobre esta transação.");
    }
    AnomalyFeedback fb =
        anomalyFeedbackRepository
            .findByTransactionIdAndType(request.transactionId(), request.type())
            .orElseGet(
                () ->
                    AnomalyFeedback.builder()
                        .user(user)
                        .transactionId(request.transactionId())
                        .type(request.type())
                        .build());
    fb.setStatus(request.status());
    anomalyFeedbackRepository.save(fb);
  }

  /** Reopens an anomaly by clearing the stored feedback. */
  @Transactional
  public void reopen(UUID transactionId, String type, User user) {
    anomalyFeedbackRepository
        .findByTransactionIdAndType(transactionId, type)
        .ifPresent(
            fb -> {
              if (!fb.getUser().getId().equals(user.getId())) {
                throw new AccessDeniedException("Você não pode agir sobre esta transação.");
              }
              anomalyFeedbackRepository.delete(fb);
            });
  }

  private AnomalyResponse build(
      Transaction tx,
      String type,
      Map<String, String> statusByKey,
      String title,
      String message,
      BigDecimal typicalAmount,
      UUID relatedId,
      LocalDate relatedDate) {
    String status = statusByKey.getOrDefault(key(tx.getId(), type), STATUS_OPEN);
    return new AnomalyResponse(
        tx.getId(),
        type,
        status,
        title,
        message,
        displayName(tx),
        tx.getCategory() != null ? tx.getCategory().getName() : null,
        tx.getDate(),
        effectiveAmount(tx),
        typicalAmount,
        relatedId,
        relatedDate);
  }

  private static String key(UUID transactionId, String type) {
    return transactionId + "|" + type;
  }

  private String effectiveName(Transaction t) {
    String base =
        t.getNormalizedDescription() != null && !t.getNormalizedDescription().isBlank()
            ? t.getNormalizedDescription()
            : t.getDescription();
    return base != null && !base.isBlank() ? base.toLowerCase().trim() : null;
  }

  private String displayName(Transaction t) {
    return t.getNormalizedDescription() != null && !t.getNormalizedDescription().isBlank()
        ? t.getNormalizedDescription()
        : t.getDescription();
  }

  private BigDecimal effectiveAmount(Transaction t) {
    if (t.isShared() && t.getUserShare() != null) return t.getUserShare();
    return t.getAmount();
  }

  private BigDecimal median(List<BigDecimal> sortedAsc) {
    int n = sortedAsc.size();
    if (n == 0) return BigDecimal.ZERO;
    if (n % 2 == 1) return sortedAsc.get(n / 2);
    return sortedAsc
        .get(n / 2 - 1)
        .add(sortedAsc.get(n / 2))
        .divide(BigDecimal.valueOf(2), 2, java.math.RoundingMode.HALF_UP);
  }
}
