package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;

public record AnomalyFeedbackRequest(
    @NotNull UUID transactionId,
    @Pattern(regexp = "AMOUNT_OUTLIER|DUPLICATE_CHARGE") String type,
    @Pattern(regexp = "FALSE_POSITIVE|ACKNOWLEDGED") String status) {}
