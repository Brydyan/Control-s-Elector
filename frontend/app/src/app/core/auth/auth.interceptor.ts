import {
  HttpErrorResponse,
  HttpEvent,
  HttpHandlerFn,
  HttpInterceptorFn,
  HttpRequest,
} from '@angular/common/http';
import { inject } from '@angular/core';
import { Observable, catchError, switchMap, throwError } from 'rxjs';

import { AuthRepository } from './data/auth.repository';
import { AuthStore } from './auth.store';

/**
 * Attaches the in-memory access token as `Authorization: Bearer` and, on
 * a 401, transparently tries a single /auth/refresh (the refresh token
 * lives in the httpOnly cookie) and replays the original request.
 *
 * The /auth/login and /auth/refresh calls are skipped for the retry so
 * we never recurse.
 */
export const authInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const repo = inject(AuthRepository);

  const isAuthEndpoint = req.url.includes('/auth/login') || req.url.includes('/auth/refresh');
  const token = store.accessToken();

  const authorized = token
    ? req.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : req;

  return next(authorized).pipe(
    catchError((err: HttpErrorResponse) => {
      if (err.status !== 401 || isAuthEndpoint) {
        return throwError(() => err);
      }
      return refreshAndRetry(req, next, repo, store, err);
    }),
  );
};

/** Single attempt: refresh, then replay the original request with the new token. */
function refreshAndRetry(
  req: HttpRequest<unknown>,
  next: HttpHandlerFn,
  repo: AuthRepository,
  store: AuthStore,
  originalError: HttpErrorResponse,
): Observable<HttpEvent<unknown>> {
  return repo.refresh().pipe(
    switchMap((session) => {
      store.setAccessToken(session.accessToken);
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
