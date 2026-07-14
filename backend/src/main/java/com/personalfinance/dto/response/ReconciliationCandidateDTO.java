package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A candidate counterpart for reconciling a bill payment. {@code id} is an extrato payment
 * transaction id (when the current import is a FATURA) or a fatura import-session id (when the
 * current import is an EXTRATO).
 */
public record ReconciliationCandidateDTO(
    UUID id, String label, BigDecimal amount, LocalDate date) {}
