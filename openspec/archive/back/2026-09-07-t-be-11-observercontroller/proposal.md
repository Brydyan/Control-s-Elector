# Proposal — T-BE-11 · Endpoints de actas (OBSERVER)

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-11.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`ObserverController` (`/api/v1/observer`): `mis-actas`, `correcciones-pendientes`, `jrvs`,
`jrv/{id}/cargos`, `POST actas`, `PUT actas/{id}/corregir`, `POST actas/{id}/imagen`.

Es la superficie que usa el veedor en campo — la única que se ejecuta desde un móvil, con mala
red, el día de la elección.

## El archivo se contradice a sí mismo

El fichero se llama `COMPLETADO-T-BE-11.md` y su línea 3 dice:

> **Estado:** ❌ **NO IMPLEMENTADO** — ActaService existe. Falta ObserverController.

`ObserverController` existe, con los siete endpoints. El nombre del archivo dice una cosa y
su cabecera la contraria.

Es el tercer caso del mismo patrón en esta carpeta: `T-BE-12.md` dice «no implementado» y lo
está; `T-BE-14.md` dice «completado» y le falta la mitad. **Las cabeceras de los task docs no
son evidencia de nada.**

## Estado real

| | |
|---|---|
| Los 7 endpoints | existen |
| `ObserverControllerTest` | 11 tests, **los 11 en ERROR** |
| `POST /actas/{actaId}/imagen` | devuelve `501 NOT_IMPLEMENTED` |
| Decisión de JRV libre (sin pre-asignación) | aplicada, documentada en `ActaService:77-82` |

## Los tres hallazgos

**1. `GET /jrvs` devuelve las ~450 JRVs y hace N consultas por cada una.**
`toJrvConEstadoDto` llama a `jrvCargoRepository.findByJrvIdAndActivo` y a
`actaRepository.findByJrvId` **por JRV**. Con 450 JRVs son ~900 consultas por petición, y
además trae todas las actas de cada mesa a memoria para contar cargos distintos.

Es el endpoint que abre la app del veedor. En una red móvil mala, el día de la elección.

**2. Las rutas del task doc no son las del código.** El doc y `backend.md` hablan de
`POST /api/actas`, `PATCH /api/actas/{actaId}`, `GET /api/observer/mis-actas`. Lo real es
`/api/v1/observer/actas`, `PUT /api/v1/observer/actas/{id}/corregir`,
`/api/v1/observer/mis-actas`.

Quien escriba el frontend (T-FE-04 … T-FE-07) contra el documento llamará a rutas que no
existen.

**3. `GET /jrvs` no filtra nada.** Devuelve todas las JRVs de la provincia a cualquier
usuario autenticado (`@PreAuthorize("isAuthenticated()")`). Es coherente con la decisión de
JRV libre, pero conviene que sea una decisión escrita y no una omisión: hoy un ADMIN —rol de
sólo lectura— también recibe la lista completa.

## Alcance

- Verificar por mutación las garantías de los siete endpoints.
- Medir el coste real de `GET /jrvs` y decidir si se corrige ahora.
- Corregir las rutas en los documentos antes de que el frontend se escriba contra ellas.

## Fuera de alcance

- **No** se implementa `POST /actas/{id}/imagen`: es `t-be-14-storage-acta`.
- **No** se toca `ActaService`: es T-BE-06 y T-BE-07.

## Criterio de cierre

1. Los 11 tests pasan.
2. Cada garantía del spec tiene su mutación ejecutada.
3. El coste de `GET /jrvs` está **medido** con datos realistas, y hay una decisión escrita.
4. Las rutas reales constan en `backend.md` y en la cabecera del task doc, y el archivo deja
   de contradecirse.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
