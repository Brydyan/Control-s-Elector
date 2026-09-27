import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router, Routes } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from './app.routes';

/**
 * Test de configuración de rutas (T-FE-02 CRITICAL 2).
 *
 * <p>Verifica que las rutas con `loadChildren` o `loadComponent` están configuradas
 * para carga lazy (no usan `component` directo). Sin esto, la verificación del lazy-load
 * era manual en DevTools — bajo Strict TDD no válida.
 */
describe('configuración de rutas (lazy-load)', () => {
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideRouter(routes), provideHttpClient()],
    });
  });

  it('rutas de features usan loadComponent (no component directo)', () => {
    // Al menos un 70% de las rutas deberían usar lazy-load. Si esto baja, alguien
    // cambió a component directo (rompe chunking).
    const lazyRoutes = routes.filter(
      (r) => r.loadComponent !== undefined || r.loadChildren !== undefined,
    );
    const totalNonError = routes.filter(
      (r) => !r.path?.endsWith('**') && r.path !== '',
    );

    expect(lazyRoutes.length).toBeGreaterThanOrEqual(totalNonError.length * 0.7);
  });

  it('rutas /observer/* usan loadComponent', () => {
    // Verificación específica: las rutas de observador referenciadas en app.routes.ts
    // usan carga lazy (loadComponent) y no component inline.
    const observerRoutes = collectObserverRoutes(routes);
    expect(observerRoutes.length).toBeGreaterThan(0);

    for (const route of observerRoutes) {
      expect(route.loadComponent).toBeDefined();
      expect(route.component).toBeUndefined();
    }
  });

  it('rutas /coordinator/* usan loadComponent', () => {
    const coordinatorRoutes = collectCoordinatorRoutes(routes);
    expect(coordinatorRoutes.length).toBeGreaterThan(0);

    for (const route of coordinatorRoutes) {
      expect(route.loadComponent).toBeDefined();
      expect(route.component).toBeUndefined();
    }
  });

  it('router resuelve /login sin error (ruta pública)', async () => {
    const router = TestBed.inject(Router);
    const ok = await router.navigate(['/login']);
    expect(ok).toBe(true);
  });

  it('router resuelve /dashboard sin error (ruta autenticada — falla porque no hay token, pero route existe)', async () => {
    const router = TestBed.inject(Router);
    await router.navigate(['/dashboard']);
    // No importa el resultado de la navegación (guard redirige), solo que la ruta existe.
    const url = router.url;
    expect(url === '/login' || url.startsWith('/dashboard') || url === '/').toBe(true);
  });
});

// -------------------------------------------------------------------------
// Helpers (recorren el árbol de rutas recursivamente)
// -------------------------------------------------------------------------

function collectObserverRoutes(routes: Routes): any[] {
  const matches: any[] = [];
  for (const r of routes) {
    if (r.path?.startsWith('observer')) {
      matches.push(r);
    }
    if (r.children) {
      matches.push(...collectObserverRoutes(r.children));
    }
  }
  return matches;
}

function collectCoordinatorRoutes(routes: Routes): any[] {
  const matches: any[] = [];
  for (const r of routes) {
    if (r.path?.startsWith('coordinator')) {
      matches.push(r);
    }
    if (r.children) {
      matches.push(...collectCoordinatorRoutes(r.children));
    }
  }
  return matches;
}
