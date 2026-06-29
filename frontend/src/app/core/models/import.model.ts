export interface ParsedTransaction {
  date: string;
  description: string;
  amount: number;
  type: string;
  cardHolder?: string;
  installmentInfo?: string;
  normalizedDescription?: string;
  incomeType?: string;
  budgetGroup?: string;
  categoryId?: string;
  categoryName?: string;
  notes?: string;
  knownPersonId?: string;
  needsReview: boolean;
}

export interface ImportPreviewResponse {
  sessionId: string;
  documentType: string;
  periodStart: string;
  periodEnd: string;
  transactions: ParsedTransaction[];
  reviewQueueCount: number;
}
