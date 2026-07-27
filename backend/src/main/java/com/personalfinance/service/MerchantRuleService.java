package com.personalfinance.service;

import com.personalfinance.dto.request.CreateMerchantRuleRequest;
import com.personalfinance.dto.response.MerchantRuleResponse;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantRuleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MerchantRuleService {

  private final MerchantRuleRepository merchantRuleRepository;
  private final CategoryRepository categoryRepository;
  private final MerchantNormalizationService normalizationService;

  @Transactional(readOnly = true)
  public List<MerchantRuleResponse> findAll(UUID userId) {
    return merchantRuleRepository.findAllVisibleToUser(userId).stream()
        .map(this::toResponse)
        .toList();
  }

  /** Creates a new rule owned by the user. Rejects a duplicate of one the user already has. */
  @Transactional
  public MerchantRuleResponse create(CreateMerchantRuleRequest request, User user) {
    String normalizedName = normalizationService.normalize(request.merchantName());
    merchantRuleRepository
        .findUserRuleByNormalizedName(normalizedName, user.getId())
        .ifPresent(
            existing -> {
              throw new IllegalArgumentException(
                  "Você já tem uma regra para \"" + existing.getMerchantName() + "\". Edite-a.");
            });

    MerchantRule rule =
        MerchantRule.builder()
            .user(user)
            .merchantName(request.merchantName())
            .normalizedName(normalizedName)
            .category(resolveCategory(request.categoryId()))
            .subcategory(request.subcategory())
            .expenseType(request.expenseType())
            .confidence(100)
            .createdBy("USER")
            .build();
    return toResponse(merchantRuleRepository.save(rule));
  }

  /**
   * Updates a rule. A user's own rule is edited in place; a global (system) rule is left untouched
   * and a personal override is created/updated instead — the override matches the same merchant
   * (same normalizedName) and wins over the global during classification.
   */
  @Transactional
  public MerchantRuleResponse update(UUID id, CreateMerchantRuleRequest request, User user) {
    MerchantRule rule =
        merchantRuleRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Regra não encontrada."));
    Category category = resolveCategory(request.categoryId());

    if (rule.getUser() == null) {
      // Global rule: create (or update) a personal override on the same normalizedName.
      MerchantRule override =
          merchantRuleRepository
              .findUserRuleByNormalizedName(rule.getNormalizedName(), user.getId())
              .orElseGet(
                  () ->
                      MerchantRule.builder()
                          .user(user)
                          .normalizedName(rule.getNormalizedName())
                          .confidence(100)
                          .createdBy("USER")
                          .build());
      override.setMerchantName(request.merchantName());
      override.setCategory(category);
      override.setSubcategory(request.subcategory());
      override.setExpenseType(request.expenseType());
      return toResponse(merchantRuleRepository.save(override));
    }

    if (!rule.getUser().getId().equals(user.getId())) {
      throw new AccessDeniedException("Você não pode editar esta regra.");
    }
    rule.setMerchantName(request.merchantName());
    rule.setCategory(category);
    rule.setSubcategory(request.subcategory());
    rule.setExpenseType(request.expenseType());
    rule.setCreatedBy("USER");
    return toResponse(merchantRuleRepository.save(rule));
  }

  /** Deletes a user's own rule. Global (system) rules cannot be deleted — override them instead. */
  @Transactional
  public void delete(UUID id, User user) {
    MerchantRule rule =
        merchantRuleRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Regra não encontrada."));
    if (rule.getUser() == null) {
      throw new AccessDeniedException(
          "Regras do sistema não podem ser excluídas. Personalize-a para sobrepô-la.");
    }
    if (!rule.getUser().getId().equals(user.getId())) {
      throw new AccessDeniedException("Você não pode excluir esta regra.");
    }
    merchantRuleRepository.delete(rule);
  }

  private Category resolveCategory(UUID categoryId) {
    if (categoryId == null) return null;
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new IllegalArgumentException("Categoria não encontrada."));
  }

  private MerchantRuleResponse toResponse(MerchantRule r) {
    return new MerchantRuleResponse(
        r.getId(),
        r.getMerchantName(),
        r.getNormalizedName(),
        r.getCategory() != null ? r.getCategory().getId() : null,
        r.getCategory() != null ? r.getCategory().getName() : null,
        r.getSubcategory(),
        r.getExpenseType(),
        r.getConfidence(),
        r.getCreatedBy(),
        r.getUser() == null);
  }
}
