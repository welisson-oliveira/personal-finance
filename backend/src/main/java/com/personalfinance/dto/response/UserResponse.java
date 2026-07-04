package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

public record UserResponse(UUID id, String name, String email, BigDecimal monthlyNetIncome) {}
