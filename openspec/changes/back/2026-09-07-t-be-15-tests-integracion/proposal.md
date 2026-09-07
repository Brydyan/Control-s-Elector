# Proposal — T-BE-15 · Tests de integración críticos

Origen: `documents/task-control/backendtask/T-BE-15.md` · Área: back · Fecha: 2026-09-07

---

## Qué se construye

Los tests que recorren el camino completo —HTTP → controlador → servicio → repositorio →
PostgreSQL— para las cinco garantías de las que depende la integridad del conteo.

Y, sobre todo: **la prueba de que esos tests protegen algo**, ejecutando su mutación.

## Por qué

Hoy hay 93 tests. **78 están en ERROR** y los 14 que pasan no tocan la base de datos
(`JwtServiceTest`, `ConfiguracionServiceTest`, `UuidV7GeneratorTest`, dos de DTO). Es decir:
**cero tests de integración se están ejecutando.**

Las garantías que nadie prueba hoy:

| Garantía | Dónde vive | Test que la cubra |
|---|---|---|
| Una acta por `jrv_id + cargo_id` | `ActaService.registrar` + `findActaActivaConLock` | ninguno |
| `SUM(votos)+nulos+blancos = totalSufragantes` | `ActaService.registrar` y `corregir` | ninguno |
| Ciclo ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA | `ActaService` | ninguno |
| RLS: un OBSERVER no ve actas ajenas | `RlsInterceptor` + policies | ninguno |
| Filtro por OBSERVER en el listado global | `findWithFilters` | ninguno |

## El requisito que el task doc no ve

`T-BE-15.md` propone **H2 en memoria** con `ddl-auto=create-drop`. Eso invalida cuatro de las
cinco garantías de arriba:

- `SELECT ... FOR UPDATE` se comporta distinto en H2 que en PostgreSQL, así que el test de
  locking probaría otra cosa.
- **RLS no existe en H2.** El test de acceso denegado pasaría siempre, midiendo nada.
- `create-drop` salta Liquibase por completo — el mismo esquema roto que tenemos hoy quedaría
  sin detectar, que es precisamente cómo llegamos aquí.
- Los tipos `jsonb`, `gen_random_uuid()` y `TIMESTAMPTZ` no son equivalentes.

Es el patrón §4.5 del playbook: comportamiento que depende de dónde corre, con el lado
permisivo en el arnés de tests. El proyecto **ya tiene** `spring-boot-testcontainers` y
`testcontainers-postgresql` en `pom.xml`. Ver `design.md` D1.

## Alcance

- Tests de integración contra **PostgreSQL real** (Testcontainers) para las cinco garantías.
- Un arnés de datos de prueba reutilizable (JRV, cargo, candidatos, usuarios por rol).
- La mutación de cada garantía, ejecutada, con el nombre del test que cayó.
- El test de concurrencia real para el locking: dos hilos, no dos llamadas secuenciales.

## Fuera de alcance

- **No** se arreglan los 78 errores: eso es `fix-liquibase-rbac-order`. Este change **empieza
  después** y su primera medición es esa suite en verde.
- **No** se persigue cobertura por porcentaje. Cinco garantías probadas de verdad valen más
  que un número.
- **No** se añade H2 al `pom.xml`. Ver `design.md` D1.

## Criterio de cierre

1. Las cinco garantías tienen test **y** mutación ejecutada, con el nombre del test que cayó
   anotado en `apply-progress.md`.
2. El test de locking usa concurrencia real y falla si se quita el `@Lock`.
3. El test de RLS corre contra PostgreSQL con las policies aplicadas, y **falla si se
   desactiva `RlsInterceptor`** — hoy ese interceptor no tiene ningún test que lo cubra.
4. El total de tests sube en el número de tests añadidos. Contado, no estimado.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order` (bloqueante duro),
`back/2026-09-07-t-be-12-cierre-coordinacion` (el ciclo de estados que se recorre).
