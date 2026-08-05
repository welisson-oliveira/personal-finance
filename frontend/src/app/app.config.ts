import { ApplicationConfig, APP_INITIALIZER } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { provideAnimationsAsync } from '@angular/platform-browser/animations/async';
import { routes } from './app.routes';
import { authInterceptor } from './core/auth/auth.interceptor';
import { apiUrlInterceptor } from './core/config/api-url.interceptor';
import { ConfigService } from './core/config/config.service';

export const appConfig: ApplicationConfig = {
  providers: [
    provideRouter(routes),
    provideAnimationsAsync(),
    provideHttpClient(withInterceptors([apiUrlInterceptor, authInterceptor])),
    {
      provide: APP_INITIALIZER,
      multi: true,
      useFactory: (config: ConfigService) => () => config.load(),
      deps: [ConfigService],
    },
  ],
};
