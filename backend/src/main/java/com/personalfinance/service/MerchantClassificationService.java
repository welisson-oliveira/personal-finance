package com.personalfinance.service;

import com.personalfinance.repository.MerchantRuleRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantClassificationService {

  private final MerchantRuleRepository merchantRuleRepository;

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
