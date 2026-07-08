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
