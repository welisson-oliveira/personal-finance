package com.personalfinance.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class CreateTransactionRequest {

  @NotBlank private String description;

  @NotNull
  @DecimalMin("0.01")
  private BigDecimal amount;

  @NotBlank private String type;

  @NotNull private LocalDate date;

  private UUID categoryId;
  private String budgetGroup;
  private String incomeType;
  private String notes;
  private boolean shared;
  private BigDecimal totalAmount;
  private BigDecimal userShare;
}
