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
  private LocalDate competenceDate; // payment month for faturas; = date otherwise
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

  // EXTRATO import only: when true, this "Pagamento de fatura" was reconciled to a fatura and must
  // not be persisted (the fatura's items already represent it).
  @Builder.Default private boolean reconciled = false;
}
