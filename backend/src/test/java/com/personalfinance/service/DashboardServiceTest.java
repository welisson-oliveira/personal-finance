package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.model.entity.User;
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

  private static final LocalDate START = LocalDate.of(2026, 5, 1);
  private static final LocalDate END = LocalDate.of(2026, 5, 31);

  private void stubIncome(UUID userId, String value) {
    when(transactionRepository.sumIncomeByUserIdAndDateBetween(userId, START, END))
        .thenReturn(new BigDecimal(value));
  }

  private void stubExpenses(UUID userId, String essential, String nonEssential) {
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", START, END))
        .thenReturn(new BigDecimal(essential));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", START, END))
        .thenReturn(new BigDecimal(nonEssential));
  }

  private void stubInvestments(UUID userId, String contribution, String redemption) {
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "CONTRIBUTION", START, END))
        .thenReturn(new BigDecimal(contribution));
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "REDEMPTION", START, END))
        .thenReturn(new BigDecimal(redemption));
  }

  private void stubDestaques(UUID userId) {
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(List.of());
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    when(transactionRepository.countExpensesInPeriod(userId, START, END)).thenReturn(0L);
    when(transactionRepository.countPixEnviadosInPeriod(userId, START, END)).thenReturn(0L);
    when(transactionRepository.countPixRecebidosInPeriod(userId, START, END)).thenReturn(0L);
  }

  @Test
  void monthly_returns_entradas_totals_and_resultado() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "3000.00");
    stubExpenses(userId, "1200.00", "800.00");
    stubInvestments(userId, "0", "0");
    stubDestaques(userId);

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    assertThat(result.getEntradas()).isEqualByComparingTo("3000.00");
    assertThat(result.getDespesasEssenciais()).isEqualByComparingTo("1200.00");
    assertThat(result.getDespesasNaoEssenciais()).isEqualByComparingTo("800.00");
    assertThat(result.getTotalDespesas()).isEqualByComparingTo("2000.00");
    assertThat(result.getResultado()).isEqualByComparingTo("1000.00");
  }

  @Test
  void monthly_calculates_percentages_correctly() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "4000.00");
    stubExpenses(userId, "2000.00", "1200.00");
    stubInvestments(userId, "800.00", "0");
    stubDestaques(userId);

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("50.00");
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo("30.00");
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("20.00");
  }

  @Test
  void monthly_aplicado_is_net_contribution_minus_redemption() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "1500.00", "500.00");
    stubDestaques(userId);

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    // Net of the R$500 redemption against the R$1500 contribution.
    assertThat(result.getAplicado()).isEqualByComparingTo("1000.00");
    assertThat(result.getResgatado()).isEqualByComparingTo("500.00");
    // 1000 / 5000 = 20%
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("20.00");
  }

  @Test
  void monthly_with_zero_income_returns_zero_percentages() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "0");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "0", "0");
    stubDestaques(userId);

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo(BigDecimal.ZERO);
    assertThat(result.getRendaBase()).isEqualByComparingTo(BigDecimal.ZERO);
  }

  @Test
  void monthly_falls_back_to_configured_net_income_when_month_has_no_income() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "0");
    stubExpenses(userId, "2500.00", "1500.00");
    stubInvestments(userId, "1000.00", "0");
    stubDestaques(userId);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    assertThat(result.getRendaBase()).isEqualByComparingTo("5000.00");
    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("50.00");
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo("30.00");
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("20.00");
  }

  @Test
  void monthly_prefers_registered_income_over_configured_net_income() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "4000.00");
    stubExpenses(userId, "2000.00", "1200.00");
    stubInvestments(userId, "800.00", "0");
    stubDestaques(userId);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    assertThat(result.getRendaBase()).isEqualByComparingTo("4000.00");
    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("50.00");
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo("30.00");
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("20.00");
  }
}
