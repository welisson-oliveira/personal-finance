package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Total expense for a category in a period (used by the category-breakdown report and the Dashboard
 * 50/30/20 drill-down). {@code previousTotal}/{@code deltaPercent} are only populated by the
 * Reports breakdown (month-over-month trend); they stay null in the Dashboard drill-down.
 */
public record CategoryTotalResponse(
    UUID categoryId,
    String categoryName,
    String categoryIcon,
    String categoryColor,
    BigDecimal total,
    BigDecimal previousTotal,
    BigDecimal deltaPercent) {

  /** Convenience for the Dashboard drill-down, where the month-over-month trend is not computed. */
  public static CategoryTotalResponse of(
      UUID categoryId, String name, String icon, String color, BigDecimal total) {
    return new CategoryTotalResponse(categoryId, name, icon, color, total, null, null);
  }
}
