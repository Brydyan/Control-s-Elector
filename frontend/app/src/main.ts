import { bootstrapApplication } from '@angular/platform-browser';
import {
  provideRouter,
  withPreloading,
  PreloadAllModules,
} from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { inject, APP_INITIALIZER, LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { provideServiceWorker } from '@angular/service-worker';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app/app.routes';
import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/core/interceptors/auth.interceptor';
import { SyncService } from './app/core/offline/sync.service';
import { primengTheme } from './theme/primeng-theme';
import { environment } from './environments/environment';

/**
 * The app speaks Spanish: the dates, the numbers and the month names. The
 * locale data has to be registered before the first bootstrap, and
 * `LOCALE_ID` tells Angular which one to use.
 */
registerLocaleData(localeEs);

bootstrapApplication(AppComponent, {
  providers: [
    { provide: LOCALE_ID, useValue: 'es' },
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes, withPreloading(PreloadAllModules)),
    providePrimeNG({ theme: primengTheme, ripple: true }),
    // The service worker is what makes the field flow work without signal:
    // it caches the app shell and the last known reads.
    provideServiceWorker('ngsw-worker.js', {
      enabled: environment.production,
      registrationStrategy: 'registerWhenStable:30000',
    }),
    {
      provide: APP_INITIALIZER,
      useFactory: () => {
        const syncService = inject(SyncService);
        return () => syncService.start();
      },
      multi: true,
    },
  ],
});
