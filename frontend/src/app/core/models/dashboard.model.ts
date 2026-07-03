export interface Destaques {
  maiorSupermercado?: string;
  maiorSupermercadoValor?: number;
  maiorDelivery?: string;
  maiorDeliveryValor?: number;
  quantidadeAssinaturas: number;
  quantidadeCompras: number;
  quantidadePixEnviados: number;
  quantidadePixRecebidos: number;
}

export interface DashboardResponse {
  year: number;
  month: number;
  receitaBruta: number;
  reembolsos: number;
  receitaReal: number;
  despesasEssenciais: number;
  despesasNaoEssenciais: number;
  totalDespesas: number;
  investido: number;
  resgatado: number;
  saldo: number;
  percentualEssenciais: number;
  percentualNaoEssenciais: number;
  percentualInvestimentos: number;
  destaques: Destaques;
}
