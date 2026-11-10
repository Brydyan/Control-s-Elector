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
import * as Sentry from '@sentry/angular';
import { MessageService } from 'primeng/api';

import { routes } from './app/app.routes';
import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/core/interceptors/auth.interceptor';
import { SyncService } from './app/core/offline/sync.service';
import { CookieConsentService } from './app/core/services/cookie-consent.service';
import { primengTheme } from './theme/primeng-theme';
import { environment } from './environments/environment';

/**
 * The app speaks Spanish: the dates, the numbers and the month names. The
 * locale data has to be registered before the first bootstrap, and
 * `LOCALE_ID` tells Angular which one to use.
 */
registerLocaleData(localeEs);

/**
 * Initialize Sentry for error tracking and crash reporting.
 *
 * - Production: 10% sampling to reduce costs and noise
 * - Dev/staging: 100% sampling for complete visibility
 * - PII scrubbing: auto-redact emails, passwords, authorization headers
 *
 * NOTE: Sentry is ALWAYS initialized (even without analytics consent) but
 * only error tracking is enabled. Custom events are blocked without consent.
 */
if (environment.sentryDsn) {
  const consentService = new CookieConsentService();
  const hasAnalyticsConsent = consentService.hasAnalyticsConsent();

  Sentry.init({
    dsn: environment.sentryDsn,
    environment: environment.production ? 'prod' : 'dev',
    sampleRate: environment.production ? 0.1 : 1.0,
    denyUrls: [/localhost/],
    beforeSend: (event) => {
      // PII redaction: remove sensitive headers and data
      if (event.request?.headers) {
        delete event.request.headers['authorization'];
      }
      return event;
    },
  });

  // Only capture custom events if user has analytics consent
  if (!hasAnalyticsConsent) {
    Sentry.setTag('analytics_consent', 'denied');
  }
}

bootstrapApplication(AppComponent, {
  providers: [
    { provide: LOCALE_ID, useValue: 'es' },
    provideHttpClient(withInterceptors([authInterceptor])),
    provideRouter(routes, withPreloading(PreloadAllModules)),
    providePrimeNG({ theme: primengTheme, ripple: true }),
    // El canal del toast (ODD 505, A): el `MessageService` de PrimeNG lleva los
    // mensajes desde el `ToastService` hasta el `<p-toast>` del root. Sin esta
    // instancia compartida, el toast se monta pero no muestra nada.
    MessageService,
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
