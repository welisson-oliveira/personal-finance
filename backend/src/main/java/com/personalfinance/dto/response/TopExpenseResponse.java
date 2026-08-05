package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A single expense in the month's "biggest expenses" ranking (where the money leaks). */
public record TopExpenseResponse(
    UUID id,
    String description,
    String categoryName,
    String categoryColor,
    LocalDate date,
    BigDecimal amount) {}
