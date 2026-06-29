package com.personalfinance.service;

import com.personalfinance.dto.request.CreateKnownPersonRequest;
import com.personalfinance.dto.response.KnownPersonResponse;
import com.personalfinance.model.entity.KnownPerson;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.KnownPersonRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class KnownPersonService {

  private final KnownPersonRepository knownPersonRepository;

  public List<KnownPersonResponse> findAll(UUID userId) {
    return knownPersonRepository.findByUserIdAndActiveTrue(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public KnownPersonResponse create(CreateKnownPersonRequest request, User user) {
    KnownPerson person =
        KnownPerson.builder()
            .user(user)
            .name(request.name())
            .relationship(request.relationship())
            .defaultIncomeType(
                request.defaultIncomeType() != null ? request.defaultIncomeType() : "REIMBURSEMENT")
            .defaultLabel(request.defaultLabel())
            .build();
    return toResponse(knownPersonRepository.save(person));
  }

  @Transactional
  public KnownPersonResponse update(UUID id, CreateKnownPersonRequest request, User user) {
    KnownPerson person = findOwnedByUser(id, user.getId());
    person.setName(request.name());
    person.setRelationship(request.relationship());
    if (request.defaultIncomeType() != null) {
      person.setDefaultIncomeType(request.defaultIncomeType());
    }
    person.setDefaultLabel(request.defaultLabel());
    return toResponse(knownPersonRepository.save(person));
  }

  @Transactional
  public void deactivate(UUID id, User user) {
    KnownPerson person = findOwnedByUser(id, user.getId());
    person.setActive(false);
    knownPersonRepository.save(person);
  }

  private KnownPerson findOwnedByUser(UUID id, UUID userId) {
    return knownPersonRepository
        .findByIdAndUserId(id, userId)
        .orElseThrow(
            () -> new IllegalArgumentException("Known person not found or not owned by user"));
  }

  private KnownPersonResponse toResponse(KnownPerson p) {
    return new KnownPersonResponse(
        p.getId(),
        p.getName(),
        p.getRelationship(),
        p.getDefaultIncomeType(),
        p.getDefaultLabel(),
        p.isActive());
  }
}
