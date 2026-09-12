import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';
import { AuthStore } from './auth.store';

/**
 * Attaches the in-memory access token as `Authorization: Bearer` ONLY for
 * requests destined to our backend API (environment.apiUrl), preventing
 * token leakage to third-party endpoints (S3, CDN, external APIs).
 *
 * On a 401, transparently delegates to AuthService.refresh() (which provides
 * single-flight deduplication across concurrent 401s) and replays the request.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);

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
      if (err.status !== 401 || isAuthEndpoint || !isApiRequest) {
        return throwError(() => err);
      }
      return refreshAndRetry(req, next, auth, store, err);
    }),
  );
};

/** Single-flight refresh attempt: delegating to AuthService, then replaying original request. */
function refreshAndRetry(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  auth: AuthService,
  store: AuthStore,
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
      return throwError(() => originalError);
    }),
  );
}
