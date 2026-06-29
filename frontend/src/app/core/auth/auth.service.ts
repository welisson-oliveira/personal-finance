import { Injectable, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { tap } from 'rxjs/operators';
import { Observable } from 'rxjs';
import { AuthResponse, LoginRequest, RegisterRequest, UserResponse } from '../models/auth.model';

const TOKEN_KEY = 'auth_token';
const USER_KEY = 'auth_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly currentUser = signal<UserResponse | null>(this.loadUser());

  constructor(
    private http: HttpClient,
    private router: Router
  ) {}

  login(req: LoginRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/login', req).pipe(tap((r) => this.store(r)));
  }

  register(req: RegisterRequest): Observable<AuthResponse> {
    return this.http.post<AuthResponse>('/api/auth/register', req).pipe(tap((r) => this.store(r)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.set(null);
    this.router.navigate(['/auth/login']);
  }

  getToken(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  isAuthenticated(): boolean {
    return !!this.getToken();
  }

  getUser(): UserResponse | null {
    return this.currentUser();
  }

  private store(resp: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, resp.token);
    localStorage.setItem(USER_KEY, JSON.stringify(resp.user));
    this.currentUser.set(resp.user);
  }

  private loadUser(): UserResponse | null {
    const raw = localStorage.getItem(USER_KEY);
    return raw ? (JSON.parse(raw) as UserResponse) : null;
  }
}
