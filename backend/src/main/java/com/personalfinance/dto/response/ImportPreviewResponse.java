package com.personalfinance.dto.response;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public record ImportPreviewResponse(
    UUID sessionId,
    String documentType,
    LocalDate periodStart,
    LocalDate periodEnd,
    List<ParsedTransactionDTO> transactions,
    int reviewQueueCount) {}
