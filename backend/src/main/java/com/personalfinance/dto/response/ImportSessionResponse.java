package com.personalfinance.dto.response;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

public record ImportSessionResponse(
    UUID id,
    String documentType,
    String fileName,
    LocalDate periodStart,
    LocalDate periodEnd,
    String status,
    LocalDateTime createdAt,
    long transactionCount) {}
