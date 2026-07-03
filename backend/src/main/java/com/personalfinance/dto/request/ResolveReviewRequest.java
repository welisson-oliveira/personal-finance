package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotBlank;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ResolveReviewRequest {

  private UUID categoryId;

  @NotBlank private String budgetGroup;

  @NotBlank private String merchantName;
}
