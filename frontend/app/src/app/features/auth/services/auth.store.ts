import { Injectable, computed, signal } from '@angular/core';

import { CurrentUser } from '../domain/models/user.model';

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

  /**
   * Role of the current session, or null when there is no session.
   *
   * The role comes from the JWT claims, the same source `roleGuard` reads.
   * Nothing is looked up in storage: the access token never leaves memory,
   * so neither can the role.
   */
  readonly role = computed(() => this._user()?.role ?? null);

  /**
   * True when the current session belongs to a MASTER.
   *
   * Single source of truth for the MASTER-only controls of the app: the
   * pages read this signal instead of guessing the role from a storage key.
   */
  readonly isMaster = computed(() => this.role() === 'MASTER');

  /**
   * T-FE-09: señal global que indica que el veedor debe desbloquear
   * con PIN local (offline + token expirado). El layout raíz observa
   * esta señal para mostrar {@link PinUnlockModalComponent}.
   */
  private readonly _requiresPinUnlock = signal<boolean>(false);
  readonly requiresPinUnlock = this._requiresPinUnlock.asReadonly();

  setRequiresPinUnlock(value: boolean): void {
    this._requiresPinUnlock.set(value);
  }

  setAcceptedTermsVersion(version: string): void {
    const currentUser = this._user();
    if (currentUser) {
      this._user.set({ ...currentUser, acceptedTermsVersion: version });
    }
  }

  setAcceptedPrivacyVersion(version: string): void {
    const currentUser = this._user();
    if (currentUser) {
      this._user.set({ ...currentUser, acceptedPrivacyVersion: version });
    }
  }

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
