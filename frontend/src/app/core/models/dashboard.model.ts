import { CategoryTotal } from './report.model';

export interface BudgetBreakdown {
  essenciais: CategoryTotal[];
  naoEssenciais: CategoryTotal[];
}

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
  despesasSemGrupo: number;
  aportes: number;
  resgatado: number;
  aplicado: number;
  resultado: number;
  saldoAcumulado: number;
  pagamentosFaturaIgnorados: number;
  rendaBase: number;
  percentualEssenciais: number;
  percentualNaoEssenciais: number;
  percentualInvestimentos: number;
  breakdown?: BudgetBreakdown;
  destaques: Destaques;
}
