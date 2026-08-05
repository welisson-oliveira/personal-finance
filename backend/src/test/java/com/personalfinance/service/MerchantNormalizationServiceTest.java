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
  void normalize_withUnknownMerchant_returnsCanonicalLowercase() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("EstabelecimentoDesconhecido"))
        .isEqualTo("estabelecimentodesconhecido");
  }

  @Test
  void normalize_withCaseInsensitiveAlias_matches() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("IFOOD DELIVERY")).isEqualTo("iFood");
  }

  @Test
  void normalize_recurringTransfer_differentValueAndDate_yieldsSameCanonical() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    String a = service.normalize("Transferencia Pix Joao 12/05 R$ 100,00");
    String b = service.normalize("Transferencia Pix Joao 15/06 R$ 250,00");
    assertThat(a).isEqualTo(b);
    assertThat(a).isEqualTo("transferencia pix joao");
  }

  @Test
  void normalize_stripsLongIdRuns_butKeepsMerchantName() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    String a = service.normalize("Boleto 12345678 Empresa Agua");
    String b = service.normalize("Boleto 87654321 Empresa Agua");
    assertThat(a).isEqualTo(b).isEqualTo("boleto empresa agua");
  }

  @Test
  void normalize_keepsShortEmbeddedDigits() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("Loja 24h")).isEqualTo("loja 24h");
  }

  @Test
  void normalize_distinctMerchants_stayDistinct() {
    when(merchantAliasRepository.findAllGlobal()).thenReturn(aliases);
    assertThat(service.normalize("Pix Joao R$ 10,00"))
        .isNotEqualTo(service.normalize("Pix Maria R$ 10,00"));
  }
}
