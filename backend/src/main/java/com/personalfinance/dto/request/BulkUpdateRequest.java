package com.personalfinance.dto.request;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Bulk edit of the selected transactions in the list. Only the non-null fields are applied — each
 * field is an independent bulk operation from the toolbar. Semantics per field are documented in
 * {@code TransactionService.bulkUpdate}.
 */
@Getter
@Setter
@NoArgsConstructor
public class BulkUpdateRequest {

  @NotEmpty(message = "ids must not be empty")
  private List<UUID> ids;

  /** Applied only to EXPENSE rows (budget group is meaningless for the other types). */
  private String budgetGroup;

  /** Applied to EXPENSE/INCOME rows (INVESTMENT carries no category). */
  private UUID categoryId;

  /** yyyy-MM; sets competenceDate to the first day of the chosen month on all rows. */
  private String competenceMonth;

  /** Applied to all rows. */
  private Boolean ignored;
}
