# Tasks — T-FE-12: Componentes Compartidos

## Fase A: Componentes Atómicos UI (`shared/ui/`)

- [x] **A.1** — Crear `shared/ui/badge/badge.component.ts`.
  - Standalone: true
  - Soporta estados electorales (`ENVIADA`, `APROBADA`, `EN_DISPUTA`, `CORREGIDA`, etc.) y roles con variantes de color.
- [x] **A.2** — Crear `shared/ui/button/button.component.ts` (variantes primary, secondary, danger, etc.).
- [x] **A.3** — Crear `shared/ui/card/card.component.ts` para contenedores de métricas y formularios.
- [x] **A.4** — Crear `shared/ui/icon/icon.component.ts`.

**Compuerta A:** `pnpm test:ci` sin errores.

## Fase B: Componentes de Estado y Feedback

- [x] **B.1** — Crear `shared/ui/loading-state/loading-state.component.ts`.
- [x] **B.2** — Crear `shared/ui/error-state/error-state.component.ts` con botón de reintento.
- [x] **B.3** — Crear `shared/ui/empty-state/empty-state.component.ts`.
- [x] **B.4** — Crear `shared/ui/skeleton/skeleton.component.ts` para carga progresiva.

**Compuerta B:** `pnpm test:ci` pasa pruebas unitarias de estados.

## Fase C: Navegación y Paginación

- [x] **C.1** — Crear `shared/components/paginator/paginator.component.ts`.
- [x] **C.2** — Campana de notificaciones integrada en `layout/topbar/topbar.component.ts`.

**Compuerta C:** Paginación y feedback visual integrados en layout.

## Fase D: Barrel + Integración

- [x] **D.1** — Exportación unificada en `shared/ui/index.ts`.
- [x] **D.2** — Tests unitarios de todos los componentes compartidos (23 tests pasando).

**Compuerta D:** `pnpm build` compila sin errores.
