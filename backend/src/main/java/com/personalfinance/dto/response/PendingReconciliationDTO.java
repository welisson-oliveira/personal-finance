package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * A still-unreconciled extrato "Pagamento de fatura" for the dedicated reconciliation screen, with
 * the candidate faturas it could be linked to and the suggested (value-matched) one.
 */
public record PendingReconciliationDTO(
    UUID paymentId,
    LocalDate date,
    BigDecimal amount,
    String description,
    UUID suggestedFaturaId,
    List<ReconciliationCandidateDTO> candidates) {}
