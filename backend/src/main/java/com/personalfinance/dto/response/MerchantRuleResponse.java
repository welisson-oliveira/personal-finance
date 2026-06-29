package com.personalfinance.dto.response;

import java.util.UUID;

public record MerchantRuleResponse(
    UUID id,
    String merchantName,
    String normalizedName,
    UUID categoryId,
    String categoryName,
    String subcategory,
    String expenseType,
    int confidence,
    String createdBy,
    boolean global) {}
