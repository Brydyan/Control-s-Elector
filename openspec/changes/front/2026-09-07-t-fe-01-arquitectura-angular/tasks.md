# Tasks — T-FE-01: Arquitectura Angular Base

## Fase A: Estructura de Carpetas

- [ ] **A.1** — Crear carpeta `src/app/core/`. Dentro: `auth/`, `services/`.
- [ ] **A.2** — Crear carpeta `src/app/shared/`. Dentro: `components/`, `pipes/`, `models/`.
- [ ] **A.3** — Crear carpeta `src/app/features/`. Dentro: `observer/`, `coordinator/`, `master/`, `dashboard/`.
- [ ] **A.4** — Crear carpeta `src/app/layout/`.
- [ ] **A.5** — Verificar que proyecto compila sin errores tras crear carpetas vacías.

**Compuerta A:** `ng build` sin errores de TypeScript. Reportar primer error si falla.

## Fase B: Servicios Stubs

- [ ] **B.1** — Crear `core/auth/auth.service.ts` (vacío, solo clase con `ngInjectable()`).
- [ ] **B.2** — Crear `core/auth/token.service.ts` (vacío, solo clase).
- [ ] **B.3** — Crear `core/services/notification.service.ts` (vacío, solo clase).
- [ ] **B.4** — Crear índices (barrels): `core/auth/index.ts`, `core/services/index.ts`, `core/index.ts`.
- [ ] **B.5** — Verificar que imports funcionan: `import { AuthService } from '@core/auth'`.

**Compuerta B:** `ng build` compila. Verificar en tsconfig que paths están configuradas.

## Fase C: Componentes Standalone Base

- [ ] **C.1** — Crear `shared/components/ejemplo.component.ts` (standalone, vacío).
- [ ] **C.2** — Crear `shared/components/index.ts` que exporta ExemploComponent.
- [ ] **C.3** — Crear `features/observer/home/home.component.ts` (standalone, importa ExemploComponent).
- [ ] **C.4** — Verificar que home.component renderiza sin errores.
- [ ] **C.5** — Crear `src/app/README.md` describiendo la estructura.

**Compuerta C:** `ng build` compila. Componente puede importar y renderizar from `@shared/components`.

## Qué NO hacer en esta fase

- **No agregar rutas** — eso es T-FE-02
- **No crear guard, interceptor** — T-FE-03, T-FE-04
- **No escribir lógica de negocio** — servicios son stubs únicamente
- **No compilar para producción** — `ng build` en modo dev es suficiente

## Compuertas

| Fase | Comando exacto | Expected | Si falla |
|------|---|---|---|
| A | `ng build` | exit 0 | Revisar error de TypeScript, reportar línea |
| B | `ng build` | exit 0 | Revisar tsconfig paths, reportar línea |
| C | `ng build` | exit 0 | Revisar imports en home.component, reportar línea |

## Verificación por Mutación

- **V.1** — Remover `src/app/core/index.ts` → `ng build` debe fallar en alguien que importe `@core/auth`
- **V.2** — Cambiar path en tsconfig: `@core` → `@core-x` → `ng build` debe fallar → restaurar → verificar que funciona
