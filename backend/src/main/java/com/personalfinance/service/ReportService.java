package com.personalfinance.service;

import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.MonthlyPointResponse;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
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
          transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
              userId, TransactionType.INCOME, "INCOME", start, end);
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

  /** Expenses grouped by category for a given month, biggest first. */
  @Transactional(readOnly = true)
  public List<CategoryTotalResponse> categoryBreakdown(UUID userId, int year, int month) {
    YearMonth ym = YearMonth.of(year, month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();

    List<Transaction> expenses =
        transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end);

    Map<UUID, BigDecimal> totals =
        expenses.stream()
            .filter(t -> t.getCategory() != null)
            .collect(
                Collectors.groupingBy(
                    t -> t.getCategory().getId(),
                    Collectors.reducing(BigDecimal.ZERO, this::effectiveAmount, BigDecimal::add)));

    Map<UUID, Transaction> byCategory =
        expenses.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.toMap(t -> t.getCategory().getId(), t -> t, (a, b) -> a));

    return totals.entrySet().stream()
        .map(
            e -> {
              var cat = byCategory.get(e.getKey()).getCategory();
              return new CategoryTotalResponse(
                  cat.getId(), cat.getName(), cat.getIcon(), cat.getColor(), e.getValue());
            })
        .sorted(Comparator.comparing(CategoryTotalResponse::total).reversed())
        .toList();
  }

  private BigDecimal effectiveAmount(Transaction t) {
    if (t.isShared() && t.getUserShare() != null) return t.getUserShare();
    return t.getAmount();
  }
}
