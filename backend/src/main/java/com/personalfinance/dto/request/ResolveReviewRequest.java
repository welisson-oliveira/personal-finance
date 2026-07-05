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

  // Budget group is required for expenses; not used for income
  private String budgetGroup;

  @NotBlank private String merchantName;

  private String transactionNotes;

  // INCOME or EXPENSE — lets the user correct a wrong type inference
  private String type;

  // Only for income (INCOME / REIMBURSEMENT / OWN_TRANSFER / INVESTMENT)
  private String incomeType;
}
