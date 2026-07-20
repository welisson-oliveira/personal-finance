import { CategoryTotal, TopExpense } from './report.model';

export interface BudgetBreakdown {
  essenciais: CategoryTotal[];
  naoEssenciais: CategoryTotal[];
}

export interface RecurringItem {
  nome: string;
  valor: number;
  nova: boolean;
}

export interface GoalExceeded {
  categoriaNome: string;
  categoriaIcon?: string | null;
  categoriaColor?: string | null;
  gasto: number;
  teto: number;
  percentual: number;
}

export interface SmallExpenseGroup {
  nome: string;
  quantidade: number;
  total: number;
}

export interface Insights {
  // 1. Maiores gastos individuais do mês.
  maioresGastos: TopExpense[];
  // 2. Comparativo com o mês passado.
  totalMesAtual: number;
  totalMesAnterior: number;
  variacaoPercentual?: number | null;
  categoriaQueMaisSubiu?: string | null;
  categoriaQueMaisSubiuValor?: number | null;
  categoriaQueMaisSubiuVariacao?: number | null;
  // 3. Assinaturas & recorrentes.
  recorrentes: RecurringItem[];
  totalRecorrente: number;
  // 4. Ritmo do mês / projeção de fechamento (só no mês corrente).
  mesCorrente: boolean;
  diasDecorridos: number;
  diasNoMes: number;
  projecaoFechamento?: number | null;
  // 5. Metas de orçamento estouradas.
  metasEstouradas: GoalExceeded[];
  // 6. Pequenos gastos que somaram muito.
  pequenosGastos: SmallExpenseGroup[];
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
  salarioEsperado: number;
  resultadoPrevisto: number;
  usandoSalarioPrevisto: boolean;
  rendaBase: number;
  percentualEssenciais: number;
  percentualNaoEssenciais: number;
  percentualInvestimentos: number;
  breakdown?: BudgetBreakdown;
  entradasBreakdown?: CategoryTotal[];
  insights?: Insights;
}
