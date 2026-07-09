import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { CategoryTotal, MonthlyPoint } from '../../core/models/report.model';

@Injectable({ providedIn: 'root' })
export class ReportService {
  constructor(private http: HttpClient) {}

  monthlyEvolution(months: number): Observable<MonthlyPoint[]> {
    const params = new HttpParams().set('months', months);
    return this.http.get<MonthlyPoint[]>('/api/reports/monthly-evolution', { params });
  }

  categoryBreakdown(year: number, month: number): Observable<CategoryTotal[]> {
    const params = new HttpParams().set('year', year).set('month', month);
    return this.http.get<CategoryTotal[]>('/api/reports/category-breakdown', { params });
  }
}
