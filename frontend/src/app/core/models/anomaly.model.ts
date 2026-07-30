export interface Anomaly {
  transactionId: string;
  type: string;
  status: string;
  title: string;
  message: string;
  description: string;
  categoryName?: string;
  date: string;
  amount: number;
  typicalAmount?: number;
  relatedTransactionId?: string;
  relatedDate?: string;
}

export interface AnomalyFeedbackRequest {
  transactionId: string;
  type: string;
  status: string;
}
