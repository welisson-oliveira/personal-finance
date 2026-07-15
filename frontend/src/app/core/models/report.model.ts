export interface MonthlyPoint {
  year: number;
  month: number;
  receita: number;
  despesa: number;
  saldo: number;
}

export interface CategoryTotal {
  categoryId: string;
  categoryName: string;
  categoryIcon?: string;
  categoryColor?: string;
  total: number;
  /** Previous month's total for the same category (Reports breakdown only; null on Dashboard). */
  previousTotal?: number | null;
  /** Month-over-month change in %, or null when there was no previous base to compare to. */
  deltaPercent?: number | null;
}

export interface TopExpense {
  id: string;
  description: string;
  categoryName?: string | null;
  categoryColor?: string | null;
  date: string;
  amount: number;
}
