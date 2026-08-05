import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { firstValueFrom } from 'rxjs';

export interface AppConfig {
  /** Absolute base URL of the backend (e.g. https://my-api.koyeb.app). Empty = same origin. */
  apiBaseUrl: string;
}

/**
 * Loads runtime config from assets/config.json at startup, so the deployed backend URL can change
 * without rebuilding. Empty apiBaseUrl (the default) keeps requests same-origin — the dev proxy and
 * a same-host deploy both work unchanged.
 */
@Injectable({ providedIn: 'root' })
export class ConfigService {
  private config: AppConfig = { apiBaseUrl: '' };

  constructor(private http: HttpClient) {}

  async load(): Promise<void> {
    try {
      const cfg = await firstValueFrom(this.http.get<AppConfig>('assets/config.json'));
      if (cfg && typeof cfg.apiBaseUrl === 'string') {
        this.config = cfg;
      }
    } catch {
      // No/invalid config.json → same-origin defaults. Expected in dev.
    }
  }

  get apiBaseUrl(): string {
    return this.config.apiBaseUrl;
  }
}
