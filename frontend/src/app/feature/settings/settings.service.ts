import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserResponse } from '../../core/models/auth.model';

@Injectable({ providedIn: 'root' })
export class SettingsService {
  constructor(private http: HttpClient) {}

  getMe(): Observable<UserResponse> {
    return this.http.get<UserResponse>('/api/users/me');
  }

  updateProfile(monthlyNetIncome: number | null): Observable<UserResponse> {
    return this.http.put<UserResponse>('/api/users/me', { monthlyNetIncome });
  }
}
