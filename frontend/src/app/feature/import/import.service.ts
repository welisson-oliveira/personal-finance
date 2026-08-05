import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ImportPreviewResponse,
  ImportSessionResponse,
  ParsedTransaction,
  PendingReconciliation,
} from '../../core/models/import.model';

@Injectable({ providedIn: 'root' })
export class ImportService {
  constructor(private http: HttpClient) {}

  parse(file: File): Observable<ImportPreviewResponse> {
    const form = new FormData();
    form.append('file', file);
    return this.http.post<ImportPreviewResponse>('/api/import/parse', form);
  }

  getPreview(sessionId: string): Observable<ImportPreviewResponse> {
    return this.http.get<ImportPreviewResponse>(`/api/import/${sessionId}/preview`);
  }

  /** Persists in-progress edits onto the pending session so they survive leaving the preview. */
  savePreview(sessionId: string, transactions: ParsedTransaction[]): Observable<void> {
    return this.http.put<void>(`/api/import/${sessionId}/preview`, transactions);
  }

  confirm(
    sessionId: string,
    transactions: ParsedTransaction[],
    reconcileExtratoPaymentIds?: string[]
  ): Observable<void> {
    return this.http.post<void>(`/api/import/${sessionId}/confirm`, {
      transactions,
      reconcileExtratoPaymentIds,
    });
  }

  cancel(sessionId: string): Observable<void> {
    return this.http.post<void>(`/api/import/${sessionId}/cancel`, {});
  }

  getHistory(): Observable<ImportSessionResponse[]> {
    return this.http.get<ImportSessionResponse[]>('/api/import/history');
  }

  deleteSession(sessionId: string): Observable<void> {
    return this.http.delete<void>(`/api/import/${sessionId}`);
  }

  /** Lists still-unreconciled extrato bill payments with fatura candidates (dedicated screen). */
  getReconciliation(): Observable<PendingReconciliation[]> {
    return this.http.get<PendingReconciliation[]>('/api/import/reconciliation');
  }

  /** Manually reconciles (substitutes) an extrato bill payment against a fatura. */
  reconcile(extratoPaymentId: string, faturaSessionId: string): Observable<void> {
    return this.http.post<void>('/api/import/reconcile', { extratoPaymentId, faturaSessionId });
  }
}
