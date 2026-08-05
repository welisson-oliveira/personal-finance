package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.MonthlyPointResponse;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @InjectMocks private ReportService service;

  private final UUID userId = UUID.randomUUID();

  @Test
  void monthly_evolution_returns_one_point_per_month_with_balance() {
    when(transactionRepository.sumIncomeByUserIdAndDateBetween(eq(userId), any(), any()))
        .thenReturn(new BigDecimal("5000.00"));
    when(transactionRepository.sumAllExpenseByUserIdAndDateBetween(eq(userId), any(), any()))
        .thenReturn(new BigDecimal("3000.00"));

    List<MonthlyPointResponse> points = service.monthlyEvolution(userId, 3);

    assertThat(points).hasSize(3);
    assertThat(points).allSatisfy(p -> assertThat(p.receita()).isEqualByComparingTo("5000.00"));
    assertThat(points).allSatisfy(p -> assertThat(p.despesa()).isEqualByComparingTo("3000.00"));
    assertThat(points).allSatisfy(p -> assertThat(p.saldo()).isEqualByComparingTo("2000.00"));
  }

  @Test
  void monthly_evolution_clamps_span_to_at_most_24_months() {
    when(transactionRepository.sumIncomeByUserIdAndDateBetween(any(), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumAllExpenseByUserIdAndDateBetween(any(), any(), any()))
        .thenReturn(BigDecimal.ZERO);

    assertThat(service.monthlyEvolution(userId, 100)).hasSize(24);
    assertThat(service.monthlyEvolution(userId, 0)).hasSize(1);
  }

  @Test
  void category_breakdown_groups_and_sorts_desc_using_user_share() {
    Category alim = Category.builder().id(UUID.randomUUID()).name("Alimentação").build();
    Category transp = Category.builder().id(UUID.randomUUID()).name("Transporte").build();

    Transaction t1 = tx(alim, new BigDecimal("100"), false, null);
    Transaction t2 = tx(alim, new BigDecimal("50"), false, null);
    // shared transaction: uses userShare (30), not amount (300)
    Transaction t3 = tx(transp, new BigDecimal("300"), true, new BigDecimal("30"));

    when(transactionRepository.findExpensesWithCategoryInPeriod(eq(userId), any(), any()))
        .thenReturn(List.of(t1, t2, t3));

    List<CategoryTotalResponse> result = service.categoryBreakdown(userId, 2026, 5);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).categoryName()).isEqualTo("Alimentação");
    assertThat(result.get(0).total()).isEqualByComparingTo("150");
    assertThat(result.get(1).categoryName()).isEqualTo("Transporte");
    assertThat(result.get(1).total()).isEqualByComparingTo("30");
  }

  @Test
  void category_breakdown_computes_month_over_month_delta() {
    Category alim = Category.builder().id(UUID.randomUUID()).name("Alimentação").build();

    // Current month (May): 100; previous month (April): 50 → delta = +100%
    when(transactionRepository.findExpensesWithCategoryInPeriod(
            eq(userId), eq(LocalDate.of(2026, 5, 1)), any()))
        .thenReturn(List.of(tx(alim, new BigDecimal("100"), false, null)));
    when(transactionRepository.findExpensesWithCategoryInPeriod(
            eq(userId), eq(LocalDate.of(2026, 4, 1)), any()))
        .thenReturn(List.of(tx(alim, new BigDecimal("50"), false, null)));

    List<CategoryTotalResponse> result = service.categoryBreakdown(userId, 2026, 5);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).total()).isEqualByComparingTo("100");
    assertThat(result.get(0).previousTotal()).isEqualByComparingTo("50");
    assertThat(result.get(0).deltaPercent()).isEqualByComparingTo("100.0");
  }

  @Test
  void category_breakdown_delta_is_null_when_no_previous_base() {
    Category alim = Category.builder().id(UUID.randomUUID()).name("Alimentação").build();

    when(transactionRepository.findExpensesWithCategoryInPeriod(
            eq(userId), eq(LocalDate.of(2026, 5, 1)), any()))
        .thenReturn(List.of(tx(alim, new BigDecimal("100"), false, null)));
    when(transactionRepository.findExpensesWithCategoryInPeriod(
            eq(userId), eq(LocalDate.of(2026, 4, 1)), any()))
        .thenReturn(List.of());

    List<CategoryTotalResponse> result = service.categoryBreakdown(userId, 2026, 5);

    assertThat(result.get(0).previousTotal()).isEqualByComparingTo("0");
    assertThat(result.get(0).deltaPercent()).isNull();
  }

  @Test
  void top_expenses_ranks_by_effective_amount_desc_and_respects_limit() {
    Category alim = Category.builder().id(UUID.randomUUID()).name("Alimentação").build();
    Transaction small = tx(alim, new BigDecimal("100"), false, null);
    // shared: effective is userShare (30), not amount (300)
    Transaction shared = tx(alim, new BigDecimal("300"), true, new BigDecimal("30"));
    Transaction big = tx(alim, new BigDecimal("200"), false, null);

    when(transactionRepository.findExpensesWithCategoryInPeriod(eq(userId), any(), any()))
        .thenReturn(List.of(small, shared, big));

    var result = service.topExpenses(userId, 2026, 5, 2);

    assertThat(result).hasSize(2);
    assertThat(result.get(0).amount()).isEqualByComparingTo("200");
    assertThat(result.get(1).amount()).isEqualByComparingTo("100");
    assertThat(result.get(0).categoryName()).isEqualTo("Alimentação");
  }

  private Transaction tx(
      Category category, BigDecimal amount, boolean shared, BigDecimal userShare) {
    return Transaction.builder()
        .id(UUID.randomUUID())
        .category(category)
        .amount(amount)
        .type(TransactionType.EXPENSE)
        .shared(shared)
        .userShare(userShare)
        .date(LocalDate.of(2026, 5, 10))
        .build();
  }
}
