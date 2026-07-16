package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;

public record CreateCategoryRequest(
    @NotBlank String name, String icon, String color, UUID parentId) {}
