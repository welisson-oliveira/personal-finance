package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.request.CreateBudgetGoalRequest;
import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.model.entity.BudgetGoal;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.BudgetGoalRepository;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BudgetGoalServiceTest {

  @Mock private BudgetGoalRepository budgetGoalRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private TransactionRepository transactionRepository;

  @InjectMocks private BudgetGoalService service;

  private User user;
  private UUID userId;
  private Category category;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).name("Teste").build();
    category = Category.builder().id(UUID.randomUUID()).name("Alimentação").build();
  }

  @Test
  void findAll_computes_progress_for_the_month() {
    BudgetGoal goal =
        BudgetGoal.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(category)
            .amount(new BigDecimal("800.00"))
            .build();
    when(budgetGoalRepository.findByUserId(userId)).thenReturn(List.of(goal));
    when(transactionRepository.sumExpenseByCategoryIdsAndDateBetween(
            eq(userId), anyList(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(new BigDecimal("600.00"));

    List<BudgetGoalResponse> result = service.findAll(userId, 2026, 5);

    assertThat(result).hasSize(1);
    BudgetGoalResponse r = result.get(0);
    assertThat(r.spent()).isEqualByComparingTo("600.00");
    assertThat(r.remaining()).isEqualByComparingTo("200.00");
    assertThat(r.percentage()).isEqualByComparingTo("75.00");
  }

  @Test
  void findAll_percentage_over_100_when_overspent() {
    BudgetGoal goal =
        BudgetGoal.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(category)
            .amount(new BigDecimal("100.00"))
            .build();
    when(budgetGoalRepository.findByUserId(userId)).thenReturn(List.of(goal));
    when(transactionRepository.sumExpenseByCategoryIdsAndDateBetween(
            any(), anyList(), any(), any()))
        .thenReturn(new BigDecimal("150.00"));

    BudgetGoalResponse r = service.findAll(userId, 2026, 5).get(0);

    assertThat(r.remaining()).isEqualByComparingTo("-50.00");
    assertThat(r.percentage()).isEqualByComparingTo("150.00");
  }

  @Test
  void findAll_rolls_up_subcategories_into_the_parent_goal() {
    Category mercado = Category.builder().id(UUID.randomUUID()).name("Mercado").build();
    BudgetGoal goal =
        BudgetGoal.builder()
            .id(UUID.randomUUID())
            .user(user)
            .category(category) // Alimentação (parent)
            .amount(new BigDecimal("1000.00"))
            .build();
    when(budgetGoalRepository.findByUserId(userId)).thenReturn(List.of(goal));
    when(categoryRepository.findByParentId(category.getId())).thenReturn(List.of(mercado));
    ArgumentCaptor<List<UUID>> idsCaptor = ArgumentCaptor.forClass(List.class);
    when(transactionRepository.sumExpenseByCategoryIdsAndDateBetween(
            eq(userId), idsCaptor.capture(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(new BigDecimal("400.00"));

    BudgetGoalResponse r = service.findAll(userId, 2026, 5).get(0);

    // The parent and its subcategory are both summed.
    assertThat(idsCaptor.getValue()).containsExactlyInAnyOrder(category.getId(), mercado.getId());
    assertThat(r.spent()).isEqualByComparingTo("400.00");
  }

  @Test
  void create_rejects_duplicate_goal_for_category() {
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(budgetGoalRepository.findByUserIdAndCategoryId(userId, category.getId()))
        .thenReturn(Optional.of(BudgetGoal.builder().build()));

    assertThatThrownBy(
            () ->
                service.create(
                    new CreateBudgetGoalRequest(category.getId(), new BigDecimal("500")), user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("already exists");

    verify(budgetGoalRepository, never()).save(any());
  }

  @Test
  void delete_rejects_goal_of_another_user() {
    User other = User.builder().id(UUID.randomUUID()).build();
    BudgetGoal goal =
        BudgetGoal.builder().id(UUID.randomUUID()).user(other).category(category).build();
    when(budgetGoalRepository.findById(goal.getId())).thenReturn(Optional.of(goal));

    assertThatThrownBy(() -> service.delete(goal.getId(), user))
        .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    verify(budgetGoalRepository, never()).delete(any());
  }
}
