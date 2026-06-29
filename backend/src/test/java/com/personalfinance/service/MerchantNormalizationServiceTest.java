package com.personalfinance.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import com.personalfinance.model.entity.MerchantAlias;
import com.personalfinance.model.entity.MerchantRule;
import com.personalfinance.repository.MerchantAliasRepository;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class MerchantNormalizationServiceTest {

  @Mock private MerchantAliasRepository merchantAliasRepository;

  @InjectMocks private MerchantNormalizationService service;

  private List<MerchantAlias> aliases;

  @BeforeEach
  void setUp() {
    MerchantRule amazon =
        MerchantRule.builder().merchantName("Amazon").normalizedName("Amazon").build();
    MerchantRule ifood =
        MerchantRule.builder().merchantName("iFood").normalizedName("iFood").build();
    MerchantRule anthropic =
        MerchantRule.builder().merchantName("Anthropic").normalizedName("Anthropic").build();

    aliases =
        List.of(
            MerchantAlias.builder().alias("AmazonMktplc").merchantRule(amazon).build(),
            MerchantAlias.builder().alias("Amazon Marketplace").merchantRule(amazon).build(),
            MerchantAlias.builder().alias("Amazonmktplc").merchantRule(amazon).build(),
            MerchantAlias.builder().alias("iFood - NuPay").merchantRule(ifood).build(),
            MerchantAlias.builder().alias("IFood").merchantRule(ifood).build(),
            MerchantAlias.builder().alias("Anthropic* Claude Sub").merchantRule(anthropic).build());
  }

  @Test
  void normalize_withAmazonMktplcVariant_returnsAmazon() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("AmazonMktplc*Belezavar")).isEqualTo("Amazon");
  }

  @Test
  void normalize_withIFoodNuPay_returnsIFood() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("iFood - NuPay")).isEqualTo("iFood");
  }

  @Test
  void normalize_withAnthropicClaudeSub_returnsAnthropic() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("Anthropic* Claude Sub")).isEqualTo("Anthropic");
  }

  @Test
  void normalize_withUnknownMerchant_returnsRawDescription() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("EstabelecimentoDesconhecido"))
        .isEqualTo("EstabelecimentoDesconhecido");
  }

  @Test
  void normalize_withCaseInsensitiveAlias_matches() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("IFOOD DELIVERY")).isEqualTo("iFood");
  }
}
