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
        .orElseGet(() -> canonicalize(rawDescription));
  }

  /**
   * Collapses volatile tokens (amounts, dates, long id/number runs, masked documents) so recurring
   * transactions that differ only by value/date/id share the same effective name and thus group,
   * propagate classification and learn together. The words (e.g. the recipient's name) are kept, so
   * distinct merchants stay distinct.
   */
  private String canonicalize(String raw) {
    String s = raw.toLowerCase();
    s = s.replace('•', ' '); // masked chars (e.g. •••.177.598-••)
    s = s.replaceAll("r\\$", " "); // currency symbol
    s = s.replaceAll("\\d{1,3}(\\.\\d{3})*,\\d{2}", " "); // 1.234,56
    s = s.replaceAll("\\d+[.,]\\d{2}\\b", " "); // 100,00 / 100.00
    s = s.replaceAll("\\b\\d{2}/\\d{2}(/\\d{2,4})?\\b", " "); // dates dd/mm(/yyyy)
    s = s.replaceAll("\\d{3}\\.?\\d{0,3}\\.?\\d{0,3}-?\\d{0,2}", " "); // masked CPF/doc runs
    s = s.replaceAll("\\b\\d{4,}\\b", " "); // long id/number runs (keeps short ones like "24h")
    s = s.replaceAll("[^\\p{L}\\p{N}\\s]", " "); // drop leftover punctuation
    s = s.replaceAll("\\s+", " ").trim(); // collapse whitespace
    return s.isBlank() ? raw.toLowerCase().trim() : s;
  }
}
