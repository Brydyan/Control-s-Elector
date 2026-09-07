# Tasks — T-FE-02: Routing con RBAC Guards

## Fase A: Guards Funcionales

- [ ] **A.1** — Crear `core/auth/guards/auth.guard.ts`. Exporta `authGuard: CanActivateFn`.
  - Chequea si `TokenService` tiene token.
  - Si no, retorna `/login`.
  - Si sí, retorna true.
- [ ] **A.2** — Crear `core/auth/guards/role.guard.ts`. Exporta `roleGuard: CanActivateFn`.
  - Inyecta `TokenService` para saber el rol del usuario.
  - Lee `data.allowedRoles` de la ruta (ej: `{ data: { allowedRoles: ['OBSERVER'] } }`).
  - Si rol está en allowedRoles, retorna true.
  - Si no, retorna false.
- [ ] **A.3** — Crear `core/auth/guards/index.ts` que exporta ambos guards.
- [ ] **A.4** — Verificar que imports funcionan: `import { authGuard, roleGuard } from '@core/auth/guards'`.

**Compuerta A:** `ng build` sin errores.

## Fase B: Rutas Base

- [ ] **B.1** — Crear `src/app/app.routes.ts` con rutas base:
  ```typescript
  {
    path: 'login',
    component: LoginComponent,  // será creado en T-FE-05
    canActivate: [] // público
  },
  {
    path: 'observer',
    loadChildren: () => import('./features/observer/observer.routes')
      .then(m => m.OBSERVER_ROUTES),
    canActivate: [authGuard, roleGuard],
    data: { allowedRoles: ['OBSERVER'] }
  }
  ```
- [ ] **B.2** — Crear `features/{observer,coordinator,master,dashboard}/` rutas internas (stubs, solo rutas vacías).
- [ ] **B.3** — Actualizar `main.ts` o `app.config.ts` para usar `app.routes.ts`.

**Compuerta B:** `ng build` compila. app.routes.ts se encuentra en src/app/.

## Fase C: Lazy-load Verificación

- [ ] **C.1** — Agregar componentes stub en cada feature (`observer/home`, `coordinator/actas`, etc.).
- [ ] **C.2** — Verificar lazy-load en DevTools: Network tab, inicialmente no debe cargar feature chunks.
- [ ] **C.3** — Navegar a /observer/home, verificar que se carga el chunk dinámicamente.

**Compuerta C:** `ng build`. Verificar en DevTools que chunks se cargan dinámicamente.

## Qué NO hacer

- **No implementar auth real** — servicios son stubs (T-FE-03)
- **No crear componentes UI** — solo stubs funcionales
- **No agregar manejo de 403** — solo redirect a /

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | exit 0 |
| B | `ng build` && `grep -r "app.routes.ts"` | archivo existe |
| C | Navegar en dev, DevTools Network | feature chunk cargado dinámicamente |

## Verificación por Mutación

- **V.1** — Remover authGuard de /observer/* → debe accesible sin token → restaurar
- **V.2** — Cambiar allowedRoles a ['MASTER'] → OBSERVER no accede → restaurar
