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

  // "Insights do mês": leitura acionável do mês (substitui os antigos "Destaques").
  private Insights insights;

  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Breakdown {
    private List<CategoryTotalResponse> essenciais;
    private List<CategoryTotalResponse> naoEssenciais;
  }

  /**
   * Leitura acionável do mês, cruzando gastos, metas e histórico recente. Cada bloco é opcional (o
   * front só renderiza o que veio preenchido).
   */
  @Getter
  @Builder
  @NoArgsConstructor
  @AllArgsConstructor
  public static class Insights {
    // 1. Maiores gastos individuais do mês (onde o dinheiro vaza).
    private List<TopExpenseResponse> maioresGastos;

    // 2. Comparativo com o mês passado (total + categoria que mais subiu).
    private BigDecimal totalMesAtual;
    private BigDecimal totalMesAnterior;
    private BigDecimal variacaoPercentual; // null quando não há base anterior
    private String categoriaQueMaisSubiu; // null quando nada subiu
    private BigDecimal categoriaQueMaisSubiuValor; // total atual da categoria
    private BigDecimal categoriaQueMaisSubiuVariacao; // aumento em R$ vs mês anterior

    // 3. Assinaturas & recorrentes (gasto mensal comprometido).
    private List<RecurringItem> recorrentes;
    private BigDecimal totalRecorrente;

    // 4. Ritmo do mês / projeção de fechamento (só no mês corrente).
    private boolean mesCorrente;
    private int diasDecorridos;
    private int diasNoMes;
    private BigDecimal projecaoFechamento; // null fora do mês corrente

    // 5. Metas de orçamento estouradas (categoria acima de 100%).
    private List<GoalExceeded> metasEstouradas;

    // 6. Pequenos gastos que, somados, pesaram muito.
    private List<SmallExpenseGroup> pequenosGastos;
  }

  /** Uma cobrança recorrente/assinatura detectada no mês. {@code nova} = apareceu pela 1ª vez. */
  public record RecurringItem(String nome, BigDecimal valor, boolean nova) {}

  /** Uma meta de orçamento estourada no mês. */
  public record GoalExceeded(
      String categoriaNome,
      String categoriaIcon,
      String categoriaColor,
      BigDecimal gasto,
      BigDecimal teto,
      BigDecimal percentual) {}

  /** Um grupo de gastos pequenos e frequentes que, somados, viraram um valor relevante. */
  public record SmallExpenseGroup(String nome, long quantidade, BigDecimal total) {}
}
