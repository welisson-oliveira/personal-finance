package com.personalfinance.service;

import com.personalfinance.repository.MerchantAliasRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class MerchantNormalizationService {

  private final MerchantAliasRepository merchantAliasRepository;

  public String normalize(String rawDescription) {
    if (rawDescription == null || rawDescription.isBlank()) {
      return rawDescription;
    }
    String lower = rawDescription.toLowerCase();
    return merchantAliasRepository.findAllGlobal().stream()
        .filter(a -> lower.contains(a.getAlias().toLowerCase()))
        .map(a -> a.getMerchantRule().getNormalizedName())
        .findFirst()
        .orElse(rawDescription);
  }
}
