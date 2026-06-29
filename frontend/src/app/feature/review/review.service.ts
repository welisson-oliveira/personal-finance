import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { ReviewQueueItem, ResolveReviewRequest } from '../../core/models/review.model';

@Injectable({ providedIn: 'root' })
export class ReviewService {
  constructor(private http: HttpClient) {}

  getPending(): Observable<ReviewQueueItem[]> {
    return this.http.get<ReviewQueueItem[]>('/api/review/pending');
  }

  resolve(id: string, req: ResolveReviewRequest): Observable<void> {
    return this.http.post<void>(`/api/review/${id}/resolve`, req);
  }
}
