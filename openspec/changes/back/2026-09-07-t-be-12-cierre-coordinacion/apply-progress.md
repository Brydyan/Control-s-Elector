# Apply progress — T-BE-12 cierre de coordinación

## Estado

- Change: `back/2026-09-07-t-be-12-cierre-coordinacion`
- Rama: `fix/t-be-12-cierre`
- Modo: Implementador; sin commit, push ni archivado.
- Structured status consumido: `applyState=ready`, `actionContext.mode=apply`, raíces permitidas respetadas. No hubo warnings.
- Workload: no había `Review Workload Forecast` en `tasks.md`; el cambio queda bajo el presupuesto de 400 líneas y single-pr.
- `skill_resolution: fallback-path` (se cargó el skill global `gentle-ai` porque no se inyectó una ruta de fase).

## A — Línea base

- A.1: `git status --short` estaba limpio antes de comenzar.
- A.2: comando solicitado ejecutado. `CoordinatorControllerTest`: **24 tests, 0 errors, 0 failures, 0 skipped**. El `exit=1` del comando literal fue por su montaje de Docker relativo; la ejecución equivalente con el repositorio montado en `/repo` terminó en `exit=0` y confirma las cifras del XML.
- A.3: `CoordinatorController` tiene **6 endpoints reales**: cinco `GET`/`PUT` y un `DELETE`, coincidiendo con el spec.

## B — Mutaciones

| ID | Mutación | Test que cayó exactamente | Decisión |
|---|---|---|---|
| B.1 | Se quitó la autorización específica de anulación. | `CoordinatorControllerTest.anular_asCoordinator_returns403` | Pasó; se restauró con `.bak`. |
| B.2 | Se eliminó el guard APROBADA en `AnularActaUseCase`. | `CoordinatorControllerTest.anular_asMaster_actaEnviada_retorna409` | Se añadió el test faltante; pasó; se restauró con `.bak`. |
| B.3 | Se permitió EN_DISPUTA en `AprobarActaUseCase`. | `CoordinatorControllerTest.aprobar_actaEnDisputa_retorna409` | Se añadió el test faltante; pasó; se restauró con `.bak`. |
| B.4 | Se quitó `@NotBlank` de `DisputaDto.motivo`. | `CoordinatorControllerTest.disputar_sinMotivo_returns400` | Pasó; se restauró con `.bak`. |
| B.5 | Se sustituyó la protección de clase por `hasRole('ADMIN')`. | `CoordinatorControllerTest.endpoints_asAdmin_returns403` | Se añadió el test faltante; pasó; se restauró con `.bak`. |

B.6 quedó resuelto con tres tests nuevos; no quedaron mutaciones sin matar.

## C — Huecos conocidos

- C.1: ya existían verificaciones de filas `audit_log` para `disputar`, `aprobar` y `anular` en `CoordinatorControllerTest` (`findByAccion`). No fue necesario añadir otro test; la mutación de aprobación se verificó mediante B.3/B.6 y la cobertura existente de auditoría.
- C.2: **implementado**. Se añadió `cantonId` opcional al endpoint paginado, a `findWithFilters` y a `findPageWithFilters`, usando `a.jrv.parroquia.canton.id`; se añadió `listarActas_conFiltroCantonId`. `documents/task-control/backend.md` no existe en esta revisión, por lo que no fue posible editar la promesa ausente.

## D — Fuentes alineadas

- D.1: filesystem rename de `documents/task-control/backendtask/T-BE-12.md` a `documents/task-control/backendtask/COMPLETADO-T-BE-12.md` (sin `git mv`).
- D.2: cabecera actualizada a **28 tests en verde, 7 garantías verificadas por mutación**.
- D.3: documentadas `/api/v1/coordinator` (no `/api/coordinator`), respuesta `204 No Content` de `anular` y los cuatro parámetros originales de `findWithFilters`.

## Archivos modificados

- `backend/electoral-api/src/main/java/ec/twintailcs/electoral/acta/domain/ActaRepository.java`
- `backend/electoral-api/src/main/java/ec/twintailcs/electoral/acta/web/CoordinatorController.java`
- `backend/electoral-api/src/main/java/ec/twintailcs/electoral/acta/web/ObserverController.java`
- `backend/electoral-api/src/test/java/ec/twintailcs/electoral/acta/web/CoordinatorControllerTest.java`
- `documents/task-control/backendtask/COMPLETADO-T-BE-12.md` (renombrado)
- `openspec/changes/back/2026-09-07-t-be-12-cierre-coordinacion/tasks.md`
- Este `apply-progress.md`

## Compuertas finales

- `mvn -B test -Dtest=CoordinatorControllerTest`: **28 tests, 0 errors, 0 failures, 0 skipped**, exit 0.
- `mvn -B test`: **53 suites, 215 tests, 0 errors, 0 failures, 1 skipped**, exit 0.
- `mvn -B spotless:check`: exit 0.

## Tareas completadas

Todas las casillas de implementación A.1–A.3, B.1–B.6, C.1–C.2 y D.1–D.3 fueron marcadas `- [x]` en `tasks.md`.

## Desviaciones

La implementación actual está organizada en casos de uso, no en la antigua lógica monolítica de `ActaService`; por eso las mutaciones B.2/B.3 se aplicaron sobre `AnularActaUseCase` y `AprobarActaUseCase`. La firma ampliada de `findWithFilters` requirió ajustar `ObserverController` con `cantonId=null` para preservar su comportamiento.

## Próximo paso

`parent-lifecycle` — corresponde al padre ejecutar verificación/receipts y luego decidir commit, push o archive.
