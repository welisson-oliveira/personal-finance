package com.personalfinance.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.personalfinance.dto.request.CreateMerchantRuleRequest;
import com.personalfinance.dto.response.MerchantRuleResponse;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.model.entity.User;
import com.personalfinance.repository.CategoryRepository;
import com.personalfinance.repository.MerchantRuleRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class MerchantRuleServiceTest {

  @Mock private MerchantRuleRepository merchantRuleRepository;
  @Mock private CategoryRepository categoryRepository;
  @Mock private MerchantNormalizationService normalizationService;

  @InjectMocks private MerchantRuleService service;

  private User user;
  private UUID userId;

  @BeforeEach
  void setUp() {
    userId = UUID.randomUUID();
    user = User.builder().id(userId).name("Test").email("t@e.com").build();
  }

  private CreateMerchantRuleRequest request(UUID categoryId) {
    return new CreateMerchantRuleRequest(
        "Padaria do Zé", "EXPENSE", categoryId, null, "ESSENTIAL", null, false);
  }

  @Test
  void create_savesUserRule_withNormalizedNameAndOwnership() {
    when(normalizationService.normalize("Padaria do Zé")).thenReturn("padaria do ze");
    when(merchantRuleRepository.findUserRuleByNormalizedName("padaria do ze", userId))
        .thenReturn(Optional.empty());
    when(merchantRuleRepository.save(any(MerchantRule.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    service.create(request(null), user);

    ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(captor.capture());
    MerchantRule saved = captor.getValue();
    assertThat(saved.getUser()).isEqualTo(user);
    assertThat(saved.getNormalizedName()).isEqualTo("padaria do ze");
    assertThat(saved.getType()).isEqualTo("EXPENSE");
    assertThat(saved.getExpenseType()).isEqualTo("ESSENTIAL");
    assertThat(saved.getCreatedBy()).isEqualTo("USER");
  }

  @Test
  void create_investmentRule_setsDirection_clearsCategoryAndUsesFillerGroup() {
    when(normalizationService.normalize("Corretora XP")).thenReturn("corretora xp");
    when(merchantRuleRepository.findUserRuleByNormalizedName("corretora xp", userId))
        .thenReturn(Optional.empty());
    when(merchantRuleRepository.save(any(MerchantRule.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CreateMerchantRuleRequest req =
        new CreateMerchantRuleRequest(
            "Corretora XP",
            "INVESTMENT",
            UUID.randomUUID(),
            null,
            "ESSENTIAL",
            "CONTRIBUTION",
            false);
    service.create(req, user);

    ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(captor.capture());
    MerchantRule saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo("INVESTMENT");
    assertThat(saved.getInvestmentDirection()).isEqualTo("CONTRIBUTION");
    assertThat(saved.getCategory()).isNull(); // investment carries no category
    assertThat(saved.getExpenseType()).isEqualTo("NON_ESSENTIAL"); // filler, group not applicable
    verify(categoryRepository, never()).findById(any());
  }

  @Test
  void create_incomeRule_canBeIgnored_andHasNoDirection() {
    when(normalizationService.normalize("Transf Própria")).thenReturn("transf propria");
    when(merchantRuleRepository.findUserRuleByNormalizedName("transf propria", userId))
        .thenReturn(Optional.empty());
    when(merchantRuleRepository.save(any(MerchantRule.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    CreateMerchantRuleRequest req =
        new CreateMerchantRuleRequest("Transf Própria", "INCOME", null, null, null, null, true);
    service.create(req, user);

    ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(captor.capture());
    MerchantRule saved = captor.getValue();
    assertThat(saved.getType()).isEqualTo("INCOME");
    assertThat(saved.isIgnored()).isTrue();
    assertThat(saved.getInvestmentDirection()).isNull();
  }

  @Test
  void create_rejectsDuplicateUserRule() {
    when(normalizationService.normalize("Padaria do Zé")).thenReturn("padaria do ze");
    when(merchantRuleRepository.findUserRuleByNormalizedName("padaria do ze", userId))
        .thenReturn(Optional.of(MerchantRule.builder().merchantName("Padaria do Zé").build()));

    assertThatThrownBy(() -> service.create(request(null), user))
        .isInstanceOf(IllegalArgumentException.class);
    verify(merchantRuleRepository, never()).save(any());
  }

  @Test
  void update_ownRule_editsInPlace() {
    UUID ruleId = UUID.randomUUID();
    MerchantRule owned =
        MerchantRule.builder()
            .id(ruleId)
            .user(user)
            .merchantName("old")
            .normalizedName("padaria do ze")
            .expenseType("NON_ESSENTIAL")
            .build();
    when(merchantRuleRepository.findById(ruleId)).thenReturn(Optional.of(owned));
    when(merchantRuleRepository.save(any(MerchantRule.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MerchantRuleResponse res = service.update(ruleId, request(null), user);

    assertThat(res.merchantName()).isEqualTo("Padaria do Zé");
    assertThat(res.expenseType()).isEqualTo("ESSENTIAL");
    assertThat(owned.getNormalizedName()).isEqualTo("padaria do ze"); // key unchanged
    verify(merchantRuleRepository, never()).findUserRuleByNormalizedName(any(), any());
  }

  @Test
  void update_globalRule_createsPersonalOverride_withoutMutatingGlobal() {
    UUID globalId = UUID.randomUUID();
    MerchantRule global =
        MerchantRule.builder()
            .id(globalId)
            .user(null)
            .merchantName("Padaria")
            .normalizedName("padaria do ze")
            .expenseType("NON_ESSENTIAL")
            .createdBy("SYSTEM")
            .build();
    when(merchantRuleRepository.findById(globalId)).thenReturn(Optional.of(global));
    when(merchantRuleRepository.findUserRuleByNormalizedName("padaria do ze", userId))
        .thenReturn(Optional.empty());
    when(merchantRuleRepository.save(any(MerchantRule.class)))
        .thenAnswer(inv -> inv.getArgument(0));

    MerchantRuleResponse res = service.update(globalId, request(null), user);

    ArgumentCaptor<MerchantRule> captor = ArgumentCaptor.forClass(MerchantRule.class);
    verify(merchantRuleRepository).save(captor.capture());
    MerchantRule override = captor.getValue();
    assertThat(override.getUser()).isEqualTo(user); // override is owned by the user
    assertThat(override.getId()).isNull(); // brand-new, not the global
    assertThat(override.getNormalizedName()).isEqualTo("padaria do ze"); // matches same merchant
    assertThat(override.getExpenseType()).isEqualTo("ESSENTIAL");
    assertThat(res.global()).isFalse();
    // global left untouched
    assertThat(global.getExpenseType()).isEqualTo("NON_ESSENTIAL");
    assertThat(global.getCreatedBy()).isEqualTo("SYSTEM");
  }

  @Test
  void update_otherUsersRule_isForbidden() {
    UUID ruleId = UUID.randomUUID();
    User other = User.builder().id(UUID.randomUUID()).build();
    MerchantRule foreign =
        MerchantRule.builder().id(ruleId).user(other).normalizedName("x").build();
    when(merchantRuleRepository.findById(ruleId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.update(ruleId, request(null), user))
        .isInstanceOf(AccessDeniedException.class);
    verify(merchantRuleRepository, never()).save(any());
  }

  @Test
  void delete_ownRule_deletes() {
    UUID ruleId = UUID.randomUUID();
    MerchantRule owned = MerchantRule.builder().id(ruleId).user(user).build();
    when(merchantRuleRepository.findById(ruleId)).thenReturn(Optional.of(owned));

    service.delete(ruleId, user);

    verify(merchantRuleRepository).delete(owned);
  }

  @Test
  void delete_globalRule_isForbidden() {
    UUID globalId = UUID.randomUUID();
    MerchantRule global = MerchantRule.builder().id(globalId).user(null).build();
    when(merchantRuleRepository.findById(globalId)).thenReturn(Optional.of(global));

    assertThatThrownBy(() -> service.delete(globalId, user))
        .isInstanceOf(AccessDeniedException.class);
    verify(merchantRuleRepository, never()).delete(any());
  }

  @Test
  void delete_otherUsersRule_isForbidden() {
    UUID ruleId = UUID.randomUUID();
    User other = User.builder().id(UUID.randomUUID()).build();
    MerchantRule foreign = MerchantRule.builder().id(ruleId).user(other).build();
    when(merchantRuleRepository.findById(ruleId)).thenReturn(Optional.of(foreign));

    assertThatThrownBy(() -> service.delete(ruleId, user))
        .isInstanceOf(AccessDeniedException.class);
    verify(merchantRuleRepository, never()).delete(any());
  }
}
