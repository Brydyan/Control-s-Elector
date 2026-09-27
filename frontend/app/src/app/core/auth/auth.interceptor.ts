import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './services/auth.service';
import { AuthStore } from './auth.store';

/**
 * Attaches the in-memory access token as `Authorization: Bearer` ONLY for
 * requests destined to our backend API (environment.apiUrl), preventing
 * token leakage to third-party endpoints (S3, CDN, external APIs).
 *
 * - On 401 Unauthorized (unauthenticated/expired session):
 *   Tries transparent single-flight refresh via AuthService.refresh().
 *   If refresh fails or if it occurred on an auth endpoint, clears the session and navigates to /login.
 * - On 403 Forbidden (authenticated, but lack permissions):
 *   Preserves the session and navigates to /dashboard.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);
  const router = inject(Router);

  const isApiRequest =
    req.url.startsWith(environment.apiUrl) ||
    req.url.startsWith('/api/') ||
    (environment.apiUrl.startsWith('http') &&
      req.url.startsWith(new URL(environment.apiUrl).origin));

  const isAuthEndpoint =
    req.url.includes('/auth/login') || req.url.includes('/auth/refresh');

  const token = store.accessToken();

  const authorized =
    isApiRequest && token
      ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
      : req;

  return next(authorized).pipe(
    catchError((err: HttpErrorResponse) => {
      if (!isApiRequest) {
        return throwError(() => err);
      }

      // 403 Forbidden: user is authenticated, but not authorized for this resource.
      if (err.status === 403) {
        router.navigateByUrl('/dashboard');
        return throwError(() => err);
      }

      // 401 on login or refresh endpoint: clear session and redirect to /login
      if (err.status === 401 && isAuthEndpoint) {
        store.clear();
        if (req.url.includes('/auth/refresh')) {
          router.navigateByUrl('/login');
        }
        return throwError(() => err);
      }

      // 401 on regular API endpoints: try single-flight refresh then retry, redirect on failure
      if (err.status === 401) {
        return refreshAndRetry(req, next, auth, store, router, err);
      }

      return throwError(() => err);
    }),
  );
};

/** Single-flight refresh attempt: delegating to AuthService, then replaying original request. */
function refreshAndRetry(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
  store: AuthStore,
  router: Router,
  originalError: HttpErrorResponse,
): Observable<HttpEvent<unknown>> {
  return auth.refresh().pipe(
    switchMap((session) => {
      const retried = req.clone({
        setHeaders: { Authorization: `Bearer ${session.accessToken}` },
      });
      return next(retried);
    }),
    catchError(() => {
      store.clear();
      router.navigateByUrl('/login');
      return throwError(() => originalError);
    }),
  );
}
