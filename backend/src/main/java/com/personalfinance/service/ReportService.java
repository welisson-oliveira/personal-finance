package com.personalfinance.service;

import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.MonthlyPointResponse;
import com.personalfinance.dto.response.TopExpenseResponse;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

  private final TransactionRepository transactionRepository;

  /**
   * Real income vs total expenses (essential + non-essential) for the last {@code months} months.
   */
  @Transactional(readOnly = true)
  public List<MonthlyPointResponse> monthlyEvolution(UUID userId, int months) {
    int span = Math.min(Math.max(months, 1), 24);
    List<MonthlyPointResponse> points = new ArrayList<>();
    YearMonth current = YearMonth.now();
    for (int i = span - 1; i >= 0; i--) {
      YearMonth ym = current.minusMonths(i);
      LocalDate start = ym.atDay(1);
      LocalDate end = ym.atEndOfMonth();
      BigDecimal receita =
          transactionRepository.sumIncomeByUserIdAndDateBetween(userId, start, end);
      BigDecimal despesa =
          transactionRepository
              .sumExpenseByBudgetGroupAndDateBetween(userId, "ESSENTIAL", start, end)
              .add(
                  transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
                      userId, "NON_ESSENTIAL", start, end));
      points.add(
          new MonthlyPointResponse(
              ym.getYear(), ym.getMonthValue(), receita, despesa, receita.subtract(despesa)));
    }
    return points;
  }

  /**
   * Expenses grouped by category for a given month, biggest first, each carrying the previous
   * month's total and the month-over-month delta % (null when the previous month was zero).
   */
  @Transactional(readOnly = true)
  public List<CategoryTotalResponse> categoryBreakdown(UUID userId, int year, int month) {
    YearMonth ym = YearMonth.of(year, month);
    MonthTotals current = totalsForMonth(userId, ym);
    MonthTotals previous = totalsForMonth(userId, ym.minusMonths(1));

    List<CategoryTotalResponse> result =
        new ArrayList<>(
            current.byCategory.entrySet().stream()
                .map(
                    e -> {
                      var cat = current.meta.get(e.getKey()).getCategory();
                      BigDecimal prev =
                          previous.byCategory.getOrDefault(e.getKey(), BigDecimal.ZERO);
                      return new CategoryTotalResponse(
                          cat.getId(),
                          cat.getName(),
                          cat.getIcon(),
                          cat.getColor(),
                          e.getValue(),
                          prev,
                          deltaPercent(e.getValue(), prev));
                    })
                .sorted(Comparator.comparing(CategoryTotalResponse::total).reversed())
                .toList());

    if (current.uncategorized.compareTo(BigDecimal.ZERO) > 0) {
      result.add(
          new CategoryTotalResponse(
              null,
              "Sem categoria",
              "❓",
              "#9E9E9E",
              current.uncategorized,
              previous.uncategorized,
              deltaPercent(current.uncategorized, previous.uncategorized)));
    }

    return result;
  }

  /** The N biggest individual expenses of a month (where the money leaks), biggest first. */
  @Transactional(readOnly = true)
  public List<TopExpenseResponse> topExpenses(UUID userId, int year, int month, int limit) {
    YearMonth ym = YearMonth.of(year, month);
    return transactionRepository
        .findExpensesWithCategoryInPeriod(userId, ym.atDay(1), ym.atEndOfMonth())
        .stream()
        .sorted(Comparator.comparing(this::effectiveAmount).reversed())
        .limit(Math.max(1, limit))
        .map(
            t ->
                new TopExpenseResponse(
                    t.getId(),
                    t.getDescription(),
                    t.getCategory() != null ? t.getCategory().getName() : null,
                    t.getCategory() != null ? t.getCategory().getColor() : null,
                    t.getDate(),
                    effectiveAmount(t)))
        .toList();
  }

  /** Per-category and uncategorized expense totals for a single month. */
  private MonthTotals totalsForMonth(UUID userId, YearMonth ym) {
    List<Transaction> expenses =
        transactionRepository.findExpensesWithCategoryInPeriod(
            userId, ym.atDay(1), ym.atEndOfMonth());

    Map<UUID, BigDecimal> byCategory =
        expenses.stream()
            .filter(t -> t.getCategory() != null)
            .collect(
                Collectors.groupingBy(
                    t -> t.getCategory().getId(),
                    Collectors.reducing(BigDecimal.ZERO, this::effectiveAmount, BigDecimal::add)));

    Map<UUID, Transaction> meta =
        expenses.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.toMap(t -> t.getCategory().getId(), t -> t, (a, b) -> a));

    BigDecimal uncategorized =
        expenses.stream()
            .filter(t -> t.getCategory() == null)
            .map(this::effectiveAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

    return new MonthTotals(byCategory, meta, uncategorized);
  }

  /** Month-over-month change as a percentage; null when there's no previous base to compare to. */
  private BigDecimal deltaPercent(BigDecimal current, BigDecimal previous) {
    if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) return null;
    return current
        .subtract(previous)
        .multiply(BigDecimal.valueOf(100))
        .divide(previous, 1, RoundingMode.HALF_UP);
  }

  private BigDecimal effectiveAmount(Transaction t) {
    if (t.isShared() && t.getUserShare() != null) return t.getUserShare();
    return t.getAmount();
  }

  private record MonthTotals(
      Map<UUID, BigDecimal> byCategory, Map<UUID, Transaction> meta, BigDecimal uncategorized) {}
}
