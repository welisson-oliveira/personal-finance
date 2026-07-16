package com.personalfinance.dto.response;

import java.math.BigDecimal;
import java.util.List;
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
  private BigDecimal
      despesasSemGrupo; // despesas não-ignoradas fora do 50/30/20 (ex.: fatura de transição)

  private BigDecimal aportes; // aportes brutos do mês (CONTRIBUTION)
  private BigDecimal resgatado; // resgates brutos do mês (REDEMPTION)
  private BigDecimal aplicado; // aporte líquido no mês (aportes − resgates)

  // "Quanto sobrou": resultado do mês (entradas − despesas).
  private BigDecimal resultado;

  // Saldo corrido "em conta" acumulado até o fim do mês (saldo inicial + receitas − despesas −
  // aportes líquidos, a partir da data do saldo inicial).
  private BigDecimal saldoAcumulado;
  // Soma dos "Pagamento de fatura" ainda ignorados no mês (sinal do furo de transição).
  private BigDecimal pagamentosFaturaIgnorados;

  // Salário previsto (Opção A): no mês corrente, usa o salário configurado como piso da renda até o
  // salário real ser importado — evita o "limbo de caixa" (fatura no dia 8, extrato no dia 31).
  private BigDecimal salarioEsperado;
  private BigDecimal resultadoPrevisto; // receita projetada − despesas (com o salário previsto)
  private boolean usandoSalarioPrevisto; // true quando a projeção acrescenta renda além da real

  private BigDecimal rendaBase;
  private BigDecimal percentualEssenciais;
  private BigDecimal percentualNaoEssenciais;
  private BigDecimal percentualInvestimentos;

  // Drill-down do 50/30/20: quais categorias compõem cada bucket de despesa (maior→menor).
  private Breakdown breakdown;

  // "De onde veio o dinheiro": entradas do mês agrupadas por categoria (maior→menor).
  private List<CategoryTotalResponse> entradasBreakdown;

  private Destaques destaques;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Breakdown {
    private List<CategoryTotalResponse> essenciais;
    private List<CategoryTotalResponse> naoEssenciais;
  }

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
