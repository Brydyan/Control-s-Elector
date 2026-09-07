# Proposal — T-FE-02: Routing con RBAC Guards

## Qué se construye
Sistema de rutas protegidas por rol. Cada ruta está guardada: si no estás autenticado o tu rol no tiene acceso, redirige automáticamente.

## Por qué
- Control de acceso en el cliente (primero línea de defensa, UX).
- Prevenir navegación accidental a rutas no permitidas.
- Lazy-load solo features que el usuario puede ver.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Rutas | /login, /observer/*, /coordinator/*, /master/*, /dashboard/* | URLs específicas dentro de roles |
| Guards | authGuard, roleGuard (funcionales, Angular 16+) | Implementación auth (T-FE-03) |
| Lazy-load | loadChildren() por feature | Pre-carga toda la app |
| Redirección | Redirige a /login si no autenticado | Manejo de errores HTTP |

## Criterio de aceptación

- [ ] Rutas definidas en `app.routes.ts`
- [ ] authGuard redirige a /login si no hay token
- [ ] roleGuard valida rol contra ruta
- [ ] /observer/*, /coordinator/*, /master/*, /dashboard/* tienen guards
- [ ] Lazy-load funciona (features se cargan bajo demanda)
- [ ] Test: navegar sin token → redirige a /login

## Fuera de alcance

- Implementación real del auth (T-FE-03)
- Componentes específicos de cada rol
- Manejo de 403 (acceso denegado)

## Dependencias

- T-FE-01 (estructura de carpetas)

## Decisiones a tomar

**D1 — Guards funcionales vs CanActivate classes:** Usar funcionales (`CanActivateFn`). Más simples en Angular 16+.

**D2 — dónde viven los guards:** En `core/auth/guards/`. Importables vía `@core/auth`.
