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
  // Full-override fields (populated from a user rule): what type to force, whether to ignore, and
  // the investment direction. Null/false on legacy expense-only rules.
  private final String type;
  private final boolean ignored;
  private final String investmentDirection;

  private ClassificationResult(
      UUID categoryId,
      String categoryName,
      String subcategory,
      String expenseType,
      int confidence,
      String type,
      boolean ignored,
      String investmentDirection) {
    this.categoryId = categoryId;
    this.categoryName = categoryName;
    this.subcategory = subcategory;
    this.expenseType = expenseType;
    this.confidence = confidence;
    this.type = type;
    this.ignored = ignored;
    this.investmentDirection = investmentDirection;
  }

  public static ClassificationResult unknown() {
    return new ClassificationResult(null, null, null, null, 0, null, false, null);
  }

  public static ClassificationResult from(MerchantRule rule) {
    return new ClassificationResult(
        rule.getCategory() != null ? rule.getCategory().getId() : null,
        rule.getCategory() != null ? rule.getCategory().getName() : null,
        rule.getSubcategory(),
        rule.getExpenseType(),
        rule.getConfidence(),
        rule.getType(),
        rule.isIgnored(),
        rule.getInvestmentDirection());
  }

  public boolean isKnown() {
    return confidence > 0;
  }

  public boolean isAutoClassifiable() {
    return confidence >= 80;
  }
}
