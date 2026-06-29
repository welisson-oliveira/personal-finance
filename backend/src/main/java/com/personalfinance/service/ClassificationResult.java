package com.personalfinance.service;

import com.personalfinance.model.entity.MerchantRule;
import java.util.UUID;
import lombok.Getter;

@Getter
public class ClassificationResult {

  private final UUID categoryId;
  private final String categoryName;
  private final String subcategory;
  private final String expenseType;
  private final int confidence;

  private ClassificationResult(
      UUID categoryId,
      String categoryName,
      String subcategory,
      String expenseType,
      int confidence) {
    this.categoryId = categoryId;
    this.categoryName = categoryName;
    this.subcategory = subcategory;
    this.expenseType = expenseType;
    this.confidence = confidence;
  }

  public static ClassificationResult unknown() {
    return new ClassificationResult(null, null, null, null, 0);
  }

  public static ClassificationResult from(MerchantRule rule) {
    return new ClassificationResult(
        rule.getCategory() != null ? rule.getCategory().getId() : null,
        rule.getCategory() != null ? rule.getCategory().getName() : null,
        rule.getSubcategory(),
        rule.getExpenseType(),
        rule.getConfidence());
  }

  public boolean isKnown() {
    return confidence > 0;
  }

  public boolean isAutoClassifiable() {
    return confidence >= 80;
  }
}
