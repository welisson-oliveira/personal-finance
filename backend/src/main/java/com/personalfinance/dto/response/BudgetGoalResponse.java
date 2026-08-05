package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A budget goal with its progress for a given month: {@code spent} is the month's expense for the
 * category, {@code remaining} = amount − spent (may be negative), {@code percentage} = spent /
 * amount * 100.
 */
public record BudgetGoalResponse(
    UUID id,
    UUID categoryId,
    String categoryName,
    String categoryIcon,
    String categoryColor,
    BigDecimal amount,
    BigDecimal spent,
    BigDecimal remaining,
    BigDecimal percentage) {}
