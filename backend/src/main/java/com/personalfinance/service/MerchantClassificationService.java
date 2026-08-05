package com.personalfinance.service;

import com.personalfinance.repository.MerchantRuleRepository;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantClassificationService {

  private final MerchantRuleRepository merchantRuleRepository;

  /**
   * A rule the user created by correcting a transaction. It is a full override — applied on import
   * ahead of the heuristics (own-transfer, investment, expense global rules) for that merchant.
   */
  public Optional<ClassificationResult> findUserOverride(String normalizedName, UUID userId) {
    if (normalizedName == null || userId == null) return Optional.empty();
    return merchantRuleRepository
        .findUserRuleByNormalizedName(normalizedName, userId)
        .map(ClassificationResult::from);
  }

  public ClassificationResult classify(String normalizedName) {
    return classify(normalizedName, null);
  }

  public ClassificationResult classify(String normalizedName, UUID userId) {
    if (userId != null) {
      var userRule = merchantRuleRepository.findUserRuleByNormalizedName(normalizedName, userId);
      if (userRule.isPresent()) {
        return ClassificationResult.from(userRule.get());
      }
    }
    return merchantRuleRepository
        .findGlobalByNormalizedName(normalizedName)
        .map(ClassificationResult::from)
        .orElse(ClassificationResult.unknown());
  }
}
