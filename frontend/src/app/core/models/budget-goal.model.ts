export interface BudgetGoal {
  id: string;
  categoryId: string;
  categoryName: string;
  categoryIcon?: string;
  categoryColor?: string;
  amount: number;
  spent: number;
  remaining: number;
  percentage: number;
}

export interface CreateBudgetGoalRequest {
  categoryId: string;
  amount: number;
}

export interface CategorySuggestion {
  categoryId: string;
  categoryName: string;
  categoryIcon?: string;
  categoryColor?: string;
  historicalMonthly: number;
  suggestedAmount: number;
  hasGoal: boolean;
}

export interface BucketSuggestion {
  group: 'ESSENTIAL' | 'NON_ESSENTIAL';
  cap: number;
  historicalTotal: number;
  suggestedTotal: number;
  overCap: boolean;
  categories: CategorySuggestion[];
}

export interface InvestmentSuggestion {
  cap: number;
  historicalMonthly: number;
  suggestedAmount: number;
}

export interface BudgetSuggestion {
  rendaBase: number;
  buckets: BucketSuggestion[];
  investimentos: InvestmentSuggestion;
}

export interface BulkBudgetGoalItem {
  categoryId: string;
  amount: number;
}
