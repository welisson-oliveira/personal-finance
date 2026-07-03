import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Page, Transaction } from '../../core/models/transaction.model';

@Injectable({ providedIn: 'root' })
export class TransactionService {
  constructor(private http: HttpClient) {}

  findAll(filters: {
    month?: string;
    type?: string;
    page?: number;
    size?: number;
  }): Observable<Page<Transaction>> {
    let params = new HttpParams();
    if (filters.month) params = params.set('month', filters.month);
    if (filters.type) params = params.set('type', filters.type);
    if (filters.page != null) params = params.set('page', filters.page);
    if (filters.size != null) params = params.set('size', filters.size);
    return this.http.get<Page<Transaction>>('/api/transactions', { params });
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/transactions/${id}`);
  }
}
