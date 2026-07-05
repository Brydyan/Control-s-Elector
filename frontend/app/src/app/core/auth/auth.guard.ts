import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';

import { AuthService } from './auth.service';
import { AuthStore } from './auth.store';

/**
 * Protects authenticated routes. If there is already an in-memory
 * session it passes immediately. Otherwise (e.g. after a page reload)
 * it tries to rebuild the session from the httpOnly refresh cookie
 * before deciding, redirecting to /login on failure.
 */
export const authGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);
  const router = inject(Router);

  if (store.isAuthenticated()) {
    return of(true);
  }

  return auth.restoreSession().pipe(
    map((restored) => (restored ? true : router.createUrlTree(['/login']))),
  );
};

/** Keeps authenticated users away from /login (bounce to /home). */
export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const router = inject(Router);
  return store.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};
