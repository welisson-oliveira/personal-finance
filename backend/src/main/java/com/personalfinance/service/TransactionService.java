package com.personalfinance.service;

import com.personalfinance.dto.request.CreateTransactionRequest;
import com.personalfinance.dto.response.TransactionResponse;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TransactionService {

  private final TransactionRepository transactionRepository;
  private final CategoryRepository categoryRepository;

  public Page<TransactionResponse> findAll(
      UUID userId, String month, String type, Pageable pageable) {
    if (month != null) {
      YearMonth ym = YearMonth.parse(month);
      LocalDate start = ym.atDay(1);
      LocalDate end = ym.atEndOfMonth();
      return transactionRepository
          .findByUserIdAndMonthExcludingOwnTransfer(userId, start, end, pageable)
          .map(this::toResponse);
    }
    if (type != null) {
      return transactionRepository
          .findByUserIdAndTypeOrderByDateDesc(userId, TransactionType.valueOf(type), pageable)
          .map(this::toResponse);
    }
    return transactionRepository
        .findByUserIdOrderByDateDesc(userId, pageable)
        .map(this::toResponse);
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
    return toResponse(transactionRepository.save(tx));
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
