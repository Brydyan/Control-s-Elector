import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideRouter, Router, Routes } from '@angular/router';
import { beforeEach, describe, expect, it } from 'vitest';

import { routes } from './app.routes';
import { NAV_CONFIG, type UserRole } from './layout/nav.config';

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

    expect(lazyRoutes.length).toBeGreaterThanOrEqual(
      totalNonError.length * 0.7,
    );
  });

  it('rutas /observer/* usan loadComponent', () => {
    // Verificación específica: las rutas de observador referenciadas en app.routes.ts
    // usan carga lazy (loadComponent) y no component inline.
    // Se excluyen los redirects que no tienen loadComponent.
    const observerRoutes = collectObserverRoutes(routes).filter(
      (r) => !r.redirectTo,
    );
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
    expect(
      url === '/login' || url.startsWith('/dashboard') || url === '/',
    ).toBe(true);
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

/**
 * Ruta efectiva del router, con el prefijo de los hijos ya resuelto.
 *
 * El wildcard `**` se excluye a proposito: es la trampa que convierte una URL
 * fantasma en un rebote silencioso al dashboard, no una ruta real.
 */
function collectPaths(routes: Routes, prefix = ''): string[] {
  const out: string[] = [];
  for (const r of routes) {
    if (!r.path) {
      if (r.children) out.push(...collectPaths(r.children, prefix));
      continue;
    }
    if (r.path.startsWith('**')) continue;
    const full = `${prefix}/${r.path}`.replace(/\/{2,}/g, '/');
    out.push(full);
    if (r.children) out.push(...collectPaths(r.children, full));
  }
  return out;
}

/** Busca una ruta por su path (sin el prefijo de la raiz). */
function findRoute(routes: Routes, path: string): any | undefined {
  for (const r of routes) {
    if (r.path === path) return r;
    if (r.children) {
      const found = findRoute(r.children, path);
      if (found) return found;
    }
  }
  return undefined;
}

/**
 * Regresion de las "URL fantasma" (#175).
 *
 * El bug original era de esa clase: el menu apuntaba a rutas que el router no
 * tenia, y la ruta comodin `**` las mandaba al dashboard. El sintoma era
 * "click en el menu y aparece el dashboard, sin error".
 *
 * Este bloque no mira dos rutas sueltas: mira TODAS las rutas de TODOS los
 * menus y exige que cada una exista en el router. Si alguien agrega un item al
 * menu y olvida la ruta (o al reves), el test cae.
 */
describe('regresion de rutas del menu (sin URL fantasma)', () => {
  const ROLES: UserRole[] = ['OBSERVER', 'COORDINATOR', 'ADMIN', 'MASTER'];

  it('toda ruta de NAV_CONFIG existe en el router', () => {
    const paths = collectPaths(routes);

    for (const role of ROLES) {
      for (const section of NAV_CONFIG[role]) {
        for (const item of section.items) {
          expect(
            paths,
            `La ruta "${item.route}" (menu "${item.label}" de ${role}) no existe en el router: rebotaria al dashboard`,
          ).toContain(item.route);
        }
      }
    }
  });

  it('/observer/mis-actas carga el componente MisActasPage con roleGuard', () => {
    const r = findRoute(routes, 'observer/mis-actas');
    expect(r).toBeDefined();
    expect(r!.loadComponent).toBeDefined();
    expect(r!.canActivate).toBeDefined();
    expect(r!.data?.['roles']).toEqual(
      expect.arrayContaining(['OBSERVER', 'MASTER']),
    );
  });

  it('/master/configuracion existe y admite MASTER', () => {
    const r = findRoute(routes, 'master/configuracion');
    expect(r).toBeDefined();
    expect(r!.data?.['roles']).toEqual(expect.arrayContaining(['MASTER']));
  });

  it('/admin/recintos existe, es lazy y queda restringida a MASTER', () => {
    // La escritura de recintos es `configuracion:write` (solo MASTER en el
    // backend) y la pagina no tiene gating por rol: abrirla con ADMIN
    // mostraria botones que el backend rebota con 403.
    const r = findRoute(routes, 'admin/recintos');
    expect(r, 'falta la ruta admin/recintos').toBeDefined();
    expect(r!.loadComponent).toBeDefined();
    expect(r!.canActivate).toBeDefined();
    expect(r!.data?.['roles']).toEqual(['MASTER']);
  });

  it('/master/cargos, /master/partidos y /master/candidatos redirigen a /master/configuracion', () => {
    for (const path of [
      'master/cargos',
      'master/partidos',
      'master/candidatos',
    ]) {
      const r = findRoute(routes, path);
      expect(r, `falta la ruta ${path}`).toBeDefined();
      expect(r!.redirectTo).toBe('master/configuracion');
    }
  });

  it('rutas placeholder redirigen a /modules/:name', () => {
    for (const [path, moduleName] of [
      ['actas', 'actas'],
      ['evidencias', 'evidencias'],
      ['evidencias/subir', 'evidencias-subir'],
    ]) {
      const r = findRoute(routes, path);
      expect(r, `falta la ruta ${path}`).toBeDefined();
      expect(r!.redirectTo).toBe(`/modules/${moduleName}`);
    }
  });

  it('/modules/:name carga PlaceholderPage con mensaje honesto', () => {
    const moduleRoute = routes.find((r) => r.path === 'modules/:name');
    expect(moduleRoute).toBeDefined();
    expect(moduleRoute!.loadComponent).toBeDefined();
    expect(moduleRoute!.data?.['title']).toBe('Módulo no disponible');
    expect(moduleRoute!.data?.['description']).toContain(
      'aún no está disponible',
    );
  });
});
