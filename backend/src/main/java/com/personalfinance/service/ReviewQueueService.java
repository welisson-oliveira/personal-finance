package com.personalfinance.service;

import com.personalfinance.dto.request.ResolveReviewRequest;
import com.personalfinance.dto.response.ReviewQueueItemResponse;
import com.personalfinance.model.entity.MerchantAlias;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.ReviewQueue;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantAliasRepository;
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

    var category =
        request.getCategoryId() != null
            ? categoryRepository.findById(request.getCategoryId()).orElse(null)
            : null;

    String rawNorm = item.getNormalizedDescription();
    final String normalizedName =
        (rawNorm == null || rawNorm.isBlank()) ? request.getMerchantName() : rawNorm;

    MerchantRule rule =
        merchantRuleRepository
            .findUserRuleByNormalizedName(normalizedName, user.getId())
            .map(
                existing -> {
                  existing.setCategory(category);
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
                            .category(category)
                            .expenseType(request.getBudgetGroup())
                            .confidence(100)
                            .createdBy("USER")
                            .build()));

    String rawAlias = item.getRawDescription();
    if (merchantAliasRepository.findByAliasIgnoreCase(rawAlias).isEmpty()) {
      merchantAliasRepository.save(
          MerchantAlias.builder().merchantRule(rule).alias(rawAlias).build());
    }

    applyRuleToSessionTransactions(item, rule, user.getId());

    item.setStatus("REVIEWED");
    item.setResolvedAt(LocalDateTime.now());
    reviewQueueRepository.save(item);
  }

  private void applyRuleToSessionTransactions(ReviewQueue item, MerchantRule rule, UUID userId) {
    if (item.getImportSession() == null || item.getNormalizedDescription() == null) return;
    UUID sessionId = item.getImportSession().getId();
    List<Transaction> sessionTxs =
        transactionRepository.findByImportSessionIdAndUserId(sessionId, userId);
    for (Transaction tx : sessionTxs) {
      if (item.getNormalizedDescription().equalsIgnoreCase(tx.getNormalizedDescription())) {
        tx.setCategory(rule.getCategory());
        tx.setBudgetGroup(rule.getExpenseType());
        transactionRepository.save(tx);
      }
    }
  }

  private ReviewQueueItemResponse toResponse(ReviewQueue item) {
    return ReviewQueueItemResponse.builder()
        .id(item.getId())
        .rawDescription(item.getRawDescription())
        .normalizedDescription(item.getNormalizedDescription())
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
