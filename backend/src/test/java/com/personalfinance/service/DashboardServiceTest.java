package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.MerchantRuleRepository;
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
class DashboardServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private MerchantRuleRepository merchantRuleRepository;

  @InjectMocks private DashboardService service;

  @Test
  void monthly_returns_correct_saldo() {
    UUID userId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 5, 1);
    LocalDate end = LocalDate.of(2026, 5, 31);

    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "INCOME", start, end))
        .thenReturn(new BigDecimal("3000.00"));
    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "REIMBURSEMENT", start, end))
        .thenReturn(new BigDecimal("500.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("1200.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("800.00"));
    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.EXPENSE, "INVESTMENT", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "INVESTMENT", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end))
        .thenReturn(List.of());
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    when(transactionRepository.countExpensesInPeriod(userId, start, end)).thenReturn(10L);
    when(transactionRepository.countPixEnviadosInPeriod(userId, start, end)).thenReturn(2L);
    when(transactionRepository.countPixRecebidosInPeriod(userId, start, end)).thenReturn(3L);

    DashboardResponse result = service.getMonthly(userId, 2026, 5);

    assertThat(result.getReceitaBruta()).isEqualByComparingTo("3000.00");
    assertThat(result.getReembolsos()).isEqualByComparingTo("500.00");
    assertThat(result.getReceitaReal()).isEqualByComparingTo("3000.00");
    assertThat(result.getDespesasEssenciais()).isEqualByComparingTo("1200.00");
    assertThat(result.getDespesasNaoEssenciais()).isEqualByComparingTo("800.00");
    assertThat(result.getTotalDespesas()).isEqualByComparingTo("2000.00");
    assertThat(result.getSaldo()).isEqualByComparingTo("1000.00");
  }

  @Test
  void monthly_calculates_percentages_correctly() {
    UUID userId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 5, 1);
    LocalDate end = LocalDate.of(2026, 5, 31);

    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "INCOME", start, end))
        .thenReturn(new BigDecimal("4000.00"));
    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "REIMBURSEMENT", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("2000.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("1200.00"));
    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.EXPENSE, "INVESTMENT", start, end))
        .thenReturn(new BigDecimal("800.00"));
    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "INVESTMENT", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end))
        .thenReturn(List.of());
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    when(transactionRepository.countExpensesInPeriod(userId, start, end)).thenReturn(0L);
    when(transactionRepository.countPixEnviadosInPeriod(userId, start, end)).thenReturn(0L);
    when(transactionRepository.countPixRecebidosInPeriod(userId, start, end)).thenReturn(0L);

    DashboardResponse result = service.getMonthly(userId, 2026, 5);

    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("50.00");
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo("30.00");
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("20.00");

    BigDecimal soma =
        result
            .getPercentualEssenciais()
            .add(result.getPercentualNaoEssenciais())
            .add(result.getPercentualInvestimentos());
    assertThat(soma).isLessThanOrEqualTo(new BigDecimal("100.00"));
  }

  @Test
  void monthly_with_zero_income_returns_zero_percentages() {
    UUID userId = UUID.randomUUID();
    LocalDate start = LocalDate.of(2026, 5, 1);
    LocalDate end = LocalDate.of(2026, 5, 31);

    when(transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            any(), any(), any(), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(any(), any(), any(), any()))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.findExpensesWithCategoryInPeriod(any(), any(), any()))
        .thenReturn(List.of());
    when(merchantRuleRepository.findAllVisibleToUser(any())).thenReturn(List.of());
    when(transactionRepository.countExpensesInPeriod(any(), any(), any())).thenReturn(0L);
    when(transactionRepository.countPixEnviadosInPeriod(any(), any(), any())).thenReturn(0L);
    when(transactionRepository.countPixRecebidosInPeriod(any(), any(), any())).thenReturn(0L);

    DashboardResponse result = service.getMonthly(userId, 2026, 5);

    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo(BigDecimal.ZERO);
  }
}
