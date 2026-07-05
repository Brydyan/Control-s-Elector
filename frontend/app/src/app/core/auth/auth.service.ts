import { Injectable, inject } from '@angular/core';
import { Observable, catchError, map, of, tap } from 'rxjs';

import { AuthRepository, Session } from './data/auth.repository';
import { LoginRequestDto } from './data/auth.dto';
import { AuthStore } from './auth.store';

/**
 * Auth use cases. This is the single entry point the UI talks to:
 * it orchestrates the repository (HTTP) and the store (state) so pages
 * never touch HttpClient or mutate state directly.
 */
@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly repo = inject(AuthRepository);
  private readonly store = inject(AuthStore);

  login(credentials: LoginRequestDto): Observable<Session> {
    return this.repo.login(credentials).pipe(tap((s) => this.persist(s)));
  }

  refresh(): Observable<Session> {
    return this.repo.refresh().pipe(tap((s) => this.persist(s)));
  }

  logout(): Observable<void> {
    return this.repo.logout().pipe(tap(() => this.store.clear()));
  }

  /**
   * Rebuilds the in-memory session after a page reload using the
   * httpOnly refresh cookie. Emits `true` if a session was restored,
   * `false` otherwise (no valid cookie). Never throws.
   */
  restoreSession(): Observable<boolean> {
    return this.repo.refresh().pipe(
      tap((s) => this.persist(s)),
      map(() => true),
      catchError(() => {
        this.store.clear();
        return of(false);
      }),
    );
  }

  private persist(s: Session): void {
    this.store.setSession(s.accessToken, s.user);
  }
}
