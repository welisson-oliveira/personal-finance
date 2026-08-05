import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { ConfigService } from './config.service';

/**
 * When a backend base URL is configured (deploy split across hosts), rewrites relative "/api/..."
 * calls to the absolute backend URL. With no base configured (dev / same-host), requests stay
 * relative and the dev proxy or reverse proxy handles them.
 */
export const apiUrlInterceptor: HttpInterceptorFn = (req, next) => {
  const base = inject(ConfigService).apiBaseUrl;
  if (base && req.url.startsWith('/api')) {
    req = req.clone({ url: base.replace(/\/$/, '') + req.url });
  }
  return next(req);
};
