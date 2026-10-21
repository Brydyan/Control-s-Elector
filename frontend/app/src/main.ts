import { bootstrapApplication } from '@angular/platform-browser';
import {
  provideRouter,
  withPreloading,
  PreloadAllModules,
} from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { inject, APP_INITIALIZER } from '@angular/core';
import { provideServiceWorker } from '@angular/service-worker';
import { providePrimeNG } from 'primeng/config';

import { routes } from './app/app.routes';
import { AppComponent } from './app/app.component';
import { authInterceptor } from './app/core/auth/auth.interceptor';
import { SyncService } from './app/core/offline/sync.service';
import { primengTheme } from './theme/primeng-theme';
import { environment } from './environments/environment';

bootstrapApplication(AppComponent, {
  providers: [
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
