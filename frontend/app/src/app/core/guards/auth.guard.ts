import { inject } from '@angular/core';
import { CanActivateFn, Router, UrlTree } from '@angular/router';
import { Observable, map, of, switchMap } from 'rxjs';

import { PinAuthService } from '../../features/auth/services/pin-auth.service';
import { AuthService } from '../../features/auth/services/auth.service';
import { AuthStore } from '../../features/auth/services/auth.store';
import { TermsAcceptanceService } from '../../features/legal/services/terms-acceptance.service';

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
  const terms = inject(TermsAcceptanceService);
  const router = inject(Router);

  return evaluarSesion().pipe(
    switchMap((sesion) => {
      // `false` = bloqueado por el PIN local; UrlTree = no hay sesion.
      if (sesion !== true) {
        return of(sesion);
      }

      // T-LEGAL-01.8: encima de la sesion esta el visto bueno de los terminos.
      // Quien no ha aceptado la version vigente no entra a las rutas protegidas.
      return terms
        .requiereAceptacion()
        .pipe(
          map((pendiente) =>
            pendiente ? router.createUrlTree(['/terminos']) : true,
          ),
        );
    }),
  );
};

/**
 * Sesion valida, sin mirar los terminos.
 *
 * <p>Existe aparte de {@link authGuard} por un motivo concreto: la pantalla de aceptacion
 * (`/terminos`) necesita una sesion —sin sesion no hay a quien registrar la aceptacion— pero no
 * puede pasar por el guard que exige la aceptacion, o la pagina se redirigiria a si misma en
 * bucle.
 */
export const sessionGuard: CanActivateFn = () => evaluarSesion();

/**
 * Sesion valida o camino de vuelta al login.
 *
 * <p>Es el nucleo de los tres guards: decide si hay sesion y, si no, a donde va el usuario. Vive
 * aparte para poder reutilizarla sin el chequeo de terminos (que es lo que separa
 * {@link authGuard} de {@link sessionGuard}).
 */
function evaluarSesion(): Observable<boolean | UrlTree> {
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
}

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
