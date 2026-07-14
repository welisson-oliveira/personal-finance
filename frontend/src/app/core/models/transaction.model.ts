export interface Transaction {
  id: string;
  description: string;
  normalizedDescription?: string;
  amount: number;
  type: string;
  budgetGroup?: string;
  investmentDirection?: string;
  ignored: boolean;
  needsReview: boolean;
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

export interface UpdateTransactionRequest {
  description: string;
  amount: number;
  type: string;
  date: string;
  categoryId?: string;
  budgetGroup?: string;
  investmentDirection?: string;
  ignored?: boolean;
  notes?: string;
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
