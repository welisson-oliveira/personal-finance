package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * One reconciliation decision offered on the preview of the second imported document.
 *
 * <p>When the current import is a FATURA there is a single slot (the fatura), and {@code
 * candidates} are extrato "Pagamento de fatura" transactions. When it is an EXTRATO there is one
 * slot per parsed bill payment (identified by {@code paymentIndex}), and {@code candidates} are
 * confirmed faturas. {@code suggestedId} is the auto-matched candidate (by value), pre-selected in
 * the UI.
 */
public record ReconciliationSlotDTO(
    String side, // "FATURA" | "EXTRATO" — what the current import is
    Integer paymentIndex, // EXTRATO: index of the bill payment in transactions; null for FATURA
    BigDecimal paymentAmount,
    LocalDate paymentDate,
    UUID suggestedId,
    List<ReconciliationCandidateDTO> candidates) {}
