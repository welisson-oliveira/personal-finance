package com.personalfinance.service;

import com.personalfinance.dto.response.DashboardResponse;
import com.personalfinance.dto.response.DashboardResponse.Destaques;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.enums.TransactionType;
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

  public DashboardResponse getMonthly(UUID userId, int year, int month) {
    YearMonth ym = YearMonth.of(year, month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();

    BigDecimal receitaBruta =
        transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "INCOME", start, end);

    BigDecimal reembolsos =
        transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "REIMBURSEMENT", start, end);

    BigDecimal receitaReal = receitaBruta;

    BigDecimal despesasEssenciais =
        transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "ESSENTIAL", start, end);

    BigDecimal despesasNaoEssenciais =
        transactionRepository.sumExpenseByBudgetGroupAndDateBetween(
            userId, "NON_ESSENTIAL", start, end);

    BigDecimal totalDespesas = despesasEssenciais.add(despesasNaoEssenciais);

    BigDecimal investido =
        transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.EXPENSE, "INVESTMENT", start, end);

    BigDecimal resgatado =
        transactionRepository.sumByUserIdAndTypeAndIncomeTypeAndDateBetween(
            userId, TransactionType.INCOME, "INVESTMENT", start, end);

    BigDecimal saldo = receitaReal.subtract(totalDespesas);

    BigDecimal percentualEssenciais = percent(despesasEssenciais, receitaReal);
    BigDecimal percentualNaoEssenciais = percent(despesasNaoEssenciais, receitaReal);
    BigDecimal percentualInvestimentos = percent(investido, receitaReal);

    Destaques destaques = buildDestaques(userId, start, end);

    return DashboardResponse.builder()
        .year(year)
        .month(month)
        .receitaBruta(receitaBruta)
        .reembolsos(reembolsos)
        .receitaReal(receitaReal)
        .despesasEssenciais(despesasEssenciais)
        .despesasNaoEssenciais(despesasNaoEssenciais)
        .totalDespesas(totalDespesas)
        .investido(investido)
        .resgatado(resgatado)
        .saldo(saldo)
        .percentualEssenciais(percentualEssenciais)
        .percentualNaoEssenciais(percentualNaoEssenciais)
        .percentualInvestimentos(percentualInvestimentos)
        .destaques(destaques)
        .build();
  }

  private Destaques buildDestaques(UUID userId, LocalDate start, LocalDate end) {
    List<Transaction> expenses =
        transactionRepository.findExpensesWithCategoryInPeriod(userId, start, end);

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
                    t.getNormalizedDescription() != null
                        && assinaturaNorm.contains(t.getNormalizedDescription().toLowerCase()))
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
