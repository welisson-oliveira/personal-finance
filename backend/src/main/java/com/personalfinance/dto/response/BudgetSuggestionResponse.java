package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Suggested budget goals derived from the user's own spending, fitted to the 50/30/20 caps. Each
 * expense bucket (ESSENTIAL / NON_ESSENTIAL) gets a cap (50% / 30% of the income base) and a list
 * of per-category suggestions; investments get a single monthly-contribution target (20% of the
 * base).
 */
public record BudgetSuggestionResponse(
    BigDecimal rendaBase, List<BucketSuggestion> buckets, InvestmentSuggestion investimentos) {

  /** One 50/30/20 expense bucket with its cap and the categories suggested to fit under it. */
  public record BucketSuggestion(
      String group, // ESSENTIAL | NON_ESSENTIAL
      BigDecimal cap, // 50% / 30% da rendaBase
      BigDecimal historicalTotal, // soma das medianas por categoria (o que você gasta hoje)
      BigDecimal suggestedTotal, // soma das metas sugeridas (≤ cap quando estourava)
      boolean overCap, // gasto histórico da faixa passava do teto → metas foram reduzidas
      List<CategorySuggestion> categories) {}

  /** A suggested goal for one category. */
  public record CategorySuggestion(
      UUID categoryId,
      String categoryName,
      String categoryIcon,
      String categoryColor,
      BigDecimal historicalMonthly, // mediana mensal dos últimos meses
      BigDecimal suggestedAmount, // meta proposta (arredondada)
      boolean hasGoal) {} // já existe meta para a categoria (será atualizada)

  /** The 20% investment floor as a single monthly-contribution target. */
  public record InvestmentSuggestion(
      BigDecimal cap, BigDecimal historicalMonthly, BigDecimal suggestedAmount) {}
}
