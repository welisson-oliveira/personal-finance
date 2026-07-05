package com.personalfinance.service;

import com.personalfinance.dto.request.CreateTransactionRequest;
import com.personalfinance.dto.response.TransactionResponse;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.MerchantDisplayName;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantDisplayNameRepository;
import com.personalfinance.repository.MerchantRuleRepository;
import com.personalfinance.repository.TransactionRepository;
import com.personalfinance.repository.TransactionSpecifications;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;
  private final MerchantDisplayNameRepository merchantDisplayNameRepository;
  private final MerchantRuleRepository merchantRuleRepository;

  @Transactional(readOnly = true)
  public Page<TransactionResponse> findAll(
      UUID userId, String month, String type, UUID categoryId, Pageable pageable) {
    LocalDate start = null;
    LocalDate end = null;
    if (month != null && !month.isBlank()) {
      YearMonth ym = YearMonth.parse(month);
      start = ym.atDay(1);
      end = ym.atEndOfMonth();
    }
    TransactionType txType =
        (type != null && !type.isBlank()) ? TransactionType.valueOf(type) : null;

    Specification<Transaction> spec =
        Specification.where(TransactionSpecifications.forUser(userId))
            .and(TransactionSpecifications.inDateRange(start, end))
            .and(TransactionSpecifications.ofType(txType))
            .and(TransactionSpecifications.inCategory(categoryId))
            .and(TransactionSpecifications.excludingOwnTransfer());

    return transactionRepository.findAll(spec, pageable).map(this::toResponse);
  }

  @Transactional
  public TransactionResponse create(CreateTransactionRequest request, User user) {
    Category category = resolveCategory(request.getCategoryId());
    Transaction tx =
        Transaction.builder()
            .user(user)
            .description(request.getDescription())
            .amount(request.getAmount())
            .type(TransactionType.valueOf(request.getType()))
            .incomeType(request.getIncomeType())
            .budgetGroup(request.getBudgetGroup())
            .date(request.getDate())
            .notes(request.getNotes())
            .category(category)
            .source("MANUAL")
            .shared(request.isShared())
            .totalAmount(request.getTotalAmount())
            .userShare(request.getUserShare())
            .build();
    return toResponse(transactionRepository.save(tx));
  }

  @Transactional
  public TransactionResponse update(UUID id, CreateTransactionRequest request, User user) {
    Transaction tx = findOwned(id, user.getId());
    Category category = resolveCategory(request.getCategoryId());
    tx.setDescription(request.getDescription());
    tx.setAmount(request.getAmount());
    tx.setType(TransactionType.valueOf(request.getType()));
    tx.setIncomeType(request.getIncomeType());
    tx.setBudgetGroup(request.getBudgetGroup());
    tx.setDate(request.getDate());
    tx.setNotes(request.getNotes());
    tx.setCategory(category);
    tx.setShared(request.isShared());
    tx.setTotalAmount(request.getTotalAmount());
    tx.setUserShare(request.getUserShare());
    Transaction saved = transactionRepository.save(tx);
    propagateClassification(saved, user);
    return toResponse(saved);
  }

  /**
   * Propagates the classification (category, budget group, income type) of an edited transaction to
   * every other transaction with the same effective name for this user, and learns a merchant rule
   * so future imports are classified the same way.
   */
  private void propagateClassification(Transaction source, User user) {
    String effectiveName =
        source.getNormalizedDescription() != null
            ? source.getNormalizedDescription()
            : source.getDescription();
    if (effectiveName == null || effectiveName.isBlank()) return;

    List<Transaction> matching =
        transactionRepository.findByUserIdAndEffectiveName(user.getId(), effectiveName);
    for (Transaction t : matching) {
      if (t.getId().equals(source.getId())) continue;
      t.setCategory(source.getCategory());
      t.setBudgetGroup(source.getBudgetGroup());
      t.setIncomeType(source.getIncomeType());
    }
    transactionRepository.saveAll(matching);

    // Learn for future imports (merchant rules classify expenses by budget group + category)
    if (source.getType() == TransactionType.EXPENSE && source.getBudgetGroup() != null) {
      MerchantRule rule =
          merchantRuleRepository
              .findUserRuleByNormalizedName(effectiveName, user.getId())
              .orElseGet(
                  () ->
                      MerchantRule.builder()
                          .user(user)
                          .merchantName(source.getDescription())
                          .normalizedName(effectiveName)
                          .createdBy("USER")
                          .build());
      rule.setCategory(source.getCategory());
      rule.setExpenseType(source.getBudgetGroup());
      rule.setConfidence(100);
      rule.setCreatedBy("USER");
      merchantRuleRepository.save(rule);
    }
  }

  @Transactional
  public TransactionResponse updateNotes(UUID id, User user, String notes) {
    Transaction tx = findOwned(id, user.getId());
    String effectiveName =
        tx.getNormalizedDescription() != null ? tx.getNormalizedDescription() : tx.getDescription();
    String cleanNotes = (notes == null || notes.isBlank()) ? null : notes.trim();

    // Upsert or delete the merchant display name mapping
    if (cleanNotes == null) {
      merchantDisplayNameRepository
          .findByUserIdAndNormalizedName(user.getId(), effectiveName)
          .ifPresent(merchantDisplayNameRepository::delete);
    } else {
      MerchantDisplayName mdn =
          merchantDisplayNameRepository
              .findByUserIdAndNormalizedName(user.getId(), effectiveName)
              .orElseGet(
                  () ->
                      MerchantDisplayName.builder()
                          .user(user)
                          .normalizedName(effectiveName)
                          .build());
      mdn.setDisplayName(cleanNotes);
      mdn.setUpdatedAt(LocalDateTime.now());
      merchantDisplayNameRepository.save(mdn);
    }

    // Propagate to all transactions with the same effective name for this user
    List<Transaction> matching =
        transactionRepository.findByUserIdAndEffectiveName(user.getId(), effectiveName);
    matching.forEach(t -> t.setNotes(cleanNotes));
    transactionRepository.saveAll(matching);

    return toResponse(matching.stream().filter(t -> t.getId().equals(id)).findFirst().orElse(tx));
  }

  @Transactional
  public void delete(UUID id, User user) {
    Transaction tx = findOwned(id, user.getId());
    transactionRepository.delete(tx);
  }

  private Transaction findOwned(UUID id, UUID userId) {
    Transaction tx =
        transactionRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Transaction not found"));
    if (!tx.getUser().getId().equals(userId)) {
      throw new AccessDeniedException("Access denied");
    }
    return tx;
  }

  private Category resolveCategory(UUID categoryId) {
    if (categoryId == null) return null;
    return categoryRepository.findById(categoryId).orElse(null);
  }

  public TransactionResponse toResponse(Transaction tx) {
    return TransactionResponse.builder()
        .id(tx.getId())
        .description(tx.getDescription())
        .normalizedDescription(tx.getNormalizedDescription())
        .amount(tx.getAmount())
        .type(tx.getType().name())
        .incomeType(tx.getIncomeType())
        .budgetGroup(tx.getBudgetGroup())
        .date(tx.getDate())
        .notes(tx.getNotes())
        .categoryId(tx.getCategory() != null ? tx.getCategory().getId() : null)
        .categoryName(tx.getCategory() != null ? tx.getCategory().getName() : null)
        .source(tx.getSource())
        .cardHolder(tx.getCardHolder())
        .installmentInfo(tx.getInstallmentInfo())
        .shared(tx.isShared())
        .totalAmount(tx.getTotalAmount())
        .userShare(tx.getUserShare())
        .build();
  }
}
