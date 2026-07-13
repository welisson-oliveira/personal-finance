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
}
