package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

/** Total expense for a category in a period (used by the category-breakdown report). */
public record CategoryTotalResponse(
    UUID categoryId,
    String categoryName,
    String categoryIcon,
    String categoryColor,
    BigDecimal total) {}
