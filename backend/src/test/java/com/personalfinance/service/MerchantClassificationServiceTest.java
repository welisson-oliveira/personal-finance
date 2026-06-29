package com.personalfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.personalfinance.model.entity.Category;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.repository.MerchantRuleRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantClassificationServiceTest {

  @Mock private MerchantRuleRepository merchantRuleRepository;

  @InjectMocks private MerchantClassificationService service;

  @Test
  void classify_iFood_returnsNonEssentialAlimentation() {
    Category alimentation = Category.builder().name("Alimentação").build();
    MerchantRule rule =
        MerchantRule.builder()
            .merchantName("iFood")
            .normalizedName("iFood")
            .category(alimentation)
            .subcategory("Delivery")
            .expenseType("NON_ESSENTIAL")
            .confidence(100)
            .build();
    when(merchantRuleRepository.findGlobalByNormalizedName("iFood")).thenReturn(Optional.of(rule));

    ClassificationResult result = service.classify("iFood");

    assertThat(result.getCategoryName()).isEqualTo("Alimentação");
    assertThat(result.getExpenseType()).isEqualTo("NON_ESSENTIAL");
    assertThat(result.getConfidence()).isEqualTo(100);
    assertThat(result.isKnown()).isTrue();
    assertThat(result.isAutoClassifiable()).isTrue();
  }

  @Test
  void classify_Nagumo_returnsEssentialAlimentation() {
    Category alimentation = Category.builder().name("Alimentação").build();
    MerchantRule rule =
        MerchantRule.builder()
            .merchantName("Nagumo")
            .normalizedName("Nagumo")
            .category(alimentation)
            .subcategory("Supermercado")
            .expenseType("ESSENTIAL")
            .confidence(100)
            .build();
    when(merchantRuleRepository.findGlobalByNormalizedName("Nagumo")).thenReturn(Optional.of(rule));

    ClassificationResult result = service.classify("Nagumo");

    assertThat(result.getCategoryName()).isEqualTo("Alimentação");
    assertThat(result.getExpenseType()).isEqualTo("ESSENTIAL");
    assertThat(result.getConfidence()).isEqualTo(100);
  }

  @Test
  void classify_unknownMerchant_returnsZeroConfidence() {
    when(merchantRuleRepository.findGlobalByNormalizedName("EstabelecimentoDesconhecido"))
        .thenReturn(Optional.empty());

    ClassificationResult result = service.classify("EstabelecimentoDesconhecido");

    assertThat(result.getConfidence()).isEqualTo(0);
    assertThat(result.isKnown()).isFalse();
    assertThat(result.getCategoryName()).isNull();
  }

  @Test
  void classify_lowConfidenceMerchant_isNotAutoClassifiable() {
    Category cat = Category.builder().name("Outros").build();
    MerchantRule rule =
        MerchantRule.builder()
            .merchantName("Loja Duvidosa")
            .normalizedName("Loja Duvidosa")
            .category(cat)
            .expenseType("NON_ESSENTIAL")
            .confidence(60)
            .build();
    when(merchantRuleRepository.findGlobalByNormalizedName("Loja Duvidosa"))
        .thenReturn(Optional.of(rule));

    ClassificationResult result = service.classify("Loja Duvidosa");

    assertThat(result.getConfidence()).isEqualTo(60);
    assertThat(result.isKnown()).isTrue();
    assertThat(result.isAutoClassifiable()).isFalse();
  }
}
