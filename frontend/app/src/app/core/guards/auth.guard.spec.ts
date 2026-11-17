import { TestBed } from '@angular/core/testing';
import {
  ActivatedRouteSnapshot,
  Router,
  RouterStateSnapshot,
  UrlTree,
} from '@angular/router';
import { provideRouter } from '@angular/router';
import { firstValueFrom, isObservable, of } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { authGuard, guestGuard, roleGuard } from './auth.guard';
import { AuthService } from '../../features/auth/services/auth.service';
import { AuthStore } from '../../features/auth/services/auth.store';
import { CurrentUser } from '../../features/auth/domain/models/user.model';
import { PinAuthService } from '../../features/auth/services/pin-auth.service';

/**
 * Tests unitarios para los guards CanActivateFn (T-FE-02 CRITICAL 1).
 *
 * <p>Cubre:
 * <ul>
 *   <li>S1: authGuard sin token en store → UrlTree('/login')</li>
 *   <li>S2: authGuard + roleGuard con token OBSERVER válido → ambos retornan true</li>
 *   <li>S3: roleGuard con token ADMIN en ruta protegida con roles=['OBSERVER','COORDINATOR']
 *       → UrlTree('/dashboard') (rechazado)</li>
 * </ul>
 *
 * <p>Lazy-load verificado por separado en {@link app.routes.spec.ts}.
 *
 * <p>Los guards pueden retornar Observable o valor directo. {@link extractValue} unifica ambos.
 */
describe('guards de routing (T-FE-02)', () => {
  const mockObserver: CurrentUser = {
    id: 1,
    publicId: 'obs-uuid',
    nombre: 'Obs',
    apellido: 'Test',
    email: 'obs@test.local',
    role: 'OBSERVER',
    permissions: ['acta:upload'],
  };

  const mockAdmin: CurrentUser = {
    id: 2,
    publicId: 'admin-uuid',
    nombre: 'Admin',
    apellido: 'Test',
    email: 'admin@test.local',
    role: 'ADMIN',
    permissions: ['acta:read'],
  };

  let store: AuthStore;
  let auth: { restoreSession: ReturnType<typeof vi.fn> };
  let router: {
    parseUrl: ReturnType<typeof vi.fn>;
    createUrlTree: ReturnType<typeof vi.fn>;
  };
  let pinAuth: { tienePinConfigurado: ReturnType<typeof vi.fn> };

  beforeEach(() => {
    auth = {
      restoreSession: vi.fn(),
    };
    router = {
      parseUrl: vi.fn(
        (url: string) => ({ toString: () => url }) as unknown as UrlTree,
      ),
      createUrlTree: vi.fn((cmds: unknown | unknown[]) => {
        const path = Array.isArray(cmds)
          ? cmds.join('/')
          : String(cmds).replace(/^\//, '');
        return { toString: () => path } as unknown as UrlTree;
      }),
    };
    pinAuth = {
      tienePinConfigurado: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        AuthStore,
        { provide: AuthService, useValue: auth },
        { provide: Router, useValue: router },
        { provide: PinAuthService, useValue: pinAuth },
        provideRouter([]),
      ],
    });

    store = TestBed.inject(AuthStore);
    store.clear();
  });

  // -------------------------------------------------------------------------
  // S1: authGuard sin token → /login
  // -------------------------------------------------------------------------

  it('S1: authGuard sin token retorna UrlTree /login', async () => {
    auth.restoreSession.mockReturnValue(of(false));

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any),
    );

    const resolved = await extractValue(result);
    expect(resolved).toBeDefined();
    expect(auth.restoreSession).toHaveBeenCalledOnce();
    expect((resolved as UrlTree).toString()).toBe('/login');
  });

  // -------------------------------------------------------------------------
  // S2: authGuard + roleGuard con token válido
  // -------------------------------------------------------------------------

  it('S2: authGuard con token válido retorna true', async () => {
    store.setSession('token', mockObserver);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any),
    );

    const resolved = await extractValue(result);
    expect(resolved).toBe(true);
    expect(auth.restoreSession).not.toHaveBeenCalled();
  });

  it('S2: roleGuard con OBSERVER en ruta OBSERVER retorna true', async () => {
    store.setSession('token', mockObserver);

    const fakeRoute = {
      data: { roles: ['OBSERVER', 'MASTER'] },
    } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(fakeRoute, {} as RouterStateSnapshot),
    );

    const resolved = await extractValue(result);
    expect(resolved).toBe(true);
  });

  // -------------------------------------------------------------------------
  // S3: roleGuard con ADMIN en ruta protegida con roles=[OBSERVER,COORDINATOR]
  // -------------------------------------------------------------------------

  it('S3: roleGuard con ADMIN en ruta solo OBSERVER/COORDINATOR redirige a /dashboard', async () => {
    store.setSession('token', mockAdmin);

    const fakeRoute = {
      data: { roles: ['OBSERVER', 'COORDINATOR'] },
    } as unknown as ActivatedRouteSnapshot;
    const result = TestBed.runInInjectionContext(() =>
      roleGuard(fakeRoute, {} as RouterStateSnapshot),
    );

    const resolved = await extractValue(result);
    expect((resolved as UrlTree).toString()).toBe('/dashboard');
  });

  // -------------------------------------------------------------------------
  // guestGuard
  // -------------------------------------------------------------------------

  it('guestGuard: usuario autenticado es redirigido a /dashboard', async () => {
    store.setSession('token', mockObserver);
    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as any, {} as any),
    );
    const resolved = await extractValue(result);
    expect((resolved as UrlTree).toString()).toBe('/dashboard');
  });

  it('guestGuard: usuario anónimo pasa', async () => {
    store.clear();
    const result = TestBed.runInInjectionContext(() =>
      guestGuard({} as any, {} as any),
    );
    const resolved = await extractValue(result);
    expect(resolved).toBe(true);
  });

  // -------------------------------------------------------------------------
  // S1 (T-FE-09): authGuard offline + PIN configurado → requiresPinUnlock
  // -------------------------------------------------------------------------

  it('S1 (T-FE-09): authGuard sin token + offline + PIN configurado seta requiresPinUnlock=true', async () => {
    // Simular token expirado (restore falla) + offline + PIN configurado
    auth.restoreSession.mockReturnValue(of(false)); // token no restituido
    pinAuth.tienePinConfigurado.mockReturnValue(true);
    vi.spyOn(navigator, 'onLine', 'get').mockReturnValue(false);

    const result = TestBed.runInInjectionContext(() =>
      authGuard({} as any, {} as any),
    );

    const resolved = await extractValue(result);
    expect(resolved).toBe(false); // Bloquea navegación
    expect(store.requiresPinUnlock()).toBe(true); // Modal se muestra
  });
});

// -------------------------------------------------------------------------
// Helper: los guards pueden retornar valor directo u Observable<boolean | UrlTree>.
// `extractValue` unifica ambos casos.
// -------------------------------------------------------------------------
async function extractValue(result: unknown): Promise<unknown> {
  if (isObservable(result)) {
    return firstValueFrom(result as any);
  }
  return result;
}
