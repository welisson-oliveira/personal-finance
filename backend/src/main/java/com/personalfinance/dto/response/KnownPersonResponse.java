package com.personalfinance.dto.response;

import java.util.UUID;

public record KnownPersonResponse(
    UUID id,
    String name,
    String relationship,
    String defaultIncomeType,
    String defaultLabel,
    boolean active) {}
