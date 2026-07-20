import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';
import { AuthService } from './auth.service';

/**
 * Adds the Bearer token to outgoing requests and, when an authenticated request comes back 401
 * (expired/invalid token), logs the user out and sends them to the login screen. The login/register
 * endpoints are excluded so a wrong-password 401 doesn't trigger the redirect (the form handles it).
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const token = auth.getToken();
  if (token) {
    req = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
  }
  return next(req).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status === 401 && auth.isAuthenticated() && !req.url.includes('/api/auth/')) {
        auth.logout();
      }
      return throwError(() => err);
    })
  );
};
