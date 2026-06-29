package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransactionResponse {

  private UUID id;
  private String description;
  private String normalizedDescription;
  private BigDecimal amount;
  private String type;
  private String incomeType;
  private String budgetGroup;
  private LocalDate date;
  private String notes;
  private UUID categoryId;
  private String categoryName;
  private String source;
  private String cardHolder;
  private String installmentInfo;
  private boolean shared;
  private BigDecimal totalAmount;
  private BigDecimal userShare;
}
