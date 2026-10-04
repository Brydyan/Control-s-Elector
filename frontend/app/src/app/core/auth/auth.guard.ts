import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map, of } from 'rxjs';

import { PinAuthService } from './pin-auth.service';
import { AuthService } from './services/auth.service';
import { AuthStore } from './auth.store';

/**
 * Protects authenticated routes. If there is already an in-memory
 * session it passes immediately. Otherwise (e.g. after a page reload)
 * it tries to rebuild the session from the httpOnly refresh cookie
 * before deciding, redirecting to /login on failure.
 *
 * T-FE-09 (A.3, design D1): si el refresh falla y estamos offline
 * (`!navigator.onLine`) con un PIN local configurado, activa el flag
 * `requiresPinUnlock` para que el layout muestre el modal de PIN en
 * lugar de redirigir a `/login` (que también requiere red).
 */
export const authGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);
  const router = inject(Router);
  const pinAuth = inject(PinAuthService);

  if (store.isAuthenticated()) {
    return of(true);
  }

  return auth.restoreSession().pipe(
    map((restored) => {
      if (restored) return true;

      // T-FE-09 A.3: si estamos offline y hay PIN configurado,
      // pedir desbloqueo local en lugar de redirigir.
      const offline =
        typeof navigator !== 'undefined' && navigator.onLine === false;
      if (offline && pinAuth.tienePinConfigurado()) {
        store.setRequiresPinUnlock(true);
        return false; // bloquea la ruta; el modal se muestra por separado
      }
      return router.createUrlTree(['/login']);
    }),
  );
};

/** Keeps authenticated users away from /login (bounce to /home). */
export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const router = inject(Router);
  return store.isAuthenticated() ? router.createUrlTree(['/dashboard']) : true;
};

/**
 * Protects routes by requiring one of the specified roles.
 * Example in route data: { roles: ['OBSERVER', 'ADMIN'] }
 */
export const roleGuard: CanActivateFn = (route) => {
  const store = inject(AuthStore);
  const auth = inject(AuthService);
  const router = inject(Router);

  const allowedRoles = (route.data?.['roles'] as string[]) || [];

  const checkRole = () => {
    const userRole = store.user()?.role;
    if (
      userRole &&
      (allowedRoles.length === 0 || allowedRoles.includes(userRole))
    ) {
      return true;
    }
    return router.createUrlTree(['/dashboard']);
  };

  if (store.isAuthenticated()) {
    return checkRole();
  }

  return auth
    .restoreSession()
    .pipe(
      map((restored) =>
        restored ? checkRole() : router.createUrlTree(['/login']),
      ),
    );
};
