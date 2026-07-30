import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Anomaly, AnomalyFeedbackRequest } from '../../core/models/anomaly.model';

@Injectable({ providedIn: 'root' })
export class AnomalyService {
  constructor(private http: HttpClient) {}

  getAll(includeResolved = false): Observable<Anomaly[]> {
    const params = new HttpParams().set('includeResolved', includeResolved);
    return this.http.get<Anomaly[]>('/api/anomalies', { params });
  }

  submitFeedback(req: AnomalyFeedbackRequest): Observable<void> {
    return this.http.post<void>('/api/anomalies/feedback', req);
  }

  reopen(transactionId: string, type: string): Observable<void> {
    const params = new HttpParams().set('transactionId', transactionId).set('type', type);
    return this.http.delete<void>('/api/anomalies/feedback', { params });
  }
}
