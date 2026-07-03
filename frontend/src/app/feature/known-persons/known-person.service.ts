import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateKnownPersonRequest, KnownPerson } from '../../core/models/known-person.model';

@Injectable({ providedIn: 'root' })
export class KnownPersonService {
  constructor(private http: HttpClient) {}

  getAll(): Observable<KnownPerson[]> {
    return this.http.get<KnownPerson[]>('/api/known-persons');
  }

  create(req: CreateKnownPersonRequest): Observable<KnownPerson> {
    return this.http.post<KnownPerson>('/api/known-persons', req);
  }

  update(id: string, req: CreateKnownPersonRequest): Observable<KnownPerson> {
    return this.http.put<KnownPerson>(`/api/known-persons/${id}`, req);
  }

  deactivate(id: string): Observable<void> {
    return this.http.patch<void>(`/api/known-persons/${id}/deactivate`, {});
  }
}
