export interface ParsedTransaction {
  date: string;
  description: string;
  amount: number;
  type: string;
  cardHolder?: string;
  installmentInfo?: string;
  normalizedDescription?: string;
  budgetGroup?: string;
  investmentDirection?: string;
  ignored?: boolean;
  categoryId?: string;
  categoryName?: string;
  notes?: string;
  knownPersonId?: string;
  needsReview: boolean;
  included: boolean;
  autoClassification?: string;
}

export interface ImportPreviewResponse {
  sessionId: string;
  documentType: string;
  periodStart: string;
  periodEnd: string;
  transactions: ParsedTransaction[];
  reviewQueueCount: number;
}

export interface ImportSessionResponse {
  id: string;
  documentType: string;
  fileName: string;
  periodStart: string;
  periodEnd: string;
  status: string;
  createdAt: string;
  transactionCount: number;
}
