# Proposal — T-BE-12 · Cerrar los endpoints de coordinación

Origen: `documents/task-control/backendtask/T-BE-12.md` · Área: back · Fecha: 2026-09-07

---

## Qué se construye

Nada nuevo. Este change **cierra** T-BE-12: la funcionalidad existe, no está verificada, y
tres documentos dicen tres cosas distintas sobre ella.

## Por qué

`T-BE-12.md:3` dice **«❌ NO IMPLEMENTADO — Falta CoordinatorController»**.
`documents/task-control/backend.md:187` dice **COMPLETO**.
El código tiene `CoordinatorController` con los seis endpoints, y la rama se mergeó en el
PR #66 (`108819d`).

El archivo tampoco está renombrado a `COMPLETADO-T-BE-12.md`, que es la convención del repo.

Y `CoordinatorControllerTest` existe con 11 tests que **hoy están en ERROR**, no en verde:
el `ApplicationContext` no levanta (ver `back/2026-09-07-fix-liquibase-rbac-order`). Así que
la afirmación «está implementado y probado» no tiene ninguna evidencia detrás.

## Divergencias medidas entre el doc y el código

| Doc T-BE-12 | Código real |
|---|---|
| `@RequestMapping("/api/coordinator")` | `/api/v1/coordinator` |
| `getUsuarioId()` desde `SecurityContextHolder.getPrincipal()` casteado a `Long` | `coordinatorId()` resuelve `usuarioRepository.findByPublicId(currentUser.id())` |
| `findWithFilters(estado, jrvId, cargoId)` — 3 parámetros | 4 parámetros: `+ registradaPorId` |
| `anular` devuelve `ResponseEntity<Acta>` | devuelve `204 No Content` |
| `backend.md:192` promete filtro por **cantón** | `findWithFilters` **no tiene** filtro de cantón |
| `RuntimeException("Acta no encontrada")` | `IllegalArgumentException` |

## Alcance

- Verificar por mutación las cuatro garantías que T-BE-12 introduce.
- Cerrar la brecha del filtro por cantón: implementarlo, o quitarlo de `backend.md`.
- Renombrar el task doc a `COMPLETADO-T-BE-12.md` y alinear las tres fuentes.

## Fuera de alcance

- **No** se reescribe `CoordinatorController`. Funciona; falta probarlo.
- **No** se arreglan los 78 errores de contexto: eso es el otro change. Este **depende** de él.
- **No** se toca `ActaService`: su ciclo de vida es T-BE-07.

## Criterio de cierre

1. Los 11 tests de `CoordinatorControllerTest` **pasan**, no dan ERROR.
2. Cada una de las cuatro garantías tiene una mutación ejecutada, con el nombre del test que
   cayó anotado.
3. El filtro por cantón existe con su test, o desaparece de `backend.md:192`. No las dos.
4. `T-BE-12.md` renombrado, y `backend.md` y la cabecera del doc dicen lo mismo que el código.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order` — sin contexto de Spring, ningún test corre.
