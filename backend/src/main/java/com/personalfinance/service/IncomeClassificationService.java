package com.personalfinance.service;

import com.personalfinance.dto.response.ParsedTransactionDTO;
import com.personalfinance.model.entity.KnownPerson;
import com.personalfinance.repository.KnownPersonRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class IncomeClassificationService {

  private final KnownPersonRepository knownPersonRepository;

  public void classify(ParsedTransactionDTO tx, UUID userId, String accountHolderName) {
    if (!"INCOME".equals(tx.getType())) return;

    String descLower = tx.getDescription().toLowerCase();

    // Transfers between the user's own accounts: keep them but out of every calculation.
    if (descLower.contains("open banking")
        && accountHolderName != null
        && descLower.contains(accountHolderName.toLowerCase())) {
      tx.setIgnored(true);
      tx.setAutoClassification("OWN_TRANSFER");
      tx.setIncluded(false);
      return;
    }

    List<KnownPerson> persons = knownPersonRepository.findByUserIdAndActiveTrue(userId);
    for (KnownPerson person : persons) {
      if (nameMatches(descLower, person.getName())) {
        tx.setKnownPersonId(person.getId());
        if (person.getDefaultLabel() != null) {
          tx.setNotes(person.getDefaultLabel());
        }
        String treatment = person.getDefaultTreatment();
        if ("IGNORE".equals(treatment)) {
          tx.setIgnored(true);
          tx.setIncluded(false);
        } else if ("ALWAYS_REVIEW".equals(treatment)) {
          tx.setNeedsReview(true);
        }
        // else INCOME: nothing extra — it is already a plain income.
        return;
      }
    }
    // Unknown third-party income is plain income (type already INCOME).
  }

  private boolean nameMatches(String descLower, String personName) {
    String[] parts = personName.toLowerCase().split("\\s+");
    int matched = 0;
    for (String part : parts) {
      if (part.length() >= 3 && descLower.contains(part)) matched++;
    }
    return matched >= Math.min(2, parts.length);
  }
}
