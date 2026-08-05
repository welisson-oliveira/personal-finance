package com.personalfinance.dto.request;

import jakarta.validation.constraints.PositiveOrZero;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class UpdateProfileRequest {

  @PositiveOrZero(message = "monthlyNetIncome must be zero or positive")
  private BigDecimal monthlyNetIncome;

  @PositiveOrZero(message = "openingBalance must be zero or positive")
  private BigDecimal openingBalance;

  private LocalDate openingBalanceDate;
}
