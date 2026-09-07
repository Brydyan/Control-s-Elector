# Proposal — T-FE-01: Arquitectura Angular Base

## Qué se construye
Estructura de carpetas base del proyecto Angular. Una arquitectura escalable que separar funcionalidad por rol (feature-based), servicios comunes (core/shared) y shell (layout).

**Patrón:** Feature-Based Organization + Core/Shared + Layout

## Por qué
- **Feature-Based:** Cada rol (observer, coordinator, master, dashboard) es un módulo independiente. Fácil agregar/remover features, lazy-load, encapsulación.
- **Core/Shared:** Servicios singleton (auth, http, notification) y componentes reutilizables en un solo lugar.
- **Standalone Components (Angular 17+):** Sin NgModules, inyección clara de dependencias, componentes más portátiles.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Estructura | Carpetas src/app, features/, core/, shared/, layout/ | Rutas, componentes UI |
| Componentes | Standalone: estructura, imports, decorators | Implementación, lógica |
| Servicios | Auth, notifications (stubs) | Implementación real |
| Barrel files | index.ts en cada carpeta | Configuración de build |
| Configuración | tsconfig paths para imports limpios | Webpack, Vite setup |

## Criterio de aceptación

- [ ] Carpetas existen con nombres correctos
- [ ] Todos los servicios tienen barrels (index.ts)
- [ ] Un componente standalone de prueba (TestComponent) puede importar desde shared
- [ ] tsconfig paths configuradas: `@core/*`, `@shared/*`, etc.
- [ ] Documentación: README en src/app/ explica la estructura

## Fuera de alcance

- Rutas (T-FE-02)
- Lógica de autenticación (T-FE-03)
- Componentes UI específicos de roles
- Tests

## Dependencias

- Node 18+, Angular 17+
- No depende de otras tareas frontend

## Decisiones a tomar

**D1 — Standalone Components vs NgModules:**
Se usa 100% standalone. `@Component({standalone: true})`, no SharedModule.

**D2 — tsconfig paths:**
Usar `@core/*`, `@shared/*`, `@features/*` para imports limpios. Evita `../../../../../../`.
