package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import lombok.*;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ParsedTransactionDTO {

  private LocalDate date;
  private String description;
  private BigDecimal amount;
  private String type;
  private String cardHolder;
  private String installmentInfo;

  private String normalizedDescription;
  private String budgetGroup; // EXPENSE: ESSENTIAL | NON_ESSENTIAL
  private String investmentDirection; // INVESTMENT: CONTRIBUTION | REDEMPTION
  private UUID categoryId;
  private String categoryName;
  private String notes;
  private UUID knownPersonId;
  @Builder.Default private boolean ignored = false;
  private boolean needsReview;
  @Builder.Default private boolean included = true;
  private String autoClassification;
}
