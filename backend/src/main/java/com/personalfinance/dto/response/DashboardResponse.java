package com.personalfinance.dto.response;

import java.math.BigDecimal;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DashboardResponse {

  private int year;
  private int month;

  // "Quanto entrou": entradas do mês (receita disponível). Ignora transferências/ignorados.
  private BigDecimal entradas;

  // "Para onde foi": despesas 50/30 + investimento (20).
  private BigDecimal despesasEssenciais;
  private BigDecimal despesasNaoEssenciais;
  private BigDecimal totalDespesas;

  private BigDecimal aportes; // aportes brutos do mês (CONTRIBUTION)
  private BigDecimal resgatado; // resgates brutos do mês (REDEMPTION)
  private BigDecimal aplicado; // aporte líquido no mês (aportes − resgates)

  // "Quanto sobrou": resultado do mês (entradas − despesas).
  private BigDecimal resultado;

  private BigDecimal rendaBase;
  private BigDecimal percentualEssenciais;
  private BigDecimal percentualNaoEssenciais;
  private BigDecimal percentualInvestimentos;

  private Destaques destaques;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Destaques {
    private String maiorSupermercado;
    private BigDecimal maiorSupermercadoValor;
    private String maiorDelivery;
    private BigDecimal maiorDeliveryValor;
    private long quantidadeAssinaturas;
    private long quantidadeCompras;
    private long quantidadePixEnviados;
    private long quantidadePixRecebidos;
  }
}
