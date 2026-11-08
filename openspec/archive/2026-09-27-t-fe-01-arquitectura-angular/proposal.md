# Proposal — T-FE-01: Arquitectura Angular Base (Validación de Implementación Actual)

## Qué se construye

**Validación y documentación de la arquitectura Angular actual** que ya está implementada en el proyecto.

Confirmación de que la estructura feature-based + core/shared + layout existente cumple con los patrones de escalabilidad, mantenibilidad y lazy-load esperados.

**Patrón Implementado:** Feature-Based Organization + Core/Shared + Layout (Angular 17+ Standalone)

## Por qué

- **Feature-Based:** Cada rol (observer, coordinator, admin, dashboard, auth) es un módulo independiente. Facilita agregar/remover features, lazy-load, y encapsulación.
- **Core/Shared:** Servicios singleton (auth, menu, network) y componentes reutilizables (UI, modelos, utilities) en un solo lugar.
- **Standalone Components (Angular 17+):** 100% implementado. Sin NgModules, inyección clara de dependencias, componentes portátiles.

## Estado Actual

**ARQUITECTURA YA IMPLEMENTADA Y FUNCIONAL:**

```
src/app/
├── core/                    ✅ Servicios singleton
│   ├── auth/               (AuthService, AuthStore, AuthGuard, AuthInterceptor)
│   ├── menu/               (MenuService)
│   └── network/            (NetworkStatusService)
├── shared/                  ✅ Componentes y utilities reutilizables
│   ├── ui/                 (Badge, Button, Card, EmptyState, Paginator)
│   ├── components/         (Componentes complejos reutilizables)
│   ├── models/             (Tipos TypeScript compartidos)
│   ├── errors/             (Error handling utilities)
│   └── a11y/               (Accesibilidad)
├── features/                ✅ Módulos por rol
│   ├── auth/               (Login, autenticación)
│   ├── observer/           (OBSERVER workflow)
│   ├── coordinator/        (COORDINATOR workflow)
│   ├── admin/              (ADMIN dashboard)
│   ├── dashboard/          (Resultados consolidados)
│   └── placeholder/        (Templates de ejemplo)
└── layout/                  ✅ Shell de aplicación
    └── (Navbar, sidenav, routing container)
```

## Criterio de Aceptación

- [x] Carpetas existen con nombres correctos (core/, shared/, features/, layout/)
- [x] Servicios singleton implementados y funcionales en core/
- [x] Componentes standalone en uso en features/
- [x] No hay NgModules — 100% standalone
- [x] Aplicación compila sin errores (`ng build` exit 0)
- [x] Features por rol (observer, coordinator, admin, dashboard)
- [ ] **Mejora futura (Opcional):** tsconfig paths para imports limpios (`@core/*`, `@shared/*`)
- [ ] **Mejora futura (Opcional):** Barrels (index.ts) en cada carpeta para exportar públicos

## Fuera de Alcance (Cubierto por otros T-FE-*)

- Rutas y routing (T-FE-02)
- Lógica de autenticación (T-FE-03)
- HTTP interceptors (T-FE-04)
- UI de roles específicos (T-FE-05 onwards)
- Tests (no es scope de arquitectura base)

## Dependencias

- Node 18+, Angular 17+ (ya en proyecto)
- No depende de otras tareas frontend (anterior a éstas)

## Decisiones Ya Tomadas (Implementadas)

**D1 — Standalone Components:**
✅ 100% standalone implementado. `@Component({standalone: true})`, sin SharedModule.

**D2 — Feature Structure:**
✅ Features por rol: observer, coordinator, admin, dashboard, auth. Fácil agregar/remover.

**D3 — Core/Shared Separation:**
✅ Servicios singleton en core/, componentes/utilities reutilizables en shared/.

**D4 — Path Aliases (Mejora Futura — NO IMPLEMENTADO):**
❌ Imports actualmente relativos. Decisión: Agregar `@core/*`, `@shared/*` en futuro si mejora claridad.
Razón: No es crítico; imports relativos funcionan bien actualmente.

**D5 — Barrels (Mejora Futura — NO IMPLEMENTADO):**
❌ No hay index.ts centralizados. Componentes importan directamente por ruta.
Razón: Funcional; mejora opcional para futuros refactors.
