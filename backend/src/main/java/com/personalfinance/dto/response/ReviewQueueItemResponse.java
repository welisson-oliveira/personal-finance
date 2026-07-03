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
public class ReviewQueueItemResponse {

  private UUID id;
  private String rawDescription;
  private String normalizedDescription;
  private BigDecimal amount;
  private LocalDate transactionDate;
  private UUID suggestedCategoryId;
  private String suggestedCategoryName;
  private String status;
}
