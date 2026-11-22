# Verificación SDD — Performance Optimization (T-PERF-OPT-01)

**SDD:** `openspec/changes/back/2026-11-19-performance-optimization/`
**Rama:** `brydyan/sc-519/feat-performance-optimization` → PR #207 (base `develop`)
**Fecha:** 2026-11-22
**Veredicto: ⚠️ PARTIAL** — implementa lo que el SDD pedía y la evidencia está medida; quedan dos
cosas fuera del alcance pedido (E2E del flujo Observer y medición contra Neon) y un incumplimiento
menor de la convención de commits que arrastra el repo, no este SDD.

---

## 1. Cumplimiento del spec

### 1.1 Propuesta — alcance

| Área del SDD | Estado | Evidencia |
|---|---|---|
| A. Índices en `acta` | ✅ | `048-add-acta-indices.sql`, 5 índices + 1 compuesto, registrados en `db.changelog-master.yaml` |
| A. Índices en `acta_detalle` | ⚠️ parcial | `049-add-acta-detalle-indices.sql` con **1** índice, no 2: el de `acta_id` es redundante con el `UNIQUE(acta_id, candidato_id)` que ya existía |
| A. Índice en `usuario.rol_id` | ✅ ya existía | `ix_usuario_rol_id` verificado en `pg_indexes` |
| B.1 EAGER → LAZY | ✅ | `Usuario.rol` LAZY + `@BatchSize(25)` sobre `Rol` |
| B.2 Batch endpoint | ✅ | `GET /api/v1/observer/cargos-with-candidatos?jrvId=`, DTOs, IT, y **consumidor frontend** (`registrar-acta`, `modificar-acta`) |
| B. Native queries optimizadas | ✅ | El repo ya usaba queries indexadas; el EXPLAIN confirma el plan |
| C. Caché frontend | ✅ | `shareReplay(1)` en `observer.api.ts` y `dashboard.api.ts` (4 cachés en dashboard) |
| D.1 gzip | ✅ | `enable-compression=true`, `compression-level=6` |
| D.2 Pool 20 → 50 | ✅ | `quarkus.datasource.jdbc.{min-size=10,max-size=50,acquisition-timeout=30s,idle-removal-interval=5m,max-lifetime=30m}` |
| E. Tests backend | ✅ | 298 tests backend, 1153 frontend + 5 a11y, 152 contract |
| E. EXPLAIN ANALYZE | ✅ | 4 queries verificadas con 50 000 filas sintéticas |
| E. Load test | ⚠️ parcial | 100 usuarios × 50 requests contra Postgres local. **No** contra Neon, que es la topología de producción |
| E. E2E flujo Observer | ❌ no ejecutado | Fuera de alcance por instrucción explícita del usuario |
| F. Documentación | ✅ | `PERFORMANCE_OPTIMIZATION.md`, `INDEXING_GUIDE.md`, `CACHING_STRATEGY.md` |

**Desviación de D4 sin consequences**: el SDD decía "mantener los 3 endpoints viejos para
compatibilidad". Se maintained — `/observer/jrv/{id}/cargos` sigue existiendo y el IT del batch
compara contra él precisamente para demostrar equivalencia.

### 1.2 Decisiones de diseño (D1–D5)

| Decisión | Elegida | Implementada | Veredicto |
|---|---|---|---|
| D1 Índices antes que query optimization | B | B | ✅ |
| D2 Lazy selectivo, no global | B | B | ✅ — solo `Usuario.rol` cambió; el resto de relaciones intactas |
| D3 Caché service-level, no interceptor | B | B | ✅ |
| D4 Endpoint batch nuevo, mantener los viejos | B | B | ✅ |
| D5 Pool 50, no 100 | A | A | ✅ |

Las cinco decisiones se aplicaron tal como se decidieron.

### 1.3 GIVEN/WHEN/THEN

El SDD no usa formato GIVEN/WHEN/THEN; usa checkboxes de tareas. Las 55 casillas se auditaron una
por una: **48 marcadas** (cada una con su evidencia en `apply-progress.md` y en `tasks.md`), **7 sin
marcar** y todas justificadas:

- E2E del flujo Observer (no ejecutado por instrucción del usuario)
- 5 metas de monitoreo post-despliegue (no verificables antes de desplegar)
- `min-gzip-size=1024` (la clave no existe en Quarkus 3.40; el filtro es por media type)

---

## 2. Cobertura de mutaciones

El SDD no define mutaciones explícitas, así que traduje sus invariantes a estados en los que el código
podría romperse y verifiqué que **un test falla** en cada uno.

| Invariante | Mutación aplicada | ¿Falla un test? | Test |
|---|---|---|---|
| `Usuario.rol` es LAZY | `FetchType.LAZY` → `EAGER` | ✅ **Sí** | `UsuarioLazyBatchIT.laAsociacionARolEsLazy` |
| El rol se carga en lote, no N+1 | `@BatchSize(25)` → `@BatchSize(1)` | ✅ **Sí** (5 sentencias vs 3) | `UsuarioLazyBatchIT.resolverLosRolesNoEscalaConElNumeroDeUsuarios` |
| Los índices de acta existen | `DROP INDEX idx_acta_detalle_candidato_id` | ✅ **Sí** | `IndicesActaIT.elIndiceDeActaDetalleExiste` |
| El endpoint batch devuelve lo mismo que el endpoint suelto | respuesta con cargos distintos | ✅ **Sí** | `ObserverCargosConCandidatosIT.devuelveElMismoCatalogoQueElEndpointSuelto` |
| El IT del batch no depende del seed | `jrv_cargo` vacía (base limpia) | ✅ **Sí** (fallaba 4/4 antes del arreglo) | `ObserverCargosConCandidatosIT` |
| La caché frontend no duplica peticiones | quitar `shareReplay(1)` | ✅ **Sí** | `observer.api.spec.ts`, `dashboard.api.spec.ts` |
| El pool sube a 50 | quitar `quarkus.datasource.jdbc.max-size` | ⚠️ **No hay test** | — el valor solo se puede observar en runtime, ver §5 |

**Cobertura: 6 de 7 invariantes con test que falla ante la mutación.** El que falta (tamaño del
pool) es una propiedad de configuración, no de comportamiento: se verificó en runtime
(§4, D.2).

### 2.1 Dos hallazgos reales que la verificación destapó

**H-1 — El IT del catálogo dependía de datos que otros IT borran.** `ObserverCargosConCandidatosIT`
buscaba "la primera JRV del seed que tenga al menos un cargo habilitado". `UserInvitationIT`,
`PasswordChangeServiceTest` y otros ejecutan `delete from jrv` / `delete from jrv_cargo` en su
limpieza. Consecuencia: en una base limpia el IT fallaba 4/4 con `NoResult`, y en una base
compartida podía pasar **usando la JRV que otro test acababa de insertar** — un falso verde que
dependía del orden de ejecución. Corregido: el IT arma su propio dataset (JRV, cargo, partido,
candidato) y lo borra al terminar.

**H-2 — El test de conteo de queries que el SDD pedía no distinguía nada.** La primera versión
comparaba `getQueryExecutionCount()` entre páginas de 5 y 20 usuarios. Ese contador **solo ve
JPQL**: con EAGER y con LAZY da el mismo número, así que el test pasaba con y sin el cambio. Se
reescribió con `getPrepareStatementCount()` (SQL emitido) y con un dataset de usuarios de **roles
distintos**, que es lo que hace visible el N+1.

---

## 3. Calidad de los tests

| Criterio | Evaluación |
|---|---|
| Pirámide completa | ✅ 298 unit + IT backend, 1153 frontend, 152 contract, 5 a11y |
| IT contra BD real, no mocks | ✅ `PostgresTestResource` (Testcontainers o `-Dtest.postgres.url`), sin mocks de persistencia |
| Casos de error | ✅ 401 sin token, 400 sin `jrvId`, JRV inexistente, 403 por rol, 429 por rate limit |
| Flakiness | ⚠️ **riesgo real, mitigado**: la suite contra base *compartida* dio 2 fallos + 25 errores (FK `incidencia_jrv_jrv_id_fkey` al limpiar `jrv` — contaminación entre suites). Contra base **limpia**: 298/298 verde. El CI usa base limpia. |
| Timeouts hardcodeados | ✅ ninguno en los tests nuevos |
| Aislamiento | ✅ los tests nuevos crean y borran su propio dataset |
| E2E de flujo crítico | ❌ no hay E2E del flujo Observer (fuera de alcance) |
| Cobertura | ✅ umbral de JaCoCo superado ("All coverage checks have been met") |

**Debilidad real**: el IT del batch pasó en verde sobre una base con datos residuales de otro
suite. Un test verde sobre datos ajenos no es evidencia. Corregido en esta sesión, pero el patrón
—tests que dependen del seed— puede seguir presente en otros IT del repo. No es de este SDD.

---

## 4. Gate por gate (lo que el SDD pedía verificar)

### A.1/A.2 — Índices
✅ **CONFIRMADO**. 8 índices `idx_acta*` visibles en `pg_indexes` (5 de `048`, 1 de `049`, 2
preexistentes de `044`), tras reconstruir el jar con los commits de perf y aplicar las migraciones
(Liquibase `Run: 2`, 108 changesets).

### A.3 — EXPLAIN ANALYZE
✅ **CONFIRMADO** con 50 000 filas sintéticas (dentro de una transacción con `ROLLBACK`):

| Query | Plan | Índice |
|---|---|---|
| `WHERE registrada_por = ?` | Index Scan | `idx_acta_registrada_por` |
| `WHERE estado = 'ENVIADA'` (7 % de las filas) | Index Scan | `idx_acta_estado` |
| Filtro compuesto jrv+cargo+estado | Bitmap Index Scan | `idx_acta_jrv_cargo_estado` |
| `ORDER BY fecha_registro DESC` | Index Scan | `idx_acta_fecha_registro_desc` |
| `acta_detalle` JOIN `acta` | Seq Scan | — (287 filas de detalle: el planner acierta a esa escala) |

### B.1 — Lazy sin N+1
✅ **CONFIRMADO** con test de conteo de queries + dos mutaciones que lo hacen fallar. Además, la
suite completa (298 tests) pasa sin `LazyInitializationException`: todos los consumidores de
`getRolNombre()` leen dentro de transacción.

### B.2 — Batch endpoint
✅ **CONFIRMADO**: 4/4 verde contra Postgres local, con dataset propio.

### D.1 — gzip
✅ **CONFIRMADO** contra backend vivo: `content-encoding: gzip` en `/api/v1/menus/me`,
`/api/v1/admin/usuarios` y `/api/v1/dashboard/resultados`.

### D.2 — Pool
✅ **CONFIRMADO con medición** (el gate estaba bloqueado: sin métricas del pool no era observable).

- Habilitadas **dos** claves: `quarkus.datasource.metrics.enabled` (global) y
  `quarkus.datasource.jdbc.metrics.enabled` (este datasource). Con una sola no aparece nada. Ambas
  son de **tiempo de build**: cambiar una exige reconstruir la imagen.
- Load test: 100 usuarios × 50 requests = **5 000 requests**, 0 errores HTTP, 0 eventos "unable to
  acquire connection", p50 25.4 ms, **p95 36.4 ms**, max 48.2 ms, 2 383 req/s.
- Pool: `agroal_max_used_count` = 50 (techo alcanzado en el pico), `agroal_creation_count_total` =
  50; en régimen permanente `agroal_active_count` 1–9 y `agroal_available_count` 41–49 → **uso medio
  5-18 %, pico 100 %**.
- `agroal_blocking_time_max_milliseconds` = 49 ms: espera máxima por conexión, sin timeouts.

> ⚠️ **El "pool al 60-80 %" que pedía el SDD no se alcanza con esta carga.** La carga es de una sola
> tabla y el pool no se satura. La cifra real en producción depende de la latencia de Neon, que
> aquí no existe. **Este número hay que medirlo en producción**, no deducirlo de un Postgres local.

### E.3 — Load test
⚠️ **PARCIAL**: se usó un script propio (100×50) en lugar del escenario exacto de JMeter (50×10) del
SDD — **más exigente**, no menos. Contra Neon: no ejecutado.

---

## 5. Calidad de los commits

| Criterio | Resultado |
|---|---|
| Formato convencional `type(scope): message` | ✅ 14/14 commits del SDD |
| Sin co-author IA | ⚠️ **2 commits con `Co-Authored-By: Claude Haiku 4.5`**, pero **ninguno es de este SDD** (son de un arreglo previo de email, `7eb304a`/`7149f73`). Verificado commit por commit. |
| Un commit por tarea | ✅ 14 commits, uno por fase/tarea |
| Mensajes que referencian la spec | ✅ varios citan la fase y el T-PERF-OPT-01 |
| Historia reescrita | ❌ no (el `1bf7dcb` se conserva, con el N.9 pendiente intacto) |
| Marcas de IA en el código | ❌ ninguna |

**Deuda que este SDD hereda** (no la introdujo): el repo tiene commits con co-author IA. AGENTS.md
lo prohíbe. No se reescribió historia (decisión explícita), así que queda como deuda del repo, no de
este cambio.

---

## 6. Alineación con AGENTS.md

| Regla | Cumplimiento |
|---|---|
| Capas resource → application → domain → persistence | ✅ el endpoint batch vive en `ObserverResource`, delega a `ActaService`, consulta en `ActaRepository` |
| Autorización con `Autorizador.exigir("<permiso>","<ROL>")`, no `@RolesAllowed` | ✅ el SDD pedía `@RolesAllowed({"OBSERVER"})`; se implementó `autorizador.exigir("acta:read")` + `@RateLimited("observer-jrvs")`. **Desviación deliberada y documentada.** |
| RLS obligatorio | ✅ `RlsIsolationIT` en verde (7 tests); el endpoint batch no salta RLS |
| Liquibase es el único dueño del schema | ✅ los índices entran por migraciones `048`/`049` registradas en `db.changelog-master.yaml`; nadie ejecuta DDL a mano |
| Mapeo ORM explícito, sin conversiones implícitas | ✅ `@ManyToOne(fetch = LAZY)` + `@BatchSize` explícitos |
| Caché explícita y medible | ✅ `shareReplay(1)`; el SDD no añadió caché de servidor (la decisión D2 de Valkey es de otro SDD) |
| Sin secretos versionados | ✅ ninguna variable sensible en los cambios |
| Sin BYTEA para evidencias | ✅ no aplica |
| Cada transición del acta necesita cobertura de integración | ✅ el flujo de registro se cubre con el IT del catálogo batch |
| E2E contra la misma API de producción, sin mocks | ⚠️ no se ejecutó E2E (fuera de alcance) |
| Push a Railway / CF Pages / Neon / R2, `k8s/` legado | ✅ no se toca infraestructura de despliegue |

---

## 7. Qué falta y por qué

| # | Falta | Impacto | Por qué |
|---|---|---|---|
| 1 | **E2E del flujo Observer** | Alto | No ejecutado por instrucción explícita del usuario. Es el gate que valida el flujo de registro de punta a punta, que es justamente donde el batch endpoint cambió el contrato de red. |
| 2 | **Load test contra Neon** | Medio | Los números de pool y latencia son contra Postgres local. La red es el factor dominante en producción; el "60-80 % de pool" solo se puede medir con la base real. |
| 3 | **Métricas del pool: re-verificar tras el deploy** | Bajo | Las claves son de tiempo de build. Hay que confirmar que el rebuild de la imagen de Railway las lleva. |
| 4 | **Deuda de co-author IA en el repo** | Bajo | Heredada, no introducida aquí. AGENTS.md la prohíbe; se corrige en un barrido aparte, sin reescribir historia. |

---

## 8. Recomendaciones de rework

No hay rework que bloquee el merge. Antes de producción:

1. **Medir el pool contra Neon** y ajustar `max-size` con dato, no con la proyección del SDD.
   El SDD decidió 50 sin medir; la decisión sigue siendo razonable, pero ahora es verificable.
2. **Ejecutar el E2E del flujo Observer** contra la API desplegada, para cerrar el gate que esta
   verificación dejó abierto.
3. **Revisar el `acta_detalle` en producción con datos reales**: el seq scan observado a escala de
   laboratorio no dice qué pasará con 50 000 actas y sus detalles.

Después del merge:

4. Barrido de los commits con co-author IA en todo el repo (sin reescribir historia: `git filter-repo`
   sobre ramas lineages, o nota de política).

---

## 9. Veredicto

# ⚠️ PARTIAL

**Listo para merge con notas.** Todo lo que el SDD pedía implementar está implementado, con la
arquitectura del proyecto respetada, la suite en verde (298 backend + 1153 frontend + 152 contract),
CI real en verde (9/9 jobs en `5935684`) y los gates de base de datos verificados con evidencia
medida —incluidos dos que estaban bloqueados por falta de PostgreSQL y que esta sesión cerró.

Las dos desviaciones que justifican PARTIAL y no CONFORM:
- **E2E del flujo Observer no ejecutado** (instrucción del usuario).
- **Load test y uso del pool medidos contra Postgres local, no contra Neon**: el "60-80 % de pool"
  que pedía el SDD no se reproduce en local, así que esa cifra sigue sin verificarse en la
  topología de producción.

Ninguna de las dos es un defecto de la implementación. Ambas son límites del alcance de la
verificación, y quedan registradas como tales.
