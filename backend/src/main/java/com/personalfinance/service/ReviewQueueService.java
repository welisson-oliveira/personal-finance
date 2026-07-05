package com.personalfinance.service;

import com.personalfinance.dto.request.ResolveReviewRequest;
import com.personalfinance.dto.response.ReviewQueueItemResponse;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.MerchantAlias;
import com.personalfinance.model.entity.MerchantDisplayName;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.ReviewQueue;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantAliasRepository;
import com.personalfinance.repository.MerchantDisplayNameRepository;
import com.personalfinance.repository.MerchantRuleRepository;
import com.personalfinance.repository.ReviewQueueRepository;
import com.personalfinance.repository.TransactionRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReviewQueueService {

  private final ReviewQueueRepository reviewQueueRepository;
  private final MerchantRuleRepository merchantRuleRepository;
  private final MerchantAliasRepository merchantAliasRepository;
  private final MerchantDisplayNameRepository merchantDisplayNameRepository;
  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;

  public List<ReviewQueueItemResponse> findPending(UUID userId) {
    return reviewQueueRepository
        .findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING")
        .stream()
        .map(this::toResponse)
        .toList();
  }

  @Transactional
  public void resolve(UUID reviewId, ResolveReviewRequest request, User user) {
    ReviewQueue item =
        reviewQueueRepository
            .findById(reviewId)
            .filter(r -> r.getUser().getId().equals(user.getId()))
            .orElseThrow(() -> new IllegalArgumentException("Review item not found"));

    String rawNorm = item.getNormalizedDescription();
    final String normalizedName =
        (rawNorm == null || rawNorm.isBlank()) ? request.getMerchantName() : rawNorm;

    boolean isIncome = "INCOME".equals(request.getType());
    String resolvedType = isIncome ? "INCOME" : "EXPENSE";

    Category category =
        (!isIncome && request.getCategoryId() != null)
            ? categoryRepository.findById(request.getCategoryId()).orElse(null)
            : null;

    String cleanNotes =
        (request.getTransactionNotes() == null || request.getTransactionNotes().isBlank())
            ? null
            : request.getTransactionNotes().trim();

    // Expenses are learned as merchant rules (+ alias) so future imports classify automatically.
    // Income is not learned here (income is driven by known persons), only corrected on the items.
    if (!isIncome && request.getBudgetGroup() != null && !request.getBudgetGroup().isBlank()) {
      final Category ruleCategory = category;
      MerchantRule rule =
          merchantRuleRepository
              .findUserRuleByNormalizedName(normalizedName, user.getId())
              .map(
                  existing -> {
                    existing.setCategory(ruleCategory);
                    existing.setExpenseType(request.getBudgetGroup());
                    existing.setConfidence(100);
                    existing.setCreatedBy("USER");
                    return merchantRuleRepository.save(existing);
                  })
              .orElseGet(
                  () ->
                      merchantRuleRepository.save(
                          MerchantRule.builder()
                              .user(user)
                              .merchantName(request.getMerchantName())
                              .normalizedName(normalizedName)
                              .category(ruleCategory)
                              .expenseType(request.getBudgetGroup())
                              .confidence(100)
                              .createdBy("USER")
                              .build()));

      String rawAlias = item.getRawDescription();
      if (merchantAliasRepository.findByAliasIgnoreCase(rawAlias).isEmpty()) {
        merchantAliasRepository.save(
            MerchantAlias.builder().merchantRule(rule).alias(rawAlias).build());
      }
    }

    if (cleanNotes != null) {
      MerchantDisplayName mdn =
          merchantDisplayNameRepository
              .findByUserIdAndNormalizedName(user.getId(), normalizedName)
              .orElseGet(
                  () ->
                      MerchantDisplayName.builder()
                          .user(user)
                          .normalizedName(normalizedName)
                          .build());
      mdn.setDisplayName(cleanNotes);
      mdn.setUpdatedAt(LocalDateTime.now());
      merchantDisplayNameRepository.save(mdn);
    }

    // Apply the resolution to every existing transaction with this name (any session)
    applyResolutionToTransactions(
        normalizedName,
        user.getId(),
        resolvedType,
        isIncome ? null : category,
        isIncome ? null : request.getBudgetGroup(),
        isIncome ? request.getIncomeType() : null,
        cleanNotes);

    // Resolve all other pending queue items with the same normalized name
    List<ReviewQueue> siblings =
        reviewQueueRepository.findByUserIdAndNormalizedDescriptionIgnoreCaseAndStatus(
            user.getId(), normalizedName, "PENDING");
    for (ReviewQueue sibling : siblings) {
      if (sibling.getId().equals(reviewId)) continue;
      sibling.setStatus("REVIEWED");
      sibling.setResolvedAt(LocalDateTime.now());
      reviewQueueRepository.save(sibling);
    }

    item.setStatus("REVIEWED");
    item.setResolvedAt(LocalDateTime.now());
    reviewQueueRepository.save(item);
  }

  private void applyResolutionToTransactions(
      String normalizedName,
      UUID userId,
      String type,
      Category category,
      String budgetGroup,
      String incomeType,
      String transactionNotes) {
    List<Transaction> matching =
        transactionRepository.findByUserIdAndEffectiveName(userId, normalizedName);
    for (Transaction tx : matching) {
      tx.setType(TransactionType.valueOf(type));
      tx.setCategory(category);
      tx.setBudgetGroup(budgetGroup);
      tx.setIncomeType(incomeType);
      if (transactionNotes != null) {
        tx.setNotes(transactionNotes);
      }
    }
    transactionRepository.saveAll(matching);
  }

  private ReviewQueueItemResponse toResponse(ReviewQueue item) {
    return ReviewQueueItemResponse.builder()
        .id(item.getId())
        .rawDescription(item.getRawDescription())
        .normalizedDescription(item.getNormalizedDescription())
        .type(item.getType() != null ? item.getType() : "EXPENSE")
        .amount(item.getAmount())
        .transactionDate(item.getTransactionDate())
        .suggestedCategoryId(
            item.getSuggestedCategory() != null ? item.getSuggestedCategory().getId() : null)
        .suggestedCategoryName(
            item.getSuggestedCategory() != null ? item.getSuggestedCategory().getName() : null)
        .status(item.getStatus())
        .build();
  }
}
