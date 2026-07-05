import { Injectable, computed, signal } from '@angular/core';

import { CurrentUser } from './domain/user.model';

/**
 * In-memory auth state.
 *
 * The access token lives ONLY here, in a signal. It is never written to
 * localStorage/sessionStorage — that would expose it to any XSS. On a
 * page reload the token is gone and the app must call /auth/refresh
 * (the refresh token survives in the httpOnly cookie) to rebuild state.
 */
@Injectable({ providedIn: 'root' })
export class AuthStore {
  private readonly _accessToken = signal<string | null>(null);
  private readonly _user = signal<CurrentUser | null>(null);

  readonly user = this._user.asReadonly();
  readonly accessToken = this._accessToken.asReadonly();
  readonly isAuthenticated = computed(() => this._accessToken() !== null);

  setSession(accessToken: string, user: CurrentUser): void {
    this._accessToken.set(accessToken);
    this._user.set(user);
  }

  setAccessToken(accessToken: string): void {
    this._accessToken.set(accessToken);
  }

  clear(): void {
    this._accessToken.set(null);
    this._user.set(null);
  }

  hasPermission(permission: string): boolean {
    return this._user()?.permissions.includes(permission) ?? false;
  }
}
