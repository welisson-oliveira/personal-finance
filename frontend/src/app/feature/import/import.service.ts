import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ImportPreviewResponse } from '../../core/models/import.model';

@Injectable({ providedIn: 'root' })
export class ImportService {
  constructor(private http: HttpClient) {}

  parse(file: File, documentType: string): Observable<ImportPreviewResponse> {
    const form = new FormData();
    form.append('file', file);
    form.append('documentType', documentType);
    return this.http.post<ImportPreviewResponse>('/api/import/parse', form);
  }

  confirm(sessionId: string): Observable<void> {
    return this.http.post<void>(`/api/import/${sessionId}/confirm`, {});
  }

  cancel(sessionId: string): Observable<void> {
    return this.http.post<void>(`/api/import/${sessionId}/cancel`, {});
  }
}
