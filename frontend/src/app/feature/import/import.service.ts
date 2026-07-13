import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {
  ImportPreviewResponse,
  ImportSessionResponse,
  ParsedTransaction,
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

  confirm(sessionId: string, transactions: ParsedTransaction[]): Observable<void> {
    return this.http.post<void>(`/api/import/${sessionId}/confirm`, transactions);
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
}
