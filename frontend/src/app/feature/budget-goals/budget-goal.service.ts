import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { BudgetGoal, CreateBudgetGoalRequest } from '../../core/models/budget-goal.model';

@Injectable({ providedIn: 'root' })
export class BudgetGoalService {
  constructor(private http: HttpClient) {}

  getAll(year: number, month: number): Observable<BudgetGoal[]> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http.get<BudgetGoal[]>('/api/budget-goals', { params });
  }

  create(req: CreateBudgetGoalRequest): Observable<BudgetGoal> {
    return this.http.post<BudgetGoal>('/api/budget-goals', req);
  }

  update(id: string, req: CreateBudgetGoalRequest): Observable<BudgetGoal> {
    return this.http.put<BudgetGoal>(`/api/budget-goals/${id}`, req);
  }

  delete(id: string): Observable<void> {
    return this.http.delete<void>(`/api/budget-goals/${id}`);
  }
}
