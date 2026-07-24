package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.request.BulkBudgetGoalRequest;
import com.personalfinance.dto.request.CreateBudgetGoalRequest;
import com.personalfinance.dto.response.BudgetGoalResponse;
import com.personalfinance.dto.response.BudgetSuggestionResponse;
import com.personalfinance.dto.response.BudgetSuggestionResponse.BucketSuggestion;
import com.personalfinance.dto.response.BudgetSuggestionResponse.CategorySuggestion;
import com.personalfinance.model.entity.BudgetGoal;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
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
    // Default: no income offsets (most tests have no income in the same category).
    lenient()
        .when(
            transactionRepository.sumIncomeByCategoryIdsAndDateBetween(
                any(), anyList(), any(LocalDate.class), any(LocalDate.class)))
        .thenReturn(BigDecimal.ZERO);
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
  void suggest_builds_5030_20_goals_and_fits_an_over_cap_bucket() {
    // base = configured salary 5000 → essenciais 2500, não-ess. 1500, invest. 1000.
    user = User.builder().id(userId).name("Teste").monthlyNetIncome(new BigDecimal("5000")).build();
    Category aluguel = Category.builder().id(UUID.randomUUID()).name("Aluguel").build();
    Category delivery = Category.builder().id(UUID.randomUUID()).name("Delivery").build();
    Category compras = Category.builder().id(UUID.randomUUID()).name("Compras").build();

    when(transactionRepository.findExpensesWithCategoryInPeriod(
            userId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30)))
        .thenReturn(
            List.of(
                exp(aluguel, "ESSENTIAL", "1200", LocalDate.of(2026, 2, 10)),
                exp(aluguel, "ESSENTIAL", "1200", LocalDate.of(2026, 3, 10)),
                exp(aluguel, "ESSENTIAL", "1200", LocalDate.of(2026, 4, 10)),
                exp(delivery, "NON_ESSENTIAL", "700", LocalDate.of(2026, 2, 12)),
                exp(delivery, "NON_ESSENTIAL", "900", LocalDate.of(2026, 3, 12)),
                exp(delivery, "NON_ESSENTIAL", "800", LocalDate.of(2026, 4, 12)),
                exp(compras, "NON_ESSENTIAL", "900", LocalDate.of(2026, 2, 20)),
                exp(compras, "NON_ESSENTIAL", "900", LocalDate.of(2026, 3, 20)),
                exp(compras, "NON_ESSENTIAL", "900", LocalDate.of(2026, 4, 20))));
    when(budgetGoalRepository.findByUserId(userId)).thenReturn(List.of());
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            eq(userId), anyString(), any(), any()))
        .thenReturn(BigDecimal.ZERO);

    BudgetSuggestionResponse s = service.suggest(user, 2026, 5);

    assertThat(s.rendaBase()).isEqualByComparingTo("5000");

    BucketSuggestion ess = bucket(s, "ESSENTIAL");
    assertThat(ess.cap()).isEqualByComparingTo("2500");
    assertThat(ess.overCap()).isFalse();
    assertThat(ess.categories())
        .singleElement()
        .satisfies(
            c -> {
              assertThat(c.categoryName()).isEqualTo("Aluguel");
              assertThat(c.suggestedAmount()).isEqualByComparingTo("1200"); // median, under cap
              assertThat(c.hasGoal()).isFalse();
            });

    BucketSuggestion non = bucket(s, "NON_ESSENTIAL");
    assertThat(non.cap()).isEqualByComparingTo("1500");
    // medians 800 (delivery) + 900 (compras) = 1700 > 1500 → scaled down to fit.
    assertThat(non.historicalTotal()).isEqualByComparingTo("1700");
    assertThat(non.overCap()).isTrue();
    assertThat(non.suggestedTotal()).isBetween(new BigDecimal("1480"), new BigDecimal("1520"));
    assertThat(non.categories())
        .extracting(CategorySuggestion::categoryName)
        .containsExactlyInAnyOrder("Delivery", "Compras");

    assertThat(s.investimentos().cap()).isEqualByComparingTo("1000");
  }

  @Test
  void suggest_ignores_categories_present_in_at_most_one_month() {
    user = User.builder().id(userId).name("Teste").monthlyNetIncome(new BigDecimal("5000")).build();
    Category eventual = Category.builder().id(UUID.randomUUID()).name("Presente").build();

    when(transactionRepository.findExpensesWithCategoryInPeriod(
            userId, LocalDate.of(2026, 2, 1), LocalDate.of(2026, 4, 30)))
        .thenReturn(List.of(exp(eventual, "NON_ESSENTIAL", "300", LocalDate.of(2026, 3, 10))));
    when(budgetGoalRepository.findByUserId(userId)).thenReturn(List.of());
    when(transactionRepository.sumInvestmentByDirectionAndDateBetween(
            eq(userId), anyString(), any(), any()))
        .thenReturn(BigDecimal.ZERO);

    BudgetSuggestionResponse s = service.suggest(user, 2026, 5);

    // median of {300, 0, 0} = 0 → the sporadic category is dropped.
    assertThat(bucket(s, "NON_ESSENTIAL").categories()).isEmpty();
  }

  @Test
  void bulkUpsert_creates_new_and_updates_existing_goals() {
    Category catA = Category.builder().id(UUID.randomUUID()).name("Aluguel").build();
    Category catB = Category.builder().id(UUID.randomUUID()).name("Delivery").build();
    BudgetGoal existing =
        BudgetGoal.builder().id(UUID.randomUUID()).user(user).category(catA).build();

    when(categoryRepository.findById(catA.getId())).thenReturn(Optional.of(catA));
    when(categoryRepository.findById(catB.getId())).thenReturn(Optional.of(catB));
    when(budgetGoalRepository.findByUserIdAndCategoryId(userId, catA.getId()))
        .thenReturn(Optional.of(existing));
    when(budgetGoalRepository.findByUserIdAndCategoryId(userId, catB.getId()))
        .thenReturn(Optional.empty());
    when(budgetGoalRepository.save(any(BudgetGoal.class))).thenAnswer(inv -> inv.getArgument(0));
    when(categoryRepository.findByParentId(any())).thenReturn(List.of());
    when(transactionRepository.sumExpenseByCategoryIdsAndDateBetween(
            any(), anyList(), any(), any()))
        .thenReturn(BigDecimal.ZERO);

    List<BudgetGoalResponse> result =
        service.bulkUpsert(
            List.of(
                new BulkBudgetGoalRequest.Item(catA.getId(), new BigDecimal("1200")),
                new BulkBudgetGoalRequest.Item(catB.getId(), new BigDecimal("500"))),
            user);

    assertThat(result).hasSize(2);
    assertThat(existing.getAmount()).isEqualByComparingTo("1200"); // updated in place
    verify(budgetGoalRepository, times(2)).save(any(BudgetGoal.class));
  }

  private BucketSuggestion bucket(BudgetSuggestionResponse s, String group) {
    return s.buckets().stream().filter(b -> b.group().equals(group)).findFirst().orElseThrow();
  }

  private Transaction exp(Category category, String group, String amount, LocalDate date) {
    return Transaction.builder()
        .id(UUID.randomUUID())
        .type(TransactionType.EXPENSE)
        .category(category)
        .budgetGroup(group)
        .amount(new BigDecimal(amount))
        .shared(false)
        .date(date)
        .build();
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
