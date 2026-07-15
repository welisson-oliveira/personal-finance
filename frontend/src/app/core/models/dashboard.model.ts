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
  entradas: number;
  despesasEssenciais: number;
  despesasNaoEssenciais: number;
  totalDespesas: number;
  aportes: number;
  resgatado: number;
  aplicado: number;
  resultado: number;
  rendaBase: number;
  percentualEssenciais: number;
  percentualNaoEssenciais: number;
  percentualInvestimentos: number;
  destaques: Destaques;
}
