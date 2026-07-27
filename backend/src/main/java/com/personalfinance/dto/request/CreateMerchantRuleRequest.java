package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record CreateMerchantRuleRequest(
    @NotBlank String merchantName,
    UUID categoryId,
    String subcategory,
    @NotBlank @Pattern(regexp = "ESSENTIAL|NON_ESSENTIAL") String expenseType) {}
