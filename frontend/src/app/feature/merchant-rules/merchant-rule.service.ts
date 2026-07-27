import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CreateMerchantRuleRequest, MerchantRule } from '../../core/models/merchant-rule.model';

@Injectable({ providedIn: 'root' })
export class MerchantRuleService {
  constructor(private http: HttpClient) {}

  getAll(): Observable<MerchantRule[]> {
    return this.http.get<MerchantRule[]>('/api/merchant-rules');
  }

  create(req: CreateMerchantRuleRequest): Observable<MerchantRule> {
    return this.http.post<MerchantRule>('/api/merchant-rules', req);
  }

  update(id: string, req: CreateMerchantRuleRequest): Observable<MerchantRule> {
    return this.http.put<MerchantRule>(`/api/merchant-rules/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/merchant-rules/${id}`);
  }
}
