export interface ReviewQueueItem {
  id: string;
  rawDescription: string;
  normalizedDescription?: string;
  amount: number;
  transactionDate: string;
  suggestedCategoryId?: string;
  suggestedCategoryName?: string;
  status: string;
}

export interface ResolveReviewRequest {
  categoryId?: string;
  budgetGroup: string;
  merchantName: string;
}
