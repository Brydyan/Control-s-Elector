# T-FE-02 — Routing con RBAC Guards

**Dificultad:** Principiante-Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-FE-01

---

## ¿Qué vas a construir?

El sistema de navegación de la app. Cada ruta está protegida: si no estás autenticado o tu rol no tiene acceso, serás redirigido automáticamente.

```
/login              → cualquiera puede entrar
/observer/*         → solo OBSERVER
/coordinator/*      → COORDINATOR y MASTER
/master/*           → solo MASTER
/dashboard/*        → ADMIN, MASTER y COORDINATOR
```

---

## Conceptos clave

### ¿Qué es un Guard en Angular?
Un guard es una función que Angular ejecuta antes de cargar una ruta. Si retorna `true`, la navegación continúa. Si retorna `false` o una URL, redirige.

```typescript
// Cuando navegas a /observer/home, Angular llama al guard primero:
// Guard pregunta: "¿está autenticado?" → sí → carga la ruta
//                                       → no → redirige a /login
```

### Lazy loading
Las rutas se cargan solo cuando el usuario las visita, no al iniciar la app. Esto hace la app más rápida:

```typescript
{
  path: 'observer',
  loadChildren: () => import('./features/observer/observer.routes')
    .then(m => m.OBSERVER_ROUTES)
}
```

### inject() — inyección funcional
En guards funcionales (Angular 16+), usas `inject()` en vez de un constructor:

```typescript
export const authGuard: CanActivateFn = (route, state) => {
  const tokenService = inject(TokenService);  // inyecta el servicio
  // ...
};
```

---

## Archivos a crear/modificar

```
src/app/
  core/guards/
    auth.guard.ts
    role.guard.ts
  app.routes.ts         ← rutas principales
  features/
    observer/observer.routes.ts
    coordinator/coordinator.routes.ts
    master/master.routes.ts
    dashboard/dashboard.routes.ts
```

---

## Pasos

### Paso 1 — AuthGuard (verifica si está autenticado)

```typescript
// src/app/core/guards/auth.guard.ts
import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../services/token.service';

export const authGuard: CanActivateFn = (route, state) => {
  const tokenService = inject(TokenService);
  const router = inject(Router);

  // Verifica si hay un access token válido en memoria
  if (tokenService.hasValidToken()) {
    return true;  // Deja pasar
  }

  // No autenticado → redirige al login
  // Guardamos la URL que intentaba visitar para redirigir después del login
  return router.createUrlTree(['/login'], {
    queryParams: { returnUrl: state.url }
  });
};
```

### Paso 2 — RoleGuard (verifica el rol)

```typescript
// src/app/core/guards/role.guard.ts
import { inject } from '@angular/core';
import { ActivatedRouteSnapshot, CanActivateFn, Router } from '@angular/router';
import { TokenService } from '../services/token.service';
import { RolUsuario } from '../../shared/models';

export const roleGuard: CanActivateFn = (route: ActivatedRouteSnapshot, state) => {
  const tokenService = inject(TokenService);
  const router = inject(Router);

  // Los roles permitidos se definen en la configuración de la ruta (ver app.routes.ts)
  const rolesPermitidos: RolUsuario[] = route.data['roles'];
  const rolActual = tokenService.getRol();

  if (rolActual && rolesPermitidos.includes(rolActual)) {
    return true;  // Rol permitido → acceso concedido
  }

  // Rol no permitido → redirige a la ruta de inicio según su rol
  if (rolActual) {
    return router.createUrlTree([getRutaInicio(rolActual)]);
  }

  return router.createUrlTree(['/login']);
};

// Determina la ruta de inicio según el rol
function getRutaInicio(rol: RolUsuario): string {
  switch (rol) {
    case 'OBSERVER': return '/observer/home';
    case 'COORDINATOR': return '/coordinator/home';
    case 'MASTER': return '/coordinator/home';  // MASTER también ve el coordinator
    case 'ADMIN': return '/dashboard';
    default: return '/login';
  }
}
```

### Paso 3 — Rutas de cada feature

```typescript
// src/app/features/observer/observer.routes.ts
import { Routes } from '@angular/router';

export const OBSERVER_ROUTES: Routes = [
  {
    path: 'home',
    loadComponent: () => import('./home/observer-home.page')
      .then(m => m.ObserverHomePage)
  },
  {
    path: 'jrv/:jrvId/registrar',
    loadComponent: () => import('./registrar-acta/registrar-acta.page')
      .then(m => m.RegistrarActaPage)
  },
  {
    path: 'actas/:actaId/corregir',
    loadComponent: () => import('./corregir-acta/corregir-acta.page')
      .then(m => m.CorregirActaPage)
  },
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full'
  }
];
```

```typescript
// src/app/features/coordinator/coordinator.routes.ts
import { Routes } from '@angular/router';

export const COORDINATOR_ROUTES: Routes = [
  {
    path: 'home',
    loadComponent: () => import('./home/coordinator-home.page')
      .then(m => m.CoordinatorHomePage)
  },
  {
    path: '',
    redirectTo: 'home',
    pathMatch: 'full'
  }
];
```

```typescript
// src/app/features/master/master.routes.ts
import { Routes } from '@angular/router';

export const MASTER_ROUTES: Routes = [
  {
    path: 'configuracion',
    loadComponent: () => import('./configuracion/configuracion.page')
      .then(m => m.ConfiguracionPage)
  },
  {
    path: 'sesiones',
    loadComponent: () => import('./sesiones/sesiones.page')
      .then(m => m.SesionesPage)
  },
  {
    path: '',
    redirectTo: 'configuracion',
    pathMatch: 'full'
  }
];
```

### Paso 4 — app.routes.ts (rutas principales)

```typescript
// src/app/app.routes.ts
import { Routes } from '@angular/router';
import { authGuard } from './core/guards/auth.guard';
import { roleGuard } from './core/guards/role.guard';

export const routes: Routes = [
  // Ruta pública — no requiere autenticación
  {
    path: 'login',
    loadComponent: () => import('./features/login/login.page')
      .then(m => m.LoginPage)
  },

  // Rutas de OBSERVER — solo OBSERVER puede entrar
  {
    path: 'observer',
    canActivate: [authGuard, roleGuard],  // Primero verifica auth, luego el rol
    data: { roles: ['OBSERVER'] },        // Lista de roles permitidos
    loadChildren: () => import('./features/observer/observer.routes')
      .then(m => m.OBSERVER_ROUTES)
  },

  // Rutas de COORDINATOR — COORDINATOR y MASTER pueden entrar
  {
    path: 'coordinator',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['COORDINATOR', 'MASTER'] },
    loadChildren: () => import('./features/coordinator/coordinator.routes')
      .then(m => m.COORDINATOR_ROUTES)
  },

  // Rutas de MASTER — solo MASTER
  {
    path: 'master',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['MASTER'] },
    loadChildren: () => import('./features/master/master.routes')
      .then(m => m.MASTER_ROUTES)
  },

  // Dashboard — ADMIN, MASTER y COORDINATOR
  {
    path: 'dashboard',
    canActivate: [authGuard, roleGuard],
    data: { roles: ['ADMIN', 'MASTER', 'COORDINATOR'] },
    loadChildren: () => import('./features/dashboard/dashboard.routes')
      .then(m => m.DASHBOARD_ROUTES)
  },

  // Ruta raíz — redirige a login
  {
    path: '',
    redirectTo: 'login',
    pathMatch: 'full'
  },

  // Cualquier ruta desconocida → login
  {
    path: '**',
    redirectTo: 'login'
  }
];
```

### Paso 5 — Registrar las rutas en app.config.ts

```typescript
// src/app/app.config.ts
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
// En T-FE-04 agregarás los interceptors aquí

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      // Los interceptors se agregarán en T-FE-04
    ),
  ]
};
```

---

## Cómo verificar que funciona

1. Corre `ng serve`
2. Ve a `http://localhost:4200/observer/home` sin estar logueado
3. Debe redirigirte a `http://localhost:4200/login?returnUrl=%2Fobserver%2Fhome`
4. Ve a `http://localhost:4200/dashboard` sin token
5. Debe redirigirte a login

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| Guard nunca redirige | `tokenService.hasValidToken()` siempre retorna true | Verifica la implementación del TokenService (T-FE-03) |
| `Cannot find module` en lazy loading | Ruta al archivo incorrecta | Verifica que el path en `loadComponent` existe exactamente |
| `NullInjectorError: No provider for TokenService` | TokenService no está en `root` | Agrega `providedIn: 'root'` en el decorator `@Injectable` |
| Bucle infinito de redirects | El guard redirige a una ruta que también tiene guard | Verifica que `/login` no tiene guard |
