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

    if (descLower.contains("open banking")
        && accountHolderName != null
        && descLower.contains(accountHolderName.toLowerCase())) {
      tx.setIncomeType("OWN_TRANSFER");
      return;
    }

    List<KnownPerson> persons = knownPersonRepository.findByUserIdAndActiveTrue(userId);
    for (KnownPerson person : persons) {
      if (nameMatches(descLower, person.getName())) {
        tx.setIncomeType(person.getDefaultIncomeType());
        tx.setKnownPersonId(person.getId());
        if (person.getDefaultLabel() != null) {
          tx.setNotes(person.getDefaultLabel());
        }
        return;
      }
    }

    tx.setIncomeType("INCOME");
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
