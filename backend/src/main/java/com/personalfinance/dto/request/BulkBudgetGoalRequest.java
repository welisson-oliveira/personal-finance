package com.personalfinance.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/** Create-or-update several budget goals in one call (used by "Aplicar metas sugeridas"). */
public record BulkBudgetGoalRequest(@NotEmpty @Valid List<Item> goals) {

  public record Item(@NotNull UUID categoryId, @NotNull @DecimalMin("0.01") BigDecimal amount) {}
}
