package com.personalfinance.service;

import com.personalfinance.model.entity.BudgetGoal;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.BudgetGoalRepository;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import com.personalfinance.repository.UserRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time (idempotent) migration to Option 2: every existing user who has no categories yet gets
 * their own editable copy of the starter tree, and their data that still points at the old global
 * (seed) categories — transactions and budget goals — is remapped to the user's equivalent category
 * by name. Runs on startup; once a user has categories it is a no-op, so restarts are safe. New
 * users are provisioned on registration instead (see {@link UserService}).
 */
@Component
@RequiredArgsConstructor
public class CategoryBootstrapRunner implements ApplicationRunner {

  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final CategoryProvisioningService provisioningService;
  private final TransactionRepository transactionRepository;
  private final BudgetGoalRepository budgetGoalRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (User user : userRepository.findAll()) {
      if (categoryRepository.existsByUserId(user.getId())) continue;
      provisioningService.provisionDefaults(user);
      remapTransactions(user);
      remapBudgetGoals(user);
    }
  }

  private void remapTransactions(User user) {
    List<Transaction> txs = transactionRepository.findByUserIdWithGlobalCategory(user.getId());
    for (Transaction tx : txs) {
      Category target = resolveTarget(user, tx.getCategory().getName());
      if (target != null) tx.setCategory(target);
    }
    transactionRepository.saveAll(txs);
  }

  /**
   * Points each goal that still targets a global (seed) category at the user's equivalent category,
   * so its "spent" keeps counting after the transactions moved. A category can hold at most one
   * goal per user, so we never remap two goals onto the same target (the second keeps its old
   * category).
   */
  private void remapBudgetGoals(User user) {
    List<BudgetGoal> goals = budgetGoalRepository.findByUserId(user.getId());
    // Category ids already claimed by a goal (user-owned goals keep theirs).
    Set<UUID> used =
        goals.stream()
            .filter(g -> g.getCategory() != null && g.getCategory().getUser() != null)
            .map(g -> g.getCategory().getId())
            .collect(Collectors.toCollection(HashSet::new));
    for (BudgetGoal goal : goals) {
      Category cat = goal.getCategory();
      if (cat == null || cat.getUser() != null) continue; // already the user's own
      Category target = resolveTarget(user, cat.getName());
      if (target != null && used.add(target.getId())) {
        goal.setCategory(target);
      }
    }
    budgetGoalRepository.saveAll(goals);
  }

  /**
   * The user's category matching a legacy global name (same name first, else the mapped top-level).
   */
  private Category resolveTarget(User user, String legacyName) {
    return categoryRepository
        .findFirstByUserIdAndName(user.getId(), legacyName)
        .or(
            () ->
                categoryRepository.findFirstByUserIdAndName(
                    user.getId(), DefaultCategories.legacyTopLevel(legacyName)))
        .orElse(null);
  }
}
