package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

public record CreateKnownPersonRequest(
    @NotBlank String name,
    @NotBlank @Pattern(regexp = "HOUSE_MEMBER|FAMILY|FRIEND|OTHER") String relationship,
    @Pattern(regexp = "INCOME|IGNORE|ALWAYS_REVIEW") String defaultTreatment,
    String defaultLabel) {}
