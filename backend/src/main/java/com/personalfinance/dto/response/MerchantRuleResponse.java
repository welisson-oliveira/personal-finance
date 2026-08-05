package com.personalfinance.dto.response;

import java.util.UUID;

public record MerchantRuleResponse(
    UUID id,
    String merchantName,
    String normalizedName,
    String type,
    UUID categoryId,
    String categoryName,
    String subcategory,
    String expenseType,
    String investmentDirection,
    boolean ignored,
    int confidence,
    String createdBy,
    boolean global) {}
