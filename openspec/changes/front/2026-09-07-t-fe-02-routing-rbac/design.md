# Design — T-FE-02: Routing con RBAC Guards

## D1 — Guards Funcionales vs Classes

**Decisión:** Funcionales (`CanActivateFn`). Sin clases, `inject()` para servicios.

**Alternativa descartada — CanActivate class.** `export class AuthGuard implements CanActivate { ... }`. Funciona pero es boilerplate. Funcionales son más simples en Angular 16+.

## D2 — Ubicación de Guards

**Decisión:** `core/auth/guards/`. Cada guard su archivo (`auth.guard.ts`, `role.guard.ts`).

**Alternativa descartada — en mismo archivo que servicio.** `core/auth/auth.service.ts` exporta el guard. Difícil de descubrir.

## D3 — Composición de Guards

**Decisión:** Usar `canActivate: [authGuard, roleGuard]` en la ruta. Angular ejecuta en orden.

**Alternativa descartada — un solo guard que hace todo.** Un guard chequea auth + rol. Código más acoplado, más difícil de reutilizar.

## D4 — Manejo de autorización fallida

**Decisión:** roleGuard retorna `false` → Angular redirige a `/`. authGuard retorna `/login`.

**Alternativa descartada — lanzar error, dejar que HttpErrorInterceptor lo maneje.** Eso es para HTTP. Guards son antes de cargar el componente.
