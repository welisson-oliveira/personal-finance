package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.request.BulkUpdateRequest;
import com.personalfinance.dto.request.CreateTransactionRequest;
import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.Transaction;
import com.personalfinance.model.entity.User;
import com.personalfinance.model.entity.enums.TransactionType;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantDisplayNameRepository;
import com.personalfinance.repository.MerchantRuleRepository;
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
class TransactionServiceTest {

  @Mock private TransactionRepository transactionRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private MerchantDisplayNameRepository merchantDisplayNameRepository;
  @Mock private MerchantRuleRepository merchantRuleRepository;

  @InjectMocks private TransactionService service;

  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).name("Test").email("t@e.com").build();
  }

  private CreateTransactionRequest expenseRequest(UUID categoryId) {
    return expenseRequest(categoryId, "ALL");
  }

  private CreateTransactionRequest expenseRequest(UUID categoryId, String propagate) {
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Nagumo");
    req.setAmount(new BigDecimal("120.00"));
    req.setType("EXPENSE");
    req.setDate(LocalDate.of(2026, 5, 10));
    req.setCategoryId(categoryId);
    req.setBudgetGroup("ESSENTIAL");
    req.setPropagate(propagate);
    return req;
  }

  @Test
  void create_defaults_competence_to_purchase_date_when_absent() {
    UUID categoryId = UUID.randomUUID();
    when(categoryRepository.findById(categoryId))
        .thenReturn(Optional.of(Category.builder().id(categoryId).name("Alimentação").build()));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.create(expenseRequest(categoryId), user);

    ArgumentCaptor<Transaction> captor = ArgumentCaptor.forClass(Transaction.class);
    verify(transactionRepository).save(captor.capture());
    assertThat(captor.getValue().getCompetenceDate()).isEqualTo(LocalDate.of(2026, 5, 10));
  }

  @Test
  void update_propagates_classification_to_matching_transactions() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Alimentação").build();

    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .build();
    Transaction other =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionRepository.findByUserIdAndEffectiveName(userId, "Nagumo"))
        .thenReturn(List.of(edited, other));

    service.update(id, expenseRequest(categoryId), user);

    // The sibling transaction (same type) inherits the new classification
    assertThat(other.getCategory()).isEqualTo(category);
    assertThat(other.getBudgetGroup()).isEqualTo("ESSENTIAL");
    // Only same-type siblings are saved; the source transaction is excluded
    verify(transactionRepository).saveAll(List.of(other));
  }

  @Test
  void update_with_CURRENT_scope_does_not_touch_siblings() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Alimentação").build();

    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .build();
    Transaction other =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(merchantRuleRepository.findUserRuleByNormalizedName("Nagumo", userId))
        .thenReturn(Optional.empty());

    service.update(id, expenseRequest(categoryId, "CURRENT"), user);

    assertThat(other.getCategory()).isNull();
    verify(transactionRepository, never()).findByUserIdAndEffectiveName(any(), any());
    verify(transactionRepository, never()).saveAll(any());
  }

  @Test
  void update_with_FUTURE_scope_propagates_only_to_later_transactions() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Alimentação").build();

    // source: competenceDate 2026-05-10 (set via date on expenseRequest)
    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 5, 10))
            .build();
    // past: date 2026-04-01 → should NOT be touched
    Transaction past =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 4, 1))
            .build();
    // future: date 2026-06-01 → should be touched
    Transaction future =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 6, 1))
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionRepository.findByUserIdAndEffectiveName(userId, "Nagumo"))
        .thenReturn(List.of(edited, past, future));
    when(merchantRuleRepository.findUserRuleByNormalizedName("Nagumo", userId))
        .thenReturn(Optional.empty());

    service.update(id, expenseRequest(categoryId, "FUTURE"), user);

    assertThat(past.getCategory()).isNull();
    assertThat(future.getCategory()).isEqualTo(category);
    verify(transactionRepository).saveAll(List.of(future));
  }

  @Test
  void update_does_not_propagate_type_change_to_different_type_siblings() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Pedágio").build();

    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("NuTag")
            .normalizedDescription("nutag")
            .type(TransactionType.INCOME)
            .build();
    Transaction charge =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .description("NuTag")
            .normalizedDescription("nutag")
            .type(TransactionType.EXPENSE)
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionRepository.findByUserIdAndEffectiveName(userId, "nutag"))
        .thenReturn(List.of(edited, charge));

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("NuTag");
    req.setAmount(new BigDecimal("8.10"));
    req.setType("INCOME");
    req.setDate(LocalDate.of(2026, 5, 14));
    req.setCategoryId(categoryId);
    req.setPropagate("ALL");
    service.update(id, req, user);

    // EXPENSE sibling must NOT be touched — different type
    assertThat(charge.getType()).isEqualTo(TransactionType.EXPENSE);
    assertThat(charge.getCategory()).isNull();
    verify(transactionRepository).saveAll(List.of());
  }

  @Test
  void update_learns_merchant_rule_for_future_imports() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Alimentação").build();

    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionRepository.findByUserIdAndEffectiveName(userId, "Nagumo"))
        .thenReturn(List.of(edited));
    when(merchantRuleRepository.findUserRuleByNormalizedName("Nagumo", userId))
        .thenReturn(Optional.empty());

    service.update(id, expenseRequest(categoryId), user);

    ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(captor.capture());
    MerchantRule rule = captor.getValue();
    assertThat(rule.getNormalizedName()).isEqualTo("Nagumo");
    assertThat(rule.getExpenseType()).isEqualTo("ESSENTIAL");
    assertThat(rule.getConfidence()).isEqualTo(100);
    assertThat(rule.getCreatedBy()).isEqualTo("USER");
  }

  @Test
  void update_keeps_needs_review_flag_on_edited_and_matching() {
    UUID id = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Alimentação").build();

    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .needsReview(true)
            .build();
    Transaction other =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .description("Nagumo")
            .normalizedDescription("Nagumo")
            .type(TransactionType.EXPENSE)
            .needsReview(true)
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionRepository.findByUserIdAndEffectiveName(userId, "Nagumo"))
        .thenReturn(List.of(edited, other));

    service.update(id, expenseRequest(categoryId), user);

    // Editing a field must NOT resolve the review — only confirmReview does.
    assertThat(edited.isNeedsReview()).isTrue();
    assertThat(other.isNeedsReview()).isTrue();
  }

  @Test
  void confirmReview_clears_needs_review_without_touching_other_fields() {
    UUID id = UUID.randomUUID();
    Transaction tx =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Nagumo")
            .type(TransactionType.EXPENSE)
            .budgetGroup("ESSENTIAL")
            .needsReview(true)
            .build();

    when(transactionRepository.findById(id)).thenReturn(Optional.of(tx));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.confirmReview(id, user);

    assertThat(tx.isNeedsReview()).isFalse();
    assertThat(tx.getBudgetGroup()).isEqualTo("ESSENTIAL");
    // No propagation on confirm — only the single row is saved.
    verify(transactionRepository, never()).saveAll(any());
  }

  @Test
  void update_learns_income_override_with_type_and_ignored_flag() {
    UUID id = UUID.randomUUID();

    // The user corrects an Open Banking transfer that was auto-ignored: it is actually their
    // salary.
    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Transferência Open Banking Itaú")
            .normalizedDescription("transferencia open banking itau")
            .type(TransactionType.INCOME)
            .ignored(false)
            .build();

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Transferência Open Banking Itaú");
    req.setAmount(new BigDecimal("5000.00"));
    req.setType("INCOME");
    req.setIgnored(false);
    req.setDate(LocalDate.of(2026, 5, 5));

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.update(id, req, user);

    ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(captor.capture());
    MerchantRule rule = captor.getValue();
    assertThat(rule.getType()).isEqualTo("INCOME");
    assertThat(rule.isIgnored()).isFalse();
    assertThat(rule.getCreatedBy()).isEqualTo("USER");
  }

  @Test
  void bulkUpdate_applies_only_provided_fields_respecting_type_and_ownership() {
    UUID catId = UUID.randomUUID();
    Category category = Category.builder().id(catId).name("Alimentação").build();

    Transaction expense =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 5, 10))
            .build();
    Transaction income =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .type(TransactionType.INCOME)
            .date(LocalDate.of(2026, 5, 11))
            .build();
    Transaction investment =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .type(TransactionType.INVESTMENT)
            .date(LocalDate.of(2026, 5, 12))
            .build();
    User other = User.builder().id(UUID.randomUUID()).build();
    Transaction foreign =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(other)
            .type(TransactionType.EXPENSE)
            .date(LocalDate.of(2026, 5, 13))
            .build();

    List<UUID> ids = List.of(expense.getId(), income.getId(), investment.getId(), foreign.getId());
    when(transactionRepository.findAllById(ids))
        .thenReturn(List.of(expense, income, investment, foreign));
    when(categoryRepository.findById(catId)).thenReturn(Optional.of(category));
    when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    BulkUpdateRequest req = new BulkUpdateRequest();
    req.setIds(ids);
    req.setBudgetGroup("ESSENTIAL");
    req.setCategoryId(catId);
    req.setCompetenceMonth("2026-07");
    req.setIgnored(true);

    var result = service.bulkUpdate(req, user);

    // The other user's row is excluded from the result and left untouched.
    assertThat(result).hasSize(3);
    assertThat(foreign.getBudgetGroup()).isNull();
    assertThat(foreign.getCompetenceDate()).isNull();
    // Budget group only lands on the expense.
    assertThat(expense.getBudgetGroup()).isEqualTo("ESSENTIAL");
    assertThat(income.getBudgetGroup()).isNull();
    assertThat(investment.getBudgetGroup()).isNull();
    // Category lands on expense + income, not investment.
    assertThat(expense.getCategory()).isEqualTo(category);
    assertThat(income.getCategory()).isEqualTo(category);
    assertThat(investment.getCategory()).isNull();
    // Competence + ignored land on every owned row.
    assertThat(income.getCompetenceDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(investment.getCompetenceDate()).isEqualTo(LocalDate.of(2026, 7, 1));
    assertThat(expense.isIgnored()).isTrue();
    assertThat(investment.isIgnored()).isTrue();
  }

  @Test
  void bulkUpdate_leaves_null_fields_untouched() {
    Transaction expense =
        Transaction.builder()
            .id(UUID.randomUUID())
            .user(user)
            .type(TransactionType.EXPENSE)
            .budgetGroup("ESSENTIAL")
            .date(LocalDate.of(2026, 5, 10))
            .competenceDate(LocalDate.of(2026, 5, 10))
            .build();
    List<UUID> ids = List.of(expense.getId());
    when(transactionRepository.findAllById(ids)).thenReturn(List.of(expense));
    when(transactionRepository.saveAll(anyList())).thenAnswer(inv -> inv.getArgument(0));

    BulkUpdateRequest req = new BulkUpdateRequest();
    req.setIds(ids);
    req.setIgnored(true); // only un/ignore

    service.bulkUpdate(req, user);

    assertThat(expense.isIgnored()).isTrue();
    assertThat(expense.getBudgetGroup()).isEqualTo("ESSENTIAL"); // untouched
    assertThat(expense.getCompetenceDate()).isEqualTo(LocalDate.of(2026, 5, 10)); // untouched
  }

  @Test
  void update_does_not_learn_a_rule_for_bill_payments() {
    UUID id = UUID.randomUUID();

    // Counting a "Pagamento de fatura" this month must NOT teach future imports to stop ignoring
    // it.
    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Pagamento de fatura")
            .normalizedDescription("pagamento de fatura")
            .type(TransactionType.EXPENSE)
            .ignored(false)
            .build();

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Pagamento de fatura");
    req.setAmount(new BigDecimal("500.00"));
    req.setType("EXPENSE");
    req.setIgnored(false);
    req.setDate(LocalDate.of(2026, 5, 5));

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

    service.update(id, req, user);

    verify(merchantRuleRepository, never()).save(any());
  }
}
