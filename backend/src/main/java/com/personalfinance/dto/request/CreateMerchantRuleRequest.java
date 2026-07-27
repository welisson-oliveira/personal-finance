package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateMerchantRuleRequest(
    @NotBlank String merchantName,
    @Pattern(regexp = "INCOME|EXPENSE|INVESTMENT") String type,
    UUID categoryId,
    String subcategory,
    @Pattern(regexp = "ESSENTIAL|NON_ESSENTIAL") String expenseType,
    @Pattern(regexp = "CONTRIBUTION|REDEMPTION") String investmentDirection,
    boolean ignored) {}
