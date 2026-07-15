package com.personalfinance.service;

import com.personalfinance.dto.response.CategoryTotalResponse;
import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.dto.response.DashboardResponse.Destaques;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.MerchantRuleRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class DashboardService {

  private final TransactionRepository transactionRepository;
  private final MerchantRuleRepository merchantRuleRepository;

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

    BigDecimal totalDespesas = despesasEssenciais.add(despesasNaoEssenciais);

    BigDecimal aportes =
        transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "CONTRIBUTION", start, end);
    BigDecimal resgatado =
        transactionRepository.sumInvestmentByDirectionAndDateBetween(
            userId, "REDEMPTION", start, end);
    // Net contribution for the 20% goal: what actually stayed invested this month.
    BigDecimal aplicado = aportes.subtract(resgatado);

    BigDecimal resultado = entradas.subtract(totalDespesas);

    // 50/30/20 base: month's registered income, falling back to the configured net salary
    BigDecimal rendaBase =
        entradas.compareTo(BigDecimal.ZERO) > 0
            ? entradas
            : (user.getMonthlyNetIncome() != null ? user.getMonthlyNetIncome() : BigDecimal.ZERO);

    BigDecimal percentualEssenciais = percent(despesasEssenciais, rendaBase);
    BigDecimal percentualNaoEssenciais = percent(despesasNaoEssenciais, rendaBase);
    BigDecimal percentualInvestimentos = percent(aplicado, rendaBase);

    // Single fetch of the month's expenses (with category + budgetGroup) feeds both the highlights
    // and the 50/30/20 drill-down.
    List<Transaction> expenses =
        transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end);

    DashboardResponse.Breakdown breakdown = buildBudgetBreakdown(expenses);
    Destaques destaques = buildDestaques(userId, start, end, expenses);

    return DashboardResponse.builder()
        .year(year)
        .month(month)
        .entradas(entradas)
        .despesasEssenciais(despesasEssenciais)
        .despesasNaoEssenciais(despesasNaoEssenciais)
        .totalDespesas(totalDespesas)
        .aportes(aportes)
        .resgatado(resgatado)
        .aplicado(aplicado)
        .resultado(resultado)
        .rendaBase(rendaBase)
        .percentualEssenciais(percentualEssenciais)
        .percentualNaoEssenciais(percentualNaoEssenciais)
        .percentualInvestimentos(percentualInvestimentos)
        .breakdown(breakdown)
        .destaques(destaques)
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
    List<Transaction> inGroup =
        expenses.stream().filter(t -> budgetGroup.equals(t.getBudgetGroup())).toList();

    Map<UUID, BigDecimal> totals =
        inGroup.stream()
            .filter(t -> t.getCategory() != null)
            .collect(
                Collectors.groupingBy(
                    t -> t.getCategory().getId(),
                    Collectors.reducing(BigDecimal.ZERO, this::effectiveAmount, BigDecimal::add)));

    Map<UUID, Transaction> byCategory =
        inGroup.stream()
            .filter(t -> t.getCategory() != null)
            .collect(Collectors.toMap(t -> t.getCategory().getId(), t -> t, (a, b) -> a));

    List<CategoryTotalResponse> result =
        new java.util.ArrayList<>(
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
        inGroup.stream()
            .filter(t -> t.getCategory() == null)
            .map(this::effectiveAmount)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    if (uncategorized.compareTo(BigDecimal.ZERO) > 0) {
      result.add(CategoryTotalResponse.of(null, "Sem categoria", "❓", "#9E9E9E", uncategorized));
    }

    return result;
  }

  private Destaques buildDestaques(
      UUID userId, LocalDate start, LocalDate end, List<Transaction> expenses) {
    String maiorSupermercado = null;
    BigDecimal maiorSupermercadoValor = BigDecimal.ZERO;
    String maiorDelivery = null;
    BigDecimal maiorDeliveryValor = BigDecimal.ZERO;

    Map<String, BigDecimal> byCategory =
        expenses.stream()
            .filter(t -> t.getCategory() != null)
            .collect(
                Collectors.groupingBy(
                    t -> t.getCategory().getName(),
                    Collectors.reducing(
                        BigDecimal.ZERO, t -> effectiveAmount(t), BigDecimal::add)));

    var rules = merchantRuleRepository.findAllVisibleToUser(userId);
    var supermarketNorm =
        rules.stream()
            .filter(r -> "Supermercado".equalsIgnoreCase(r.getSubcategory()))
            .map(r -> r.getNormalizedName().toLowerCase())
            .collect(Collectors.toSet());
    var deliveryNorm =
        rules.stream()
            .filter(r -> "Delivery".equalsIgnoreCase(r.getSubcategory()))
            .map(r -> r.getNormalizedName().toLowerCase())
            .collect(Collectors.toSet());
    var assinaturaNorm =
        rules.stream()
            .filter(r -> "Assinatura".equalsIgnoreCase(r.getSubcategory()))
            .map(r -> r.getNormalizedName().toLowerCase())
            .collect(Collectors.toSet());

    Map<String, BigDecimal> byNormalized =
        expenses.stream()
            .filter(t -> t.getNormalizedDescription() != null)
            .collect(
                Collectors.groupingBy(
                    t -> t.getNormalizedDescription().toLowerCase(),
                    Collectors.reducing(
                        BigDecimal.ZERO, t -> effectiveAmount(t), BigDecimal::add)));

    var superEntry =
        byNormalized.entrySet().stream()
            .filter(e -> supermarketNorm.contains(e.getKey()))
            .max(Comparator.comparing(Map.Entry::getValue));
    if (superEntry.isPresent()) {
      maiorSupermercado = capitalize(superEntry.get().getKey());
      maiorSupermercadoValor = superEntry.get().getValue();
    }

    var deliveryEntry =
        byNormalized.entrySet().stream()
            .filter(e -> deliveryNorm.contains(e.getKey()))
            .max(Comparator.comparing(Map.Entry::getValue));
    if (deliveryEntry.isPresent()) {
      maiorDelivery = capitalize(deliveryEntry.get().getKey());
      maiorDeliveryValor = deliveryEntry.get().getValue();
    }

    long qtdAssinaturas =
        expenses.stream()
            .filter(
                t ->
                    (t.getNormalizedDescription() != null
                            && assinaturaNorm.contains(t.getNormalizedDescription().toLowerCase()))
                        || (t.getCategory() != null
                            && "Assinatura".equalsIgnoreCase(t.getCategory().getName())))
            .count();

    long qtdCompras = transactionRepository.countExpensesInPeriod(userId, start, end);
    long qtdPixEnviados = transactionRepository.countPixEnviadosInPeriod(userId, start, end);
    long qtdPixRecebidos = transactionRepository.countPixRecebidosInPeriod(userId, start, end);

    return Destaques.builder()
        .maiorSupermercado(maiorSupermercado)
        .maiorSupermercadoValor(maiorSupermercadoValor)
        .maiorDelivery(maiorDelivery)
        .maiorDeliveryValor(maiorDeliveryValor)
        .quantidadeAssinaturas(qtdAssinaturas)
        .quantidadeCompras(qtdCompras)
        .quantidadePixEnviados(qtdPixEnviados)
        .quantidadePixRecebidos(qtdPixRecebidos)
        .build();
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
