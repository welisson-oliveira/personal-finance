import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { DashboardResponse } from '../../core/models/dashboard.model';

@Injectable({ providedIn: 'root' })
export class DashboardService {
  constructor(private http: HttpClient) {}

  getMonthly(year: number, month: number): Observable<DashboardResponse> {
    return this.http.get<DashboardResponse>(`/api/dashboard/monthly?year=${year}&month=${month}`);
  }
}
