package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

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
    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Nagumo");
    req.setAmount(new BigDecimal("120.00"));
    req.setType("EXPENSE");
    req.setDate(LocalDate.of(2026, 5, 10));
    req.setCategoryId(categoryId);
    req.setBudgetGroup("ESSENTIAL");
    return req;
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

    // The sibling transaction inherits the new classification
    assertThat(other.getCategory()).isEqualTo(category);
    assertThat(other.getBudgetGroup()).isEqualTo("ESSENTIAL");
    verify(transactionRepository).saveAll(List.of(edited, other));
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
  void update_does_not_create_merchant_rule_for_income() {
    UUID id = UUID.randomUUID();

    Transaction edited =
        Transaction.builder()
            .id(id)
            .user(user)
            .description("Salário")
            .normalizedDescription("Salário")
            .type(TransactionType.INCOME)
            .build();

    CreateTransactionRequest req = new CreateTransactionRequest();
    req.setDescription("Salário");
    req.setAmount(new BigDecimal("5000.00"));
    req.setType("INCOME");
    req.setDate(LocalDate.of(2026, 5, 5));

    when(transactionRepository.findById(id)).thenReturn(Optional.of(edited));
    when(transactionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    when(transactionRepository.findByUserIdAndEffectiveName(userId, "Salário"))
        .thenReturn(List.of(edited));

    service.update(id, req, user);

    verify(merchantRuleRepository, never()).save(any());
  }
}
