package com.personalfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.personalfinance.model.entity.BudgetGoal;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.BudgetGoalRepository;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import com.personalfinance.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class CategoryBootstrapRunnerTest {

  @Mock private UserRepository userRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private CategoryProvisioningService provisioningService;
  @Mock private TransactionRepository transactionRepository;
  @Mock private BudgetGoalRepository budgetGoalRepository;

  @InjectMocks private CategoryBootstrapRunner runner;

  @Test
  void run_remaps_a_goal_on_a_global_category_to_the_users_own_category() {
    UUID userId = UUID.randomUUID();
    User user = User.builder().id(userId).build();
    Category global =
        Category.builder().id(UUID.randomUUID()).name("Alimentação").build(); // user==null
    Category owned =
        Category.builder().id(UUID.randomUUID()).name("Alimentação").user(user).build();
    BudgetGoal goal =
        BudgetGoal.builder().id(UUID.randomUUID()).user(user).category(global).build();

    when(userRepository.findAll()).thenReturn(List.of(user));
    when(categoryRepository.existsByUserId(userId)).thenReturn(false);
    when(transactionRepository.findByUserIdWithGlobalCategory(userId)).thenReturn(List.of());
    when(budgetGoalRepository.findByUserId(userId)).thenReturn(List.of(goal));
    when(categoryRepository.findFirstByUserIdAndName(userId, "Alimentação"))
        .thenReturn(Optional.of(owned));

    runner.run(null);

    // The goal now targets the user's own category, so its "spent" keeps counting.
    assertThat(goal.getCategory()).isEqualTo(owned);
    verify(provisioningService).provisionDefaults(user);
    verify(budgetGoalRepository).saveAll(List.of(goal));
  }

  @Test
  void run_does_nothing_for_users_that_already_have_categories() {
    UUID userId = UUID.randomUUID();
    User user = User.builder().id(userId).build();
    when(userRepository.findAll()).thenReturn(List.of(user));
    when(categoryRepository.existsByUserId(userId)).thenReturn(true);

    runner.run(null);

    verify(provisioningService, never()).provisionDefaults(any());
    verify(budgetGoalRepository, never()).saveAll(any());
  }
}
