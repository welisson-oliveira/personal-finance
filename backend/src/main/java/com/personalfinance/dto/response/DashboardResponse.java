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

  private BigDecimal receitaBruta;
  private BigDecimal reembolsos;
  private BigDecimal receitaReal;

  private BigDecimal despesasEssenciais;
  private BigDecimal despesasNaoEssenciais;
  private BigDecimal totalDespesas;

  private BigDecimal investido;
  private BigDecimal resgatado;

  private BigDecimal saldo;
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
