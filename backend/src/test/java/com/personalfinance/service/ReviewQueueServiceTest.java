package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.request.ResolveReviewRequest;
import com.personalfinance.dto.response.ReviewQueueItemResponse;
import com.personalfinance.model.entity.*;
import com.personalfinance.repository.*;
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
class ReviewQueueServiceTest {

  @Mock private ReviewQueueRepository reviewQueueRepository;
  @Mock private MerchantRuleRepository merchantRuleRepository;
  @Mock private MerchantAliasRepository merchantAliasRepository;
  @Mock private TransactionRepository transactionRepository;
  @Mock private CategoryRepository categoryRepository;

  @InjectMocks private ReviewQueueService service;

  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).name("Test User").email("test@example.com").build();
  }

  @Test
  void findPending_returns_only_pending_items_for_user() {
    ReviewQueue item =
        ReviewQueue.builder()
            .id(UUID.randomUUID())
            .user(user)
            .rawDescription("iFood - NuPay")
            .normalizedDescription("iFood")
            .amount(new BigDecimal("45.90"))
            .transactionDate(LocalDate.of(2026, 5, 10))
            .status("PENDING")
            .build();

    when(reviewQueueRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING"))
        .thenReturn(List.of(item));

    List<ReviewQueueItemResponse> result = service.findPending(userId);

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRawDescription()).isEqualTo("iFood - NuPay");
    assertThat(result.get(0).getNormalizedDescription()).isEqualTo("iFood");
    assertThat(result.get(0).getStatus()).isEqualTo("PENDING");
  }

  @Test
  void findPending_returns_empty_list_when_no_items() {
    when(reviewQueueRepository.findByUserIdAndStatusOrderByCreatedAtDesc(userId, "PENDING"))
        .thenReturn(List.of());

    assertThat(service.findPending(userId)).isEmpty();
  }

  @Test
  void resolve_marks_item_as_reviewed() {
    UUID reviewId = UUID.randomUUID();
    UUID categoryId = UUID.randomUUID();
    Category category = Category.builder().id(categoryId).name("Alimentação").build();
    ImportSession session = ImportSession.builder().id(UUID.randomUUID()).build();

    ReviewQueue item =
        ReviewQueue.builder()
            .id(reviewId)
            .user(user)
            .rawDescription("Loja Desconhecida")
            .normalizedDescription("Loja Desconhecida")
            .amount(new BigDecimal("100.00"))
            .transactionDate(LocalDate.of(2026, 5, 15))
            .importSession(session)
            .status("PENDING")
            .build();

    ResolveReviewRequest request =
        new ResolveReviewRequest(categoryId, "NON_ESSENTIAL", "Loja Desconhecida");

    when(reviewQueueRepository.findById(reviewId)).thenReturn(Optional.of(item));
    when(categoryRepository.findById(categoryId)).thenReturn(Optional.of(category));
    when(merchantRuleRepository.findUserRuleByNormalizedName("Loja Desconhecida", userId))
        .thenReturn(Optional.empty());
    MerchantRule savedRule =
        MerchantRule.builder()
            .id(UUID.randomUUID())
            .user(user)
            .merchantName("Loja Desconhecida")
            .normalizedName("Loja Desconhecida")
            .category(category)
            .expenseType("NON_ESSENTIAL")
            .confidence(100)
            .createdBy("USER")
            .build();
    when(merchantRuleRepository.save(any())).thenReturn(savedRule);
    when(merchantAliasRepository.findByAliasIgnoreCase("Loja Desconhecida"))
        .thenReturn(Optional.empty());
    when(transactionRepository.findByImportSessionIdAndUserId(session.getId(), userId))
        .thenReturn(List.of());

    service.resolve(reviewId, request, user);

    ArgumentCaptor<ReviewQueue> captor = ArgumentCaptor.forClass(ReviewQueue.class);
    verify(reviewQueueRepository).save(captor.capture());
    assertThat(captor.getValue().getStatus()).isEqualTo("REVIEWED");
    assertThat(captor.getValue().getResolvedAt()).isNotNull();
  }

  @Test
  void resolve_creates_merchant_rule_with_user_confidence_100() {
    UUID reviewId = UUID.randomUUID();
    ImportSession session = ImportSession.builder().id(UUID.randomUUID()).build();
    ReviewQueue item =
        ReviewQueue.builder()
            .id(reviewId)
            .user(user)
            .rawDescription("Padaria Central")
            .normalizedDescription("Padaria Central")
            .amount(new BigDecimal("25.00"))
            .transactionDate(LocalDate.of(2026, 5, 20))
            .importSession(session)
            .status("PENDING")
            .build();

    ResolveReviewRequest request = new ResolveReviewRequest(null, "ESSENTIAL", "Padaria Central");

    when(reviewQueueRepository.findById(reviewId)).thenReturn(Optional.of(item));
    when(merchantRuleRepository.findUserRuleByNormalizedName("Padaria Central", userId))
        .thenReturn(Optional.empty());
    MerchantRule savedRule =
        MerchantRule.builder()
            .id(UUID.randomUUID())
            .merchantName("Padaria Central")
            .normalizedName("Padaria Central")
            .expenseType("ESSENTIAL")
            .confidence(100)
            .createdBy("USER")
            .build();
    when(merchantRuleRepository.save(any())).thenReturn(savedRule);
    when(merchantAliasRepository.findByAliasIgnoreCase("Padaria Central"))
        .thenReturn(Optional.empty());
    when(transactionRepository.findByImportSessionIdAndUserId(session.getId(), userId))
        .thenReturn(List.of());

    service.resolve(reviewId, request, user);

    ArgumentCaptor<MerchantRule> ruleCaptor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(ruleCaptor.capture());
    MerchantRule created = ruleCaptor.getValue();
    assertThat(created.getConfidence()).isEqualTo(100);
    assertThat(created.getCreatedBy()).isEqualTo("USER");
    assertThat(created.getMerchantName()).isEqualTo("Padaria Central");
    assertThat(created.getExpenseType()).isEqualTo("ESSENTIAL");
  }

  @Test
  void resolve_creates_alias_for_raw_description() {
    UUID reviewId = UUID.randomUUID();
    ImportSession session = ImportSession.builder().id(UUID.randomUUID()).build();
    ReviewQueue item =
        ReviewQueue.builder()
            .id(reviewId)
            .user(user)
            .rawDescription("Padaria Central Ltda")
            .normalizedDescription("Padaria Central")
            .amount(new BigDecimal("30.00"))
            .transactionDate(LocalDate.of(2026, 5, 21))
            .importSession(session)
            .status("PENDING")
            .build();

    ResolveReviewRequest request = new ResolveReviewRequest(null, "ESSENTIAL", "Padaria Central");

    when(reviewQueueRepository.findById(reviewId)).thenReturn(Optional.of(item));
    MerchantRule savedRule =
        MerchantRule.builder().id(UUID.randomUUID()).normalizedName("Padaria Central").build();
    when(merchantRuleRepository.findUserRuleByNormalizedName("Padaria Central", userId))
        .thenReturn(Optional.empty());
    when(merchantRuleRepository.save(any())).thenReturn(savedRule);
    when(merchantAliasRepository.findByAliasIgnoreCase("Padaria Central Ltda"))
        .thenReturn(Optional.empty());
    when(transactionRepository.findByImportSessionIdAndUserId(session.getId(), userId))
        .thenReturn(List.of());

    service.resolve(reviewId, request, user);

    ArgumentCaptor<MerchantAlias> aliasCaptor = ArgumentCaptor.forClass(MerchantAlias.class);
    verify(merchantAliasRepository).save(aliasCaptor.capture());
    assertThat(aliasCaptor.getValue().getAlias()).isEqualTo("Padaria Central Ltda");
  }

  @Test
  void resolve_does_not_duplicate_alias_if_already_exists() {
    UUID reviewId = UUID.randomUUID();
    ImportSession session = ImportSession.builder().id(UUID.randomUUID()).build();
    ReviewQueue item =
        ReviewQueue.builder()
            .id(reviewId)
            .user(user)
            .rawDescription("iFood - NuPay")
            .normalizedDescription("iFood")
            .amount(new BigDecimal("60.00"))
            .transactionDate(LocalDate.of(2026, 5, 22))
            .importSession(session)
            .status("PENDING")
            .build();

    ResolveReviewRequest request = new ResolveReviewRequest(null, "NON_ESSENTIAL", "iFood");
    MerchantRule existingRule =
        MerchantRule.builder()
            .id(UUID.randomUUID())
            .normalizedName("iFood")
            .expenseType("NON_ESSENTIAL")
            .confidence(100)
            .createdBy("USER")
            .build();

    when(reviewQueueRepository.findById(reviewId)).thenReturn(Optional.of(item));
    when(merchantRuleRepository.findUserRuleByNormalizedName("iFood", userId))
        .thenReturn(Optional.of(existingRule));
    when(merchantRuleRepository.save(any())).thenReturn(existingRule);
    when(merchantAliasRepository.findByAliasIgnoreCase("iFood - NuPay"))
        .thenReturn(Optional.of(MerchantAlias.builder().alias("iFood - NuPay").build()));
    when(transactionRepository.findByImportSessionIdAndUserId(session.getId(), userId))
        .thenReturn(List.of());

    service.resolve(reviewId, request, user);

    verify(merchantAliasRepository, never()).save(any());
  }

  @Test
  void resolve_throws_when_item_not_owned_by_user() {
    UUID reviewId = UUID.randomUUID();
    User otherUser = User.builder().id(UUID.randomUUID()).build();
    ReviewQueue item =
        ReviewQueue.builder()
            .id(reviewId)
            .user(otherUser)
            .rawDescription("Any")
            .amount(BigDecimal.TEN)
            .transactionDate(LocalDate.now())
            .status("PENDING")
            .build();

    when(reviewQueueRepository.findById(reviewId)).thenReturn(Optional.of(item));

    assertThatThrownBy(
            () ->
                service.resolve(reviewId, new ResolveReviewRequest(null, "ESSENTIAL", "Any"), user))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("Review item not found");
  }

  @Test
  void resolve_applies_rule_to_matching_session_transactions() {
    UUID reviewId = UUID.randomUUID();
    UUID sessionId = UUID.randomUUID();
    ImportSession session = ImportSession.builder().id(sessionId).build();

    ReviewQueue item =
        ReviewQueue.builder()
            .id(reviewId)
            .user(user)
            .rawDescription("Padaria X")
            .normalizedDescription("Padaria X")
            .amount(new BigDecimal("20.00"))
            .transactionDate(LocalDate.of(2026, 5, 10))
            .importSession(session)
            .status("PENDING")
            .build();

    Category category = Category.builder().id(UUID.randomUUID()).name("Alimentação").build();
    ResolveReviewRequest request =
        new ResolveReviewRequest(category.getId(), "ESSENTIAL", "Padaria X");

    Transaction matchingTx =
        Transaction.builder().id(UUID.randomUUID()).normalizedDescription("padaria x").build();

    MerchantRule savedRule =
        MerchantRule.builder()
            .id(UUID.randomUUID())
            .normalizedName("Padaria X")
            .category(category)
            .expenseType("ESSENTIAL")
            .confidence(100)
            .createdBy("USER")
            .build();

    when(reviewQueueRepository.findById(reviewId)).thenReturn(Optional.of(item));
    when(categoryRepository.findById(category.getId())).thenReturn(Optional.of(category));
    when(merchantRuleRepository.findUserRuleByNormalizedName("Padaria X", userId))
        .thenReturn(Optional.empty());
    when(merchantRuleRepository.save(any())).thenReturn(savedRule);
    when(merchantAliasRepository.findByAliasIgnoreCase("Padaria X")).thenReturn(Optional.empty());
    when(transactionRepository.findByImportSessionIdAndUserId(sessionId, userId))
        .thenReturn(List.of(matchingTx));

    service.resolve(reviewId, request, user);

    verify(transactionRepository).save(matchingTx);
    assertThat(matchingTx.getCategory()).isEqualTo(category);
    assertThat(matchingTx.getBudgetGroup()).isEqualTo("ESSENTIAL");
  }
}
