package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.dto.response.DashboardResponse.Insights;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.MerchantRuleRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
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
  @Mock private BudgetGoalService budgetGoalService;

  @InjectMocks private DashboardService service;

  private static final LocalDate START = LocalDate.of(2026, 5, 1);
  private static final LocalDate END = LocalDate.of(2026, 5, 31);
  private static final LocalDate HIST_START = LocalDate.of(2026, 2, 1);
  private static final LocalDate HIST_END = LocalDate.of(2026, 4, 30);

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
    // By default the whole-month expense total equals the two groups (no ungrouped expenses).
    when(transactionRepository.sumAllExpenseByUserIdAndDateBetween(userId, START, END))
        .thenReturn(new BigDecimal(essential).add(new BigDecimal(nonEssential)));
  }

  private void stubInvestments(UUID userId, String contribution, String redemption) {
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "CONTRIBUTION", START, END))
        .thenReturn(new BigDecimal(contribution));
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "REDEMPTION", START, END))
        .thenReturn(new BigDecimal(redemption));
  }

  /** Empty current-month expenses + empty insights history/rules/goals for the fixed May window. */
  private void stubInsights(UUID userId) {
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(List.of());
    stubInsightsHistoryEmpty(userId, YearMonth.of(2026, 5));
  }

  /** History window, merchant rules and budget goals empty for the month {@code ym}. */
  private void stubInsightsHistoryEmpty(UUID userId, YearMonth ym) {
    when(transactionRepository.findExpensesWithCategoryInPeriod(
            userId, ym.minusMonths(3).atDay(1), ym.minusMonths(1).atEndOfMonth()))
        .thenReturn(List.of());
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    when(budgetGoalService.findAll(userId, ym.getYear(), ym.getMonthValue())).thenReturn(List.of());
  }

  @Test
  void monthly_returns_entradas_totals_and_resultado() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "3000.00");
    stubExpenses(userId, "1200.00", "800.00");
    stubInvestments(userId, "0", "0");
    stubInsights(userId);

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    assertThat(result.getEntradas()).isEqualByComparingTo("3000.00");
    assertThat(result.getDespesasEssenciais()).isEqualByComparingTo("1200.00");
    assertThat(result.getDespesasNaoEssenciais()).isEqualByComparingTo("800.00");
    assertThat(result.getTotalDespesas()).isEqualByComparingTo("2000.00");
    assertThat(result.getResultado()).isEqualByComparingTo("1000.00");
  }

  @Test
  void monthly_computes_saldo_acumulado_despesas_sem_grupo_and_ignored_bill_payments() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "1000.00", "500.00"); // grouped expenses = 1500
    stubInvestments(userId, "0", "0");
    stubInsights(userId);
    // Whole-month expenses (2000) exceed the grouped ones (1500) → 500 ungrouped (ex.: transition
    // bill).
    when(transactionRepository.sumAllExpenseByUserIdAndDateBetween(userId, START, END))
        .thenReturn(new BigDecimal("2000.00"));
    when(transactionRepository.sumAccumulatedBalanceBetween(userId, LocalDate.of(1970, 1, 1), END))
        .thenReturn(new BigDecimal("3200.00"));
    when(transactionRepository.sumIgnoredBillPaymentsInPeriod(userId, START, END))
        .thenReturn(new BigDecimal("750.00"));

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    assertThat(result.getTotalDespesas()).isEqualByComparingTo("2000.00");
    assertThat(result.getDespesasSemGrupo()).isEqualByComparingTo("500.00");
    // resultado now nets ALL expenses: 5000 − 2000
    assertThat(result.getResultado()).isEqualByComparingTo("3000.00");
    assertThat(result.getSaldoAcumulado()).isEqualByComparingTo("3200.00");
    assertThat(result.getPagamentosFaturaIgnorados()).isEqualByComparingTo("750.00");
  }

  @Test
  void monthly_calculates_percentages_correctly() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "4000.00");
    stubExpenses(userId, "2000.00", "1200.00");
    stubInvestments(userId, "800.00", "0");
    stubInsights(userId);

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
    stubInsights(userId);

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
    stubInsights(userId);

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
    stubInsights(userId);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    assertThat(result.getRendaBase()).isEqualByComparingTo("5000.00");
    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("50.00");
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo("30.00");
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("20.00");
  }

  @Test
  void monthly_builds_5030_20_breakdown_by_category_biggest_first() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "1700.00", "300.00");
    stubInvestments(userId, "0", "0");

    Category moradia = Category.builder().id(UUID.randomUUID()).name("Moradia").build();
    Category comida = Category.builder().id(UUID.randomUUID()).name("Comida").build();
    Category lazer = Category.builder().id(UUID.randomUUID()).name("Lazer").build();
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(
            List.of(
                expense("ESSENTIAL", comida, "500"),
                expense("ESSENTIAL", moradia, "1000"),
                expense("ESSENTIAL", null, "200"),
                expense("NON_ESSENTIAL", lazer, "300")));
    stubInsightsHistoryEmpty(userId, YearMonth.of(2026, 5));

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    List<CategoryTotalResponse> ess = result.getBreakdown().getEssenciais();
    assertThat(ess)
        .extracting(CategoryTotalResponse::categoryName)
        .containsExactly("Moradia", "Comida", "Sem categoria");
    assertThat(ess.get(0).total()).isEqualByComparingTo("1000");
    assertThat(ess.get(2).total()).isEqualByComparingTo("200");
    assertThat(result.getBreakdown().getNaoEssenciais())
        .singleElement()
        .satisfies(c -> assertThat(c.categoryName()).isEqualTo("Lazer"));
  }

  private Transaction expense(String budgetGroup, Category category, String amount) {
    return tx(
        TransactionType.EXPENSE, budgetGroup, category, null, amount, LocalDate.of(2026, 5, 10));
  }

  private Transaction tx(
      TransactionType type,
      String budgetGroup,
      Category category,
      String normalized,
      String amount,
      LocalDate date) {
    return Transaction.builder()
        .id(UUID.randomUUID())
        .type(type)
        .budgetGroup(budgetGroup)
        .category(category)
        .normalizedDescription(normalized)
        .description(normalized != null ? normalized : "tx")
        .amount(new BigDecimal(amount))
        .shared(false)
        .date(date)
        .build();
  }

  @Test
  void monthly_builds_entradas_breakdown_by_category_biggest_first() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5300.00");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "0", "0");
    stubInsights(userId);

    Category salario = Category.builder().id(UUID.randomUUID()).name("Salário").build();
    Category freela = Category.builder().id(UUID.randomUUID()).name("Freelance").build();
    when(transactionRepository.findIncomeWithCategoryInPeriod(userId, START, END))
        .thenReturn(
            List.of(incomeTx(freela, "1000"), incomeTx(salario, "4000"), incomeTx(null, "300")));

    DashboardResponse result = service.getMonthly(User.builder().id(userId).build(), 2026, 5);

    assertThat(result.getEntradasBreakdown())
        .extracting(CategoryTotalResponse::categoryName)
        .containsExactly("Salário", "Freelance", "Sem categoria");
    assertThat(result.getEntradasBreakdown().get(0).total()).isEqualByComparingTo("4000");
  }

  private Transaction incomeTx(Category category, String amount) {
    return Transaction.builder()
        .id(UUID.randomUUID())
        .type(TransactionType.INCOME)
        .category(category)
        .amount(new BigDecimal(amount))
        .shared(false)
        .date(LocalDate.of(2026, 5, 10))
        .build();
  }

  @Test
  void monthly_floors_the_5030_20_base_with_the_configured_salary_every_month() {
    UUID userId = UUID.randomUUID();
    // Past month (May) with only R$4000 of income imported, but a configured salary of R$5000.
    stubIncome(userId, "4000.00");
    stubExpenses(userId, "2000.00", "1200.00");
    stubInvestments(userId, "800.00", "0");
    stubInsights(userId);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    // The salary floors the base (max(4000, 5000) = 5000), so the percentages stay sane even when
    // the month's real income wasn't fully imported.
    assertThat(result.getRendaBase()).isEqualByComparingTo("5000.00");
    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("40.00"); // 2000/5000
    assertThat(result.getPercentualNaoEssenciais()).isEqualByComparingTo("24.00"); // 1200/5000
    assertThat(result.getPercentualInvestimentos()).isEqualByComparingTo("16.00"); // 800/5000
  }

  @Test
  void monthly_keeps_the_month_income_as_base_when_it_exceeds_the_salary() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "6000.00"); // earned more than the configured salary this month
    stubExpenses(userId, "3000.00", "1800.00");
    stubInvestments(userId, "1200.00", "0");
    stubInsights(userId);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    assertThat(result.getRendaBase()).isEqualByComparingTo("6000.00"); // max(6000, 5000)
    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("50.00");
  }

  @Test
  void monthly_projects_configured_salary_as_income_floor_for_current_month() {
    UUID userId = UUID.randomUUID();
    YearMonth current = YearMonth.now();
    LocalDate start = current.atDay(1);
    LocalDate end = current.atEndOfMonth();

    // Only R$90 imported so far this month (fatura already landed, salary statement not yet).
    when(transactionRepository.sumIncomeByUserIdAndDateBetween(userId, start, end))
        .thenReturn(new BigDecimal("90.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("1200.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("800.00"));
    when(transactionRepository.sumAllExpenseByUserIdAndDateBetween(userId, start, end))
        .thenReturn(new BigDecimal("2000.00"));
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "CONTRIBUTION", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "REDEMPTION", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end))
        .thenReturn(List.of());
    stubInsightsHistoryEmpty(userId, current);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, current.getYear(), current.getMonthValue());

    // Real figures stay untouched: entradas = 90, resultado = 90 − 2000.
    assertThat(result.getEntradas()).isEqualByComparingTo("90.00");
    assertThat(result.getResultado()).isEqualByComparingTo("-1910.00");
    // Projection uses the configured salary as an income floor: 5000 − 2000.
    assertThat(result.isUsandoSalarioPrevisto()).isTrue();
    assertThat(result.getSalarioEsperado()).isEqualByComparingTo("5000.00");
    assertThat(result.getResultadoPrevisto()).isEqualByComparingTo("3000.00");
    // 50/30/20 base is the projected income: 1200/5000 = 24%.
    assertThat(result.getRendaBase()).isEqualByComparingTo("5000.00");
    assertThat(result.getPercentualEssenciais()).isEqualByComparingTo("24.00");
  }

  @Test
  void monthly_does_not_project_salary_for_a_past_month() {
    UUID userId = UUID.randomUUID();
    // START/END = May 2026, a past month relative to "now" (2026-07).
    stubIncome(userId, "90.00");
    stubExpenses(userId, "1200.00", "800.00");
    stubInvestments(userId, "0", "0");
    stubInsights(userId);

    User user = User.builder().id(userId).monthlyNetIncome(new BigDecimal("5000.00")).build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    // No projection for a closed month: resultado equals resultadoPrevisto (90 − 2000).
    assertThat(result.isUsandoSalarioPrevisto()).isFalse();
    assertThat(result.getResultado()).isEqualByComparingTo("-1910.00");
    assertThat(result.getResultadoPrevisto()).isEqualByComparingTo("-1910.00");
  }

  @Test
  void monthly_seeds_saldo_acumulado_with_opening_balance() {
    UUID userId = UUID.randomUUID();
    LocalDate openingDate = LocalDate.of(2026, 1, 1);
    stubIncome(userId, "3000.00");
    stubExpenses(userId, "1000.00", "500.00");
    stubInvestments(userId, "0", "0");
    stubInsights(userId);
    // Net cash movement since the opening date, up to the end of the selected month.
    when(transactionRepository.sumAccumulatedBalanceBetween(userId, openingDate, END))
        .thenReturn(new BigDecimal("500.00"));

    User user =
        User.builder()
            .id(userId)
            .openingBalance(new BigDecimal("2000.00"))
            .openingBalanceDate(openingDate)
            .build();
    DashboardResponse result = service.getMonthly(user, 2026, 5);

    // R$2000 opening balance + R$500 net movement since the opening date.
    assertThat(result.getSaldoAcumulado()).isEqualByComparingTo("2500.00");
  }

  // ---------------------------------------------------------------------------
  // Insights do mês
  // ---------------------------------------------------------------------------

  @Test
  void insights_ranks_the_biggest_expenses_of_the_month_first() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "0", "0");
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(
            List.of(
                tx(TransactionType.EXPENSE, "ESSENTIAL", null, "aluguel", "1500", END),
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", null, "loja", "120", END),
                tx(TransactionType.EXPENSE, "ESSENTIAL", null, "mercado", "300", END)));
    stubInsightsHistoryEmpty(userId, YearMonth.of(2026, 5));

    Insights insights =
        service.getMonthly(User.builder().id(userId).build(), 2026, 5).getInsights();

    assertThat(insights.getMaioresGastos())
        .extracting(g -> g.amount().toPlainString())
        .containsExactly("1500", "300", "120");
  }

  @Test
  void insights_compares_total_with_previous_month_and_flags_the_category_that_grew_most() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "800.00", "0"); // current total = 800
    stubInvestments(userId, "0", "0");

    Category lazer = Category.builder().id(UUID.randomUUID()).name("Lazer").build();
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(List.of(tx(TransactionType.EXPENSE, "ESSENTIAL", lazer, "cinema", "800", END)));
    // Previous month (April) had R$500 total, all in "Lazer".
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, HIST_START, HIST_END))
        .thenReturn(
            List.of(
                tx(
                    TransactionType.EXPENSE,
                    "ESSENTIAL",
                    lazer,
                    "cinema",
                    "500",
                    LocalDate.of(2026, 4, 10))));
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    when(budgetGoalService.findAll(userId, 2026, 5)).thenReturn(List.of());

    Insights insights =
        service.getMonthly(User.builder().id(userId).build(), 2026, 5).getInsights();

    assertThat(insights.getTotalMesAtual()).isEqualByComparingTo("800");
    assertThat(insights.getTotalMesAnterior()).isEqualByComparingTo("500");
    // (800 − 500) / 500 * 100 = 60%
    assertThat(insights.getVariacaoPercentual()).isEqualByComparingTo("60.0");
    assertThat(insights.getCategoriaQueMaisSubiu()).isEqualTo("Lazer");
    assertThat(insights.getCategoriaQueMaisSubiuVariacao()).isEqualByComparingTo("300");
  }

  @Test
  void insights_detects_recurring_charges_and_flags_a_new_subscription() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "0", "0");

    Category assinatura = Category.builder().id(UUID.randomUUID()).name("Assinatura").build();
    // netflix recurred (April + May), spotify is a brand-new subscription this month.
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(
            List.of(
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", null, "netflix", "40", END),
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", assinatura, "spotify", "20", END)));
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, HIST_START, HIST_END))
        .thenReturn(
            List.of(
                tx(
                    TransactionType.EXPENSE,
                    "NON_ESSENTIAL",
                    null,
                    "netflix",
                    "40",
                    LocalDate.of(2026, 4, 5))));
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    when(budgetGoalService.findAll(userId, 2026, 5)).thenReturn(List.of());

    Insights insights =
        service.getMonthly(User.builder().id(userId).build(), 2026, 5).getInsights();

    assertThat(insights.getRecorrentes())
        .extracting(DashboardResponse.RecurringItem::nome)
        .containsExactlyInAnyOrder("Netflix", "Spotify");
    assertThat(insights.getRecorrentes())
        .filteredOn(DashboardResponse.RecurringItem::nova)
        .extracting(DashboardResponse.RecurringItem::nome)
        .containsExactly("Spotify");
    assertThat(insights.getTotalRecorrente()).isEqualByComparingTo("60");
  }

  @Test
  void insights_projects_month_close_from_spend_pace_in_the_current_month() {
    UUID userId = UUID.randomUUID();
    YearMonth current = YearMonth.now();
    LocalDate start = current.atDay(1);
    LocalDate end = current.atEndOfMonth();

    when(transactionRepository.sumIncomeByUserIdAndDateBetween(userId, start, end))
        .thenReturn(new BigDecimal("5000.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", start, end))
        .thenReturn(new BigDecimal("300.00"));
    when(transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumAllExpenseByUserIdAndDateBetween(userId, start, end))
        .thenReturn(new BigDecimal("300.00"));
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "CONTRIBUTION", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "REDEMPTION", start, end))
        .thenReturn(BigDecimal.ZERO);
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end))
        .thenReturn(List.of());
    stubInsightsHistoryEmpty(userId, current);

    Insights insights =
        service
            .getMonthly(
                User.builder().id(userId).build(), current.getYear(), current.getMonthValue())
            .getInsights();

    assertThat(insights.isMesCorrente()).isTrue();
    assertThat(insights.getDiasNoMes()).isEqualTo(current.lengthOfMonth());
    // projeção = gasto * diasNoMes / diasDecorridos
    BigDecimal esperado =
        new BigDecimal("300.00")
            .multiply(BigDecimal.valueOf(insights.getDiasNoMes()))
            .divide(
                BigDecimal.valueOf(insights.getDiasDecorridos()),
                2,
                java.math.RoundingMode.HALF_UP);
    assertThat(insights.getProjecaoFechamento()).isEqualByComparingTo(esperado);
  }

  @Test
  void insights_has_no_projection_for_a_past_month() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "1000.00", "0");
    stubInvestments(userId, "0", "0");
    stubInsights(userId);

    Insights insights =
        service.getMonthly(User.builder().id(userId).build(), 2026, 5).getInsights();

    assertThat(insights.isMesCorrente()).isFalse();
    assertThat(insights.getProjecaoFechamento()).isNull();
  }

  @Test
  void insights_flags_blown_budget_goals() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "0", "0");
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(List.of());
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, HIST_START, HIST_END))
        .thenReturn(List.of());
    when(merchantRuleRepository.findAllVisibleToUser(userId)).thenReturn(List.of());
    // One goal blown (remaining < 0), one within budget → only the blown one surfaces.
    when(budgetGoalService.findAll(userId, 2026, 5))
        .thenReturn(
            List.of(
                new BudgetGoalResponse(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Delivery",
                    "🍔",
                    "#FF0000",
                    new BigDecimal("200.00"),
                    new BigDecimal("350.00"),
                    new BigDecimal("-150.00"),
                    new BigDecimal("175.00")),
                new BudgetGoalResponse(
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    "Transporte",
                    "🚌",
                    "#00FF00",
                    new BigDecimal("400.00"),
                    new BigDecimal("100.00"),
                    new BigDecimal("300.00"),
                    new BigDecimal("25.00"))));

    Insights insights =
        service.getMonthly(User.builder().id(userId).build(), 2026, 5).getInsights();

    assertThat(insights.getMetasEstouradas())
        .singleElement()
        .satisfies(
            g -> {
              assertThat(g.categoriaNome()).isEqualTo("Delivery");
              assertThat(g.gasto()).isEqualByComparingTo("350.00");
              assertThat(g.teto()).isEqualByComparingTo("200.00");
            });
  }

  @Test
  void insights_groups_small_frequent_expenses_that_added_up() {
    UUID userId = UUID.randomUUID();
    stubIncome(userId, "5000.00");
    stubExpenses(userId, "0", "0");
    stubInvestments(userId, "0", "0");
    when(transactionRepository.findExpensesWithCategoryInPeriod(userId, START, END))
        .thenReturn(
            List.of(
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", null, "cafe", "15", END),
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", null, "cafe", "18", END),
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", null, "cafe", "12", END),
                tx(TransactionType.EXPENSE, "NON_ESSENTIAL", null, "cafe", "20", END),
                // A one-off large purchase must not be grouped as "small".
                tx(TransactionType.EXPENSE, "ESSENTIAL", null, "geladeira", "1200", END)));
    stubInsightsHistoryEmpty(userId, YearMonth.of(2026, 5));

    Insights insights =
        service.getMonthly(User.builder().id(userId).build(), 2026, 5).getInsights();

    assertThat(insights.getPequenosGastos())
        .singleElement()
        .satisfies(
            g -> {
              assertThat(g.nome()).isEqualTo("Cafe");
              assertThat(g.quantidade()).isEqualTo(4);
              assertThat(g.total()).isEqualByComparingTo("65");
            });
  }
}
