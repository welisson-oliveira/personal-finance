export interface Transaction {
  id: string;
  description: string;
  normalizedDescription?: string;
  amount: number;
  type: string;
  incomeType?: string;
  budgetGroup?: string;
  date: string;
  notes?: string;
  categoryId?: string;
  categoryName?: string;
  source: string;
  cardHolder?: string;
  installmentInfo?: string;
  shared: boolean;
  totalAmount?: number;
  userShare?: number;
}

export interface Page<T> {
  content: T[];
  totalElements: number;
  totalPages: number;
  number: number;
  size: number;
}
