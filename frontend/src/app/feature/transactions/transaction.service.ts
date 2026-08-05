import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, Transaction, UpdateTransactionRequest } from '../../core/models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  constructor(private http: HttpClient) {}

  findAll(filters: {
    month?: string;
    type?: string;
    categoryId?: string;
    needsReview?: boolean;
    search?: string;
    budgetGroup?: string;
    includeIgnored?: boolean;
    sort?: string;
    page?: number;
    size?: number;
  }): Observable<Page<Transaction>> {
    let params = new HttpParams();
    if (filters.month) params = params.set('month', filters.month);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.categoryId) params = params.set('categoryId', filters.categoryId);
    if (filters.needsReview) params = params.set('needsReview', true);
    if (filters.search) params = params.set('search', filters.search);
    if (filters.budgetGroup) params = params.set('budgetGroup', filters.budgetGroup);
    if (filters.includeIgnored) params = params.set('includeIgnored', true);
    if (filters.sort) params = params.set('sort', filters.sort);
    if (filters.page != null) params = params.set('page', filters.page);
    if (filters.size != null) params = params.set('size', filters.size);
    return this.http.get<Page<Transaction>>('/api/transactions', { params });
  }

  update(id: string, request: UpdateTransactionRequest): Observable<Transaction> {
    return this.http.put<Transaction>(`/api/transactions/${id}`, request);
  }

  updateNotes(id: string, notes: string): Observable<Transaction> {
    return this.http.patch<Transaction>(`/api/transactions/${id}/notes`, { notes });
  }

  /** Explicitly marks a transaction as reviewed (clears the pending-review flag). */
  confirmReview(id: string): Observable<Transaction> {
    return this.http.patch<Transaction>(`/api/transactions/${id}/review`, {});
  }

  /**
   * Bulk edit of the selected rows. Only the provided fields are applied (each is an independent
   * toolbar action); the backend scopes budgetGroup to expenses and category to expense/income.
   */
  bulkUpdate(payload: {
    ids: string[];
    budgetGroup?: string;
    categoryId?: string;
    competenceMonth?: string;
    ignored?: boolean;
  }): Observable<Transaction[]> {
    return this.http.patch<Transaction[]>('/api/transactions/bulk', payload);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/transactions/${id}`);
  }
}
