package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A detected transaction anomaly. Identity is (transactionId, type). {@code status} is OPEN when
 * the user hasn't acted, or FALSE_POSITIVE / ACKNOWLEDGED once they have.
 */
public record AnomalyResponse(
    UUID transactionId,
    String type,
    String status,
    String title,
    String message,
    String description,
    String categoryName,
    LocalDate date,
    BigDecimal amount,
    BigDecimal typicalAmount,
    UUID relatedTransactionId,
    LocalDate relatedDate) {}
