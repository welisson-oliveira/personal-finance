package com.personalfinance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.UUID;

public record CreateBudgetGoalRequest(
    @NotNull UUID categoryId, @NotNull @DecimalMin("0.01") BigDecimal amount) {}
