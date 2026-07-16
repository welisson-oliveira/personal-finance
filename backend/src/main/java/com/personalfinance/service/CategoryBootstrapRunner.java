package com.personalfinance.service;

import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import com.personalfinance.repository.UserRepository;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * One-time (idempotent) migration to Option 2: every existing user who has no categories yet gets
 * their own editable copy of the starter tree, and their transactions that still point at the old
 * global (seed) categories are remapped to the user's equivalent category by name. Runs on startup;
 * once a user has categories it is a no-op, so restarts are safe. New users are provisioned on
 * registration instead (see {@link UserService}).
 */
@Component
@RequiredArgsConstructor
public class CategoryBootstrapRunner implements ApplicationRunner {

  private final UserRepository userRepository;
  private final CategoryRepository categoryRepository;
  private final CategoryProvisioningService provisioningService;
  private final TransactionRepository transactionRepository;

  @Override
  @Transactional
  public void run(ApplicationArguments args) {
    for (User user : userRepository.findAll()) {
      if (categoryRepository.existsByUserId(user.getId())) continue;
      provisioningService.provisionDefaults(user);
      remapTransactions(user);
    }
  }

  private void remapTransactions(User user) {
    List<Transaction> txs = transactionRepository.findByUserIdWithGlobalCategory(user.getId());
    for (Transaction tx : txs) {
      String legacyName = tx.getCategory().getName();
      // Prefer a same-named category (e.g. "Contas" → Moradia/Contas); else the mapped top-level.
      Category target =
          categoryRepository
              .findFirstByUserIdAndName(user.getId(), legacyName)
              .or(
                  () ->
                      categoryRepository.findFirstByUserIdAndName(
                          user.getId(), DefaultCategories.legacyTopLevel(legacyName)))
              .orElse(null);
      if (target != null) tx.setCategory(target);
    }
    transactionRepository.saveAll(txs);
  }
}
