export interface ParsedTransaction {
  date: string;
  competenceDate?: string;
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
  /** EXTRATO only: set when the user reconciled this "Pagamento de fatura" to a fatura. */
  reconciled?: boolean;
}

export interface ReconciliationCandidate {
  id: string;
  label: string;
  amount: number;
  date: string;
}

export interface ReconciliationSlot {
  side: string; // 'FATURA' | 'EXTRATO' — what the current import is
  paymentIndex: number | null; // EXTRATO: index into transactions; null for FATURA
  paymentAmount: number;
  paymentDate: string;
  suggestedId: string | null;
  candidates: ReconciliationCandidate[];
}

export interface ImportPreviewResponse {
  sessionId: string;
  documentType: string;
  periodStart: string;
  periodEnd: string;
  transactions: ParsedTransaction[];
  reviewQueueCount: number;
  reconciliation: ReconciliationSlot[];
}

export interface PendingReconciliation {
  paymentId: string;
  date: string;
  amount: number;
  description: string;
  suggestedFaturaId: string | null;
  candidates: ReconciliationCandidate[];
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
