package com.personalfinance.service;

import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.dto.response.DashboardResponse.GoalExceeded;
import com.personalfinance.dto.response.DashboardResponse.Insights;
import com.personalfinance.dto.response.DashboardResponse.RecurringItem;
import com.personalfinance.dto.response.DashboardResponse.SmallExpenseGroup;
import com.personalfinance.dto.response.TopExpenseResponse;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.MerchantRuleRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

  /** Individual expense at/under this value counts as "small" for the "somaram muito" insight. */
  private static final BigDecimal SMALL_EXPENSE_CEILING = new BigDecimal("50");

  /** Minimum occurrences for a group of small expenses to be worth surfacing. */
  private static final int SMALL_EXPENSE_MIN_COUNT = 3;

  private final TransactionRepository transactionRepository;
  private final MerchantRuleRepository merchantRuleRepository;
  private final BudgetGoalService budgetGoalService;

  public DashboardResponse getMonthly(User user, int year, int month) {
    UUID userId = user.getId();
    YearMonth ym = YearMonth.of(year, month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();

    BigDecimal entradas = transactionRepository.sumIncomeByUserIdAndDateBetween(userId, start, end);

    BigDecimal despesasEssenciais =
        transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", start, end);

    BigDecimal despesasNaoEssenciais =
        transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", start, end);

    // "Resultado do mês" counts ALL non-ignored expenses, not only the ones with a 50/30/20 group —
    // so a re-counted "Pagamento de fatura" (setup month) shows up even without a group.
    BigDecimal totalDespesas =
        transactionRepository.sumAllExpenseByUserIdAndDateBetween(userId, start, end);
    // Expenses that fall outside Essenciais/Não Essenciais (e.g. the transition bill payment).
    BigDecimal despesasSemGrupo =
        totalDespesas.subtract(despesasEssenciais).subtract(despesasNaoEssenciais);

    BigDecimal aportes =
        transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "CONTRIBUTION", start, end);
    BigDecimal resgatado =
        transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "REDEMPTION", start, end);
    // Net contribution for the 20% goal: what actually stayed invested this month.
    BigDecimal aplicado = aportes.subtract(resgatado);

    BigDecimal resultado = entradas.subtract(totalDespesas);

    // Running "cash in account" up to the end of the selected month. Seeded by the user's opening
    // balance (from its reference date) so it can equal the real account balance, not just the net
    // of transactions since sign-up.
    BigDecimal openingBalance =
        user.getOpeningBalance() != null ? user.getOpeningBalance() : BigDecimal.ZERO;
    LocalDate accStart =
        user.getOpeningBalanceDate() != null
            ? user.getOpeningBalanceDate()
            : LocalDate.of(1970, 1, 1);
    BigDecimal movimentado =
        transactionRepository.sumAccumulatedBalanceBetween(userId, accStart, end);
    BigDecimal saldoAcumulado =
        openingBalance.add(movimentado != null ? movimentado : BigDecimal.ZERO);
    // Bill payments still ignored this month = likely a transition-month "furo" (no matching
    // fatura).
    BigDecimal pagamentosFaturaIgnorados =
        transactionRepository.sumIgnoredBillPaymentsInPeriod(userId, start, end);

    // Expected salary (Option A): for the CURRENT calendar month, use the configured net salary as
    // an income floor until the real salary is imported — so the fatura arriving on day 8 doesn't
    // paint the month red before the day-31 statement (with the salary) is imported.
    BigDecimal salarioEsperado =
        user.getMonthlyNetIncome() != null ? user.getMonthlyNetIncome() : BigDecimal.ZERO;
    boolean mesCorrente = ym.equals(YearMonth.now());
    BigDecimal receitaProjetada = mesCorrente ? entradas.max(salarioEsperado) : entradas;
    boolean usandoSalarioPrevisto = receitaProjetada.compareTo(entradas) > 0;
    BigDecimal resultadoPrevisto = receitaProjetada.subtract(totalDespesas);

    // 50/30/20 base: the month's income floored by the configured monthly salary, EVERY month — so
    // a month whose real income wasn't (fully) imported doesn't collapse the base and blow the
    // percentages up (e.g. a fatura-heavy month with only R$281 of income imported). If neither is
    // set the base is 0 and the percentages show "—".
    BigDecimal rendaBase = entradas.max(salarioEsperado);

    BigDecimal percentualEssenciais = percent(despesasEssenciais, rendaBase);
    BigDecimal percentualNaoEssenciais = percent(despesasNaoEssenciais, rendaBase);
    BigDecimal percentualInvestimentos = percent(aplicado, rendaBase);

    // Single fetch of the month's expenses (with category + budgetGroup) feeds both the insights
    // and the 50/30/20 drill-down.
    List<Transaction> expenses =
        transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end);

    DashboardResponse.Breakdown breakdown = buildBudgetBreakdown(expenses);
    Insights insights = buildInsights(user, ym, expenses, totalDespesas);

    // "De onde veio o dinheiro": entradas agrupadas por categoria (income uses raw amount, like the
    // entradas total). Reuses the same category-total shape as the expense drill-down.
    List<CategoryTotalResponse> entradasBreakdown =
        categoriesFrom(
            transactionRepository.findIncomeWithCategoryInPeriod(userId, start, end),
            Transaction::getAmount);

    return DashboardResponse.builder()
        .year(year)
        .month(month)
        .entradas(entradas)
        .despesasEssenciais(despesasEssenciais)
        .despesasNaoEssenciais(despesasNaoEssenciais)
        .totalDespesas(totalDespesas)
        .despesasSemGrupo(despesasSemGrupo)
        .aportes(aportes)
        .resgatado(resgatado)
        .aplicado(aplicado)
        .resultado(resultado)
        .saldoAcumulado(saldoAcumulado)
        .pagamentosFaturaIgnorados(pagamentosFaturaIgnorados)
        .salarioEsperado(salarioEsperado)
        .resultadoPrevisto(resultadoPrevisto)
        .usandoSalarioPrevisto(usandoSalarioPrevisto)
        .rendaBase(rendaBase)
        .percentualEssenciais(percentualEssenciais)
        .percentualNaoEssenciais(percentualNaoEssenciais)
        .percentualInvestimentos(percentualInvestimentos)
        .breakdown(breakdown)
        .entradasBreakdown(entradasBreakdown)
        .insights(insights)
        .build();
  }

  /** Categories composing each expense bucket (ESSENTIAL / NON_ESSENTIAL), biggest first. */
  private DashboardResponse.Breakdown buildBudgetBreakdown(List<Transaction> expenses) {
    return DashboardResponse.Breakdown.builder()
        .essenciais(categoriesForGroup(expenses, "ESSENTIAL"))
        .naoEssenciais(categoriesForGroup(expenses, "NON_ESSENTIAL"))
        .build();
  }

  private List<CategoryTotalResponse> categoriesForGroup(
      List<Transaction> expenses, String budgetGroup) {
    return categoriesFrom(
        expenses.stream().filter(t -> budgetGroup.equals(t.getBudgetGroup())).toList(),
        this::effectiveAmount);
  }

  /** Groups transactions by category (biggest first) with a "Sem categoria" bucket. */
  private List<CategoryTotalResponse> categoriesFrom(
      List<Transaction> txs, java.util.function.Function<Transaction, BigDecimal> amountFn) {
    Map<UUID, BigDecimal> totals =
        txs.stream()
            .filter(t -> t.getCategory() != null)
            .collect(
                Collectors.groupingBy(
                    t -> t.getCategory().getId(),
                    Collectors.reducing(BigDecimal.ZERO, amountFn, BigDecimal::add)));

    Map<UUID, Transaction> byCategory =
        txs.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.toMap(t -> t.getCategory().getId(), t -> t, (a, b) -> a));

    List<CategoryTotalResponse> result =
        new ArrayList<>(
            totals.entrySet().stream()
                .map(
                    e -> {
                      var cat = byCategory.get(e.getKey()).getCategory();
                      return CategoryTotalResponse.of(
                          cat.getId(), cat.getName(), cat.getIcon(), cat.getColor(), e.getValue());
                    })
                .sorted(Comparator.comparing(CategoryTotalResponse::total).reversed())
                .toList());

    BigDecimal uncategorized =
        txs.stream()
            .filter(t -> t.getCategory() == null)
            .map(amountFn)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (uncategorized.compareTo(BigDecimal.ZERO) > 0) {
      result.add(CategoryTotalResponse.of(null, "Sem categoria", "❓", "#9E9E9E", uncategorized));
    }

    return result;
  }

  /**
   * "Insights do mês": an actionable reading of the month — biggest expenses, month-over-month
   * comparison, recurring charges, spend pace/projection, blown budget goals, and
   * small-but-frequent expenses. Reuses the already-fetched current-month {@code expenses} and
   * pulls the previous three months once for the comparison/recurrence blocks.
   */
  private Insights buildInsights(
      User user, YearMonth ym, List<Transaction> expenses, BigDecimal totalDespesas) {
    UUID userId = user.getId();

    // ---- 1. Maiores gastos do mês (top 5 individuais) ----
    List<TopExpenseResponse> maioresGastos =
        expenses.stream()
            .sorted(Comparator.comparing(this::effectiveAmount).reversed())
            .limit(5)
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

    // Previous three months (competence), bucketed by month, for comparison + recurrence.
    List<Transaction> history =
        transactionRepository.findExpensesWithCategoryInPeriod(
            userId, ym.minusMonths(3).atDay(1), ym.minusMonths(1).atEndOfMonth());
    Map<YearMonth, List<Transaction>> historyByMonth =
        history.stream().collect(Collectors.groupingBy(this::competenceMonth));
    List<Transaction> prevMonth = historyByMonth.getOrDefault(ym.minusMonths(1), List.of());

    // ---- 2. Comparativo com o mês passado ----
    BigDecimal totalMesAnterior =
        prevMonth.stream().map(this::effectiveAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
    BigDecimal variacaoPercentual =
        totalMesAnterior.compareTo(BigDecimal.ZERO) == 0
            ? null
            : totalDespesas
                .subtract(totalMesAnterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(totalMesAnterior, 1, RoundingMode.HALF_UP);

    Map<String, BigDecimal> catAtual = sumByCategoryName(expenses);
    Map<String, BigDecimal> catAnterior = sumByCategoryName(prevMonth);
    String categoriaQueMaisSubiu = null;
    BigDecimal categoriaQueMaisSubiuValor = null;
    BigDecimal categoriaQueMaisSubiuVariacao = null;
    BigDecimal maiorAlta = BigDecimal.ZERO;
    for (var e : catAtual.entrySet()) {
      BigDecimal alta =
          e.getValue().subtract(catAnterior.getOrDefault(e.getKey(), BigDecimal.ZERO));
      if (alta.compareTo(maiorAlta) > 0) {
        maiorAlta = alta;
        categoriaQueMaisSubiu = e.getKey();
        categoriaQueMaisSubiuValor = e.getValue();
        categoriaQueMaisSubiuVariacao = alta;
      }
    }

    // ---- 3. Assinaturas & recorrentes ----
    Set<String> assinaturaNorm =
        merchantRuleRepository.findAllVisibleToUser(userId).stream()
            .filter(r -> "Assinatura".equalsIgnoreCase(r.getSubcategory()))
            .map(r -> r.getNormalizedName().toLowerCase())
            .collect(Collectors.toSet());

    // In how many of the previous months did each normalized name appear.
    Map<String, Set<YearMonth>> priorMonthsByName = new HashMap<>();
    historyByMonth.forEach(
        (mes, txs) ->
            txs.forEach(
                t -> {
                  String key = normalizedKey(t);
                  if (key != null) {
                    priorMonthsByName.computeIfAbsent(key, k -> new HashSet<>()).add(mes);
                  }
                }));

    Map<String, List<Transaction>> currentByName =
        expenses.stream()
            .filter(t -> normalizedKey(t) != null)
            .collect(Collectors.groupingBy(this::normalizedKey));

    List<RecurringItem> recorrentes = new ArrayList<>();
    currentByName.forEach(
        (key, txs) -> {
          int priorCount = priorMonthsByName.getOrDefault(key, Set.of()).size();
          boolean assinatura =
              assinaturaNorm.contains(key)
                  || txs.stream()
                      .anyMatch(
                          t ->
                              t.getCategory() != null
                                  && "Assinatura".equalsIgnoreCase(t.getCategory().getName()));
          boolean recorrentePorHistorico = priorCount >= 1;
          if (recorrentePorHistorico || assinatura) {
            BigDecimal valor =
                txs.stream().map(this::effectiveAmount).reduce(BigDecimal.ZERO, BigDecimal::add);
            boolean nova = assinatura && priorCount == 0; // assinatura vista pela 1ª vez
            recorrentes.add(new RecurringItem(label(txs), valor, nova));
          }
        });
    recorrentes.sort(Comparator.comparing(RecurringItem::valor).reversed());
    List<RecurringItem> recorrentesTop =
        recorrentes.size() > 8 ? List.copyOf(recorrentes.subList(0, 8)) : recorrentes;
    BigDecimal totalRecorrente =
        recorrentesTop.stream().map(RecurringItem::valor).reduce(BigDecimal.ZERO, BigDecimal::add);

    // ---- 4. Ritmo do mês / projeção de fechamento (só mês corrente) ----
    boolean mesCorrente = ym.equals(YearMonth.now());
    int diasNoMes = ym.lengthOfMonth();
    int diasDecorridos = mesCorrente ? LocalDate.now().getDayOfMonth() : diasNoMes;
    BigDecimal projecaoFechamento = null;
    if (mesCorrente && diasDecorridos > 0) {
      projecaoFechamento =
          totalDespesas
              .multiply(BigDecimal.valueOf(diasNoMes))
              .divide(BigDecimal.valueOf(diasDecorridos), 2, RoundingMode.HALF_UP);
    }

    // ---- 5. Metas de orçamento estouradas ----
    List<GoalExceeded> metasEstouradas =
        budgetGoalService.findAll(userId, ym.getYear(), ym.getMonthValue()).stream()
            .filter(g -> g.remaining().compareTo(BigDecimal.ZERO) < 0)
            .sorted(Comparator.comparing(BudgetGoalResponse::percentage).reversed())
            .map(
                g ->
                    new GoalExceeded(
                        g.categoryName(),
                        g.categoryIcon(),
                        g.categoryColor(),
                        g.spent(),
                        g.amount(),
                        g.percentage()))
            .toList();

    // ---- 6. Pequenos gastos que somaram muito ----
    List<SmallExpenseGroup> pequenosGastos =
        currentByName.values().stream()
            .filter(txs -> txs.size() >= SMALL_EXPENSE_MIN_COUNT)
            .filter(
                txs ->
                    txs.stream()
                        .allMatch(t -> effectiveAmount(t).compareTo(SMALL_EXPENSE_CEILING) <= 0))
            .map(
                txs ->
                    new SmallExpenseGroup(
                        label(txs),
                        txs.size(),
                        txs.stream()
                            .map(this::effectiveAmount)
                            .reduce(BigDecimal.ZERO, BigDecimal::add)))
            .sorted(Comparator.comparing(SmallExpenseGroup::total).reversed())
            .limit(5)
            .toList();

    return Insights.builder()
        .maioresGastos(maioresGastos)
        .totalMesAtual(totalDespesas)
        .totalMesAnterior(totalMesAnterior)
        .variacaoPercentual(variacaoPercentual)
        .categoriaQueMaisSubiu(categoriaQueMaisSubiu)
        .categoriaQueMaisSubiuValor(categoriaQueMaisSubiuValor)
        .categoriaQueMaisSubiuVariacao(categoriaQueMaisSubiuVariacao)
        .recorrentes(recorrentesTop)
        .totalRecorrente(totalRecorrente)
        .mesCorrente(mesCorrente)
        .diasDecorridos(diasDecorridos)
        .diasNoMes(diasNoMes)
        .projecaoFechamento(projecaoFechamento)
        .metasEstouradas(metasEstouradas)
        .pequenosGastos(pequenosGastos)
        .build();
  }

  /**
   * Total expense per category name (null category → "Sem categoria"), respecting the user share.
   */
  private Map<String, BigDecimal> sumByCategoryName(List<Transaction> txs) {
    return txs.stream()
        .collect(
            Collectors.groupingBy(
                t -> t.getCategory() != null ? t.getCategory().getName() : "Sem categoria",
                Collectors.reducing(BigDecimal.ZERO, this::effectiveAmount, BigDecimal::add)));
  }

  private YearMonth competenceMonth(Transaction t) {
    LocalDate d = t.getCompetenceDate() != null ? t.getCompetenceDate() : t.getDate();
    return YearMonth.from(d);
  }

  /** Recurrence/merchant key: the normalized description, or null when it wasn't computed. */
  private String normalizedKey(Transaction t) {
    return t.getNormalizedDescription() != null ? t.getNormalizedDescription().toLowerCase() : null;
  }

  /** Human-friendly label for a group of transactions sharing a normalized name. */
  private String label(List<Transaction> txs) {
    Transaction t = txs.get(0);
    String name =
        t.getNormalizedDescription() != null ? t.getNormalizedDescription() : t.getDescription();
    return capitalize(name);
  }

  private BigDecimal effectiveAmount(Transaction t) {
    if (t.isShared() && t.getUserShare() != null) return t.getUserShare();
    return t.getAmount();
  }

  private BigDecimal percent(BigDecimal part, BigDecimal total) {
    if (total == null || total.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO;
    return part.multiply(BigDecimal.valueOf(100)).divide(total, 2, RoundingMode.HALF_UP);
  }

  private String capitalize(String s) {
    if (s == null || s.isEmpty()) return s;
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }
}
