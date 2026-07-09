package com.personalfinance.dto.response;

import java.math.BigDecimal;

/** One month of the evolution report: real income, total expenses and balance. */
public record MonthlyPointResponse(
    int year, int month, BigDecimal receita, BigDecimal despesa, BigDecimal saldo) {}
