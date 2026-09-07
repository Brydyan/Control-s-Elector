# Proposal — T-BE-13 · Endpoints de dashboard

Origen: `documents/task-control/backendtask/T-BE-13.md` · Área: back · Fecha: 2026-09-07

---

## Qué se construye

Cuatro endpoints de **sólo lectura** para ADMIN, MASTER y COORDINATOR:

| Endpoint | Devuelve |
|---|---|
| `GET /api/v1/dashboard/resumen` | conteo de actas por estado |
| `GET /api/v1/dashboard/resultados` | votos por candidato, agrupados por cargo |
| `GET /api/v1/dashboard/progreso` | JRVs con acta vs total, por cantón |
| `GET /api/v1/dashboard/novedades` | actas EN_DISPUTA con tiempo transcurrido |

## Por qué

Es la única vista consolidada del proceso electoral. Hoy `frontend/app` tiene una
`DashboardPage` con datos de relleno y ningún endpoint detrás.

## La regla que gobierna todo el módulo

**Las actas ANULADA se excluyen de todos los cálculos.** Aparece en `CLAUDE.md`, en
`backend.md:210` y en el task doc. Es una regla de integridad electoral: un acta anulada dejó
de ser un resultado válido, y sus votos no pueden sumar.

No es una condición que se escriba una vez: hay que aplicarla en **cada** query de agregación
y comprobar en **cada** una que se aplicó. Es el patrón §4.2 del playbook esperando a ocurrir
— una regla presente en un sitio y ausente en su vecino.

## Estado real de lo que ya existe

| Pieza | Estado |
|---|---|
| `ActaRepository.countByEstado(EstadoActa)` | existe |
| `ActaRepository.findByEstado(EstadoActa)` | existe |
| `ActaRepository.findWithFilters(...)` | existe, 4 parámetros, sin cantón |
| Queries de agregación (`SUM`, `GROUP BY`) | **no existen** |
| `DashboardController` y sus DTOs | **no existen** |
| `CantonRepository`, `JrvRepository.countByParroquia_Canton` | verificar antes de usarlos |

## Alcance

- `DashboardController` con los cuatro GET y `@PreAuthorize` de módulo.
- DTOs de respuesta: `ResumenActasDto`, `ResultadoCargoDto`, `ProgresoCantonDto`, `NovedadDto`.
- Queries de agregación en `ActaRepository` y `ActaDetalleRepository`, todas con el filtro
  de ANULADA.
- Tests que prueban la exclusión de ANULADA en cada endpoint, y el RBAC del módulo.

## Fuera de alcance

- **No** se implementa realtime (Supabase). Es T-IN-06 + T-FE-11.
- **No** se incluyen evidencias en `/novedades`. Ese trozo depende de T-BE-16, cuyo enum
  `TipoObservacion` está sin definir. `/novedades` entrega sólo actas EN_DISPUTA, y la unión
  con evidencias se abre como change aparte cuando T-BE-16 cierre.
- **No** se toca el frontend.
- **No** se añade paginación. Con ~450 JRVs y 6 cargos el techo es ~2700 actas; si la
  medición dice otra cosa, se abre un change de paginación con el número medido delante.

## Criterio de cierre

1. Los cuatro endpoints responden `200` a ADMIN, MASTER y COORDINATOR, y `403` a OBSERVER.
2. Por cada endpoint de agregación existe un test que crea un acta ANULADA y comprueba que
   **no** aparece en el resultado.
3. Cada exclusión de ANULADA tiene su mutación ejecutada, con el nombre del test que cayó.
4. `ADMIN` no puede escribir nada: el controlador expone sólo `@GetMapping`.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order` (la suite tiene que correr) y
`back/2026-09-07-t-be-12-cierre-coordinacion` (el estado EN_DISPUTA que consume `/novedades`
se produce ahí).
