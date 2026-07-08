package com.personalfinance.service;

import com.personalfinance.dto.request.CreateBudgetGoalRequest;
import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.model.entity.BudgetGoal;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.BudgetGoalRepository;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class BudgetGoalService {

  private final BudgetGoalRepository budgetGoalRepository;
  private final CategoryRepository categoryRepository;
  private final TransactionRepository transactionRepository;

  /** Lists the user's goals with spending progress for the given month. */
  @Transactional(readOnly = true)
  public List<BudgetGoalResponse> findAll(UUID userId, int year, int month) {
    YearMonth ym = YearMonth.of(year, month);
    LocalDate start = ym.atDay(1);
    LocalDate end = ym.atEndOfMonth();
    return budgetGoalRepository.findByUserId(userId).stream()
        .map(goal -> toResponse(goal, userId, start, end))
        .toList();
  }

  @Transactional
  public BudgetGoalResponse create(CreateBudgetGoalRequest request, User user) {
    Category category = resolveCategory(request.categoryId());
    budgetGoalRepository
        .findByUserIdAndCategoryId(user.getId(), category.getId())
        .ifPresent(
            g -> {
              throw new IllegalArgumentException("A goal already exists for this category");
            });
    BudgetGoal goal =
        BudgetGoal.builder().user(user).category(category).amount(request.amount()).build();
    BudgetGoal saved = budgetGoalRepository.save(goal);
    YearMonth now = YearMonth.now();
    return toResponse(saved, user.getId(), now.atDay(1), now.atEndOfMonth());
  }

  @Transactional
  public BudgetGoalResponse update(UUID id, CreateBudgetGoalRequest request, User user) {
    BudgetGoal goal = findOwned(id, user.getId());
    goal.setAmount(request.amount());
    BudgetGoal saved = budgetGoalRepository.save(goal);
    YearMonth now = YearMonth.now();
    return toResponse(saved, user.getId(), now.atDay(1), now.atEndOfMonth());
  }

  @Transactional
  public void delete(UUID id, User user) {
    BudgetGoal goal = findOwned(id, user.getId());
    budgetGoalRepository.delete(goal);
  }

  private BudgetGoal findOwned(UUID id, UUID userId) {
    BudgetGoal goal =
        budgetGoalRepository
            .findById(id)
            .orElseThrow(() -> new IllegalArgumentException("Budget goal not found"));
    if (!goal.getUser().getId().equals(userId)) {
      throw new AccessDeniedException("Access denied");
    }
    return goal;
  }

  private Category resolveCategory(UUID categoryId) {
    return categoryRepository
        .findById(categoryId)
        .orElseThrow(() -> new IllegalArgumentException("Category not found"));
  }

  private BudgetGoalResponse toResponse(
      BudgetGoal goal, UUID userId, LocalDate start, LocalDate end) {
    Category category = goal.getCategory();
    BigDecimal spent =
        transactionRepository.sumExpenseByCategoryAndDateBetween(
            userId, category.getId(), start, end);
    BigDecimal amount = goal.getAmount();
    BigDecimal remaining = amount.subtract(spent);
    BigDecimal percentage =
        amount.compareTo(BigDecimal.ZERO) > 0
            ? spent.multiply(BigDecimal.valueOf(100)).divide(amount, 2, RoundingMode.HALF_UP)
            : BigDecimal.ZERO;
    return new BudgetGoalResponse(
        goal.getId(),
        category.getId(),
        category.getName(),
        category.getIcon(),
        category.getColor(),
        amount,
        spent,
        remaining,
        percentage);
  }
}
