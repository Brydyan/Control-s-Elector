# Apply Progress — Performance Optimization

## Estado: IMPLEMENTADO — verificación de integración ejecutada

El código de las fases A, B, D y F está aplicado y compila. La fase C ya estaba
implementada antes de esta sesión. Los gates que necesitan PostgreSQL se
ejecutaron contra un Postgres local en Docker (`ci-pg`, Postgres 16), no contra
Neon. La verificación no incluye E2E del flujo Observer (fuera de alcance por
instrucción del usuario) ni un load test contra Neon.

## Commits aplicados

| Fase | Commit | Mensaje |
|------|--------|---------|
| A | `063138e` | `perf(db): add missing acta and acta_detalle indexes` |
| B.1 | `c395d69` | `perf(auth): load usuario.rol lazily with batch size` |
| B.2 | `25527f0` | `perf(observer): add batch endpoint for JRV cargos with candidates` |
| D | `6fe24ac` | `perf(infra): enable gzip and raise the datasource pool` |
| F | `7813b2e` | docs: performance optimization and indexing guide |
| F+ | `937e18b` | fix: use real Quarkus 3.40 pool/compression keys |
| B.2 FE | `d5ce6a8` | perf(observer): wire registrar-acta/modificar-acta to batch catalog |
| Fix | `3331637` | fix(auth): move BatchSize from Usuario.rol to Rol entity |
| Fix | `285d807` | fix(api): preserve WebApplicationException status in GenericMapper |
| Gates | `32eb3f2` | test(perf): cerrar los gates B.1, B.2 y D.2 con evidencia medida |

---

## Phase A: Database Indexing

### A.1: Índices en `acta`

- [x] Migración creada: `database/changelog/048-add-acta-indices.sql`
- [x] `idx_acta_registrada_por`
- [x] `idx_acta_jrv_id`
- [x] `idx_acta_cargo_id`
- [x] `idx_acta_fecha_registro_desc`
- [x] `idx_acta_jrv_cargo_estado` (compuesto)
- [x] Registrada en `db.changelog-master.yaml`
- [x] **Testing local CONFIRMADO**: los 8 índices `idx_acta*` (5 de 048, 1 de 049
      y 2 preexistentes de 044) visibles en `pg_indexes` contra
      `electoral_db_ci`, tras reconstruir el jar con los commits de perf y
      aplicar las migraciones (Liquibase `Run: 2`, 108 changesets).

### A.2: Índices en `acta_detalle`

- [x] Migración creada: `database/changelog/049-add-acta-detalle-indices.sql`
- [x] `idx_acta_detalle_candidato_id`
- [x] Registrada en `db.changelog-master.yaml`
- [x] **Testing local CONFIRMADO**: índice visible en `pg_indexes`.

### A.3: EXPLAIN ANALYZE

- [x] **CONFIRMADO** contra Postgres local con 50 000 filas sintéticas de `acta`
      (dataset de prueba dentro de una transacción con `ROLLBACK`, para no tocar
      la base):
  - `WHERE registrada_por = ?` → **Index Scan** con `idx_acta_registrada_por`.
  - `WHERE estado = 'ENVIADA'` (7 % de las filas) → **Index Scan** con
    `idx_acta_estado`.
  - Filtro compuesto (jrv + cargo + estado) → **Bitmap Index Scan** con
    `idx_acta_jrv_cargo_estado`.
  - `ORDER BY fecha_registro DESC` → **Index Scan** con
    `idx_acta_fecha_registro_desc`.
  - La query de `acta_detalle` por JOIN queda en seq scan: en el set sintético
    hay 287 filas de detalle, y a esa escala el planner acierta. No es un
    defecto del índice; a escala de producción el planner lo elige distinto.
      Las queries y el resultado esperado quedan documentados en
      `documents/INDEXING_GUIDE.md`.

---

## Phase B: Backend ORM Optimization

### B.1: EAGER → LAZY

- [x] `Usuario.rol`: `FetchType.LAZY` + `@BatchSize(size = 25)` sobre `Rol`
- [x] Compila (`mvnw -DskipTests test-compile`)
- [x] Consumidores de `getRolNombre()` verificados uno por uno: todos corren
      dentro de una transacción (`LoginUseCase`, `RefreshTokenUseCase`,
      `ActivarUsuarioPorTokenUseCase`, `InvitacionService`,
      `UserInvitationService`, `UsuarioAdminService.listar`), así que no hay
      `LazyInitializationException`
- [x] `UsuarioMapperTest`: 8/8 en verde
- [x] **Test de conteo de queries CONFIRMADO** (`UsuarioLazyBatchIT`, commit
      `32eb3f2`): cuenta las sentencias SQL del listado de usuarios y afirma
      que resolver los roles no escala con el número de usuarios, y que la
      asociación está mapeada LAZY.
      - Mutación `Rol.@BatchSize(25)` → `@BatchSize(1)`: el test **falla**
        (5 sentencias para 3 usuarios contra 3 para 1 usuario).
      - Mutación `Usuario.rol` LAZY → EAGER: el test **falla** (la asociación ya
        no está declarada LAZY).

### B.2: Batch endpoint

- [x] `GET /api/v1/observer/cargos-with-candidatos?jrvId=`
- [x] DTOs: `CargosConCandidatosResponse`, `CargoConCandidatosDto`
- [x] `ActaRepository.listarCandidatosDeCargos` con
      `join fetch c.cargo join fetch c.partido` (una query, no N+1)
- [x] Tres queries fijas: cargos de la JRV, candidatos de esos cargos, partidos
      cacheados
- [x] Autorización `acta:read` + rate limit `observer-jrvs`
- [x] `jrvId` nulo → 400
- [x] IT `ObserverCargosConCandidatosIT` escrito (401, 400, JRV inexistente,
      equivalencia con el endpoint suelto)
- [x] **IT ejecutado, 4/4 verde** contra Postgres local.
- [x] **IT autocontenido** (commit `32eb3f2`): el IT crea su propio dataset
      (JRV, cargo, partido, candidato) y lo borra al terminar. Antes dependía
      del seed de `jrv`/`jrv_cargo`, que otros IT de la suite borran con
      `delete from jrv`: en una base limpia fallaba 4/4 con `NoResult`, y en
      una base compartida podía pasar usando la JRV de otro test.

---

## Phase C: Frontend Caching

Ya implementada antes de esta sesión; no se reimplementó.

- [x] `observer.api.ts`: `partidosCache$` con `shareReplay(1)`
- [x] `dashboard.api.ts`: cachés con `shareReplay(1)`
- [x] Documentada en `documents/CACHING_STRATEGY.md`
- [x] Suite completa en verde: 1153 tests + 5 a11y, lint limpio

---

## Phase D: Infrastructure

### D.1: gzip

- [x] `quarkus.http.enable-compression=true`
- [x] `quarkus.http.compression-level=6`
- [x] sin umbral de bytes: Quarkus 3.40 comprime por media type, no por tamano
- [x] **CONFIRMADO contra backend vivo**: las respuestas de
      `/api/v1/menus/me`, `/api/v1/admin/usuarios` y
      `/api/v1/dashboard/resultados` traen `content-encoding: gzip`.

### D.2: Pool

- [x] `quarkus.datasource.jdbc.min-size=${DB_POOL_MIN_SIZE:10}`
- [x] `quarkus.datasource.jdbc.max-size=${DB_POOL_MAX_SIZE:50}`
- [x] `quarkus.datasource.jdbc.acquisition-timeout=${DB_POOL_ACQUISITION_TIMEOUT:30s}`
- [x] `quarkus.datasource.jdbc.idle-removal-interval=5m`, `max-lifetime=30m`
- [x] **Métricas del pool habilitadas** (commit `32eb3f2`): hacen falta **dos**
      claves, no una: `quarkus.datasource.metrics.enabled=true` (global) y
      `quarkus.datasource.jdbc.metrics.enabled=true` (este datasource). Con solo
      una no aparece nada. Son de tiempo de build: cambiar una exige
      reconstruir la imagen.
- [x] **Sin errores de adquisición bajo carga CONFIRMADO**: 100 usuarios ×
      50 requests (5 000 requests) contra Postgres local, 0 errores HTTP,
      0 eventos "unable to acquire connection" en el log, p50 25.4 ms,
      p95 36.4 ms, max 48.2 ms, 2 383 req/s.
- [x] **Uso del pool medido**: `agroal_max_used_count` = 50 (el techo del pool,
      alcanzado en el pico) y `agroal_creation_count_total` = 50; en régimen
      permanente `agroal_active_count` entre 1 y 9 y `agroal_available_count`
      entre 41 y 49, es decir **uso medio del 5-18 % y pico del 100 %**. El
      "60-80 %" que pedía el SDD no se alcanza en este escenario: la carga es
      de una sola tabla y el pool no se satura. Contra Neon, con red real, la
      cifra cambia.
- [x] `agroal_blocking_time_max_milliseconds` = 49 ms: espera máxima por
      conexión, sin timeouts.

---

## Phase E: Testing & Verification

| Gate | Estado | Evidencia |
|------|--------|-----------|
| Compilación backend | ✅ | `mvnw clean verify` |
| Spotless | ✅ | `spotless:check` en verde |
| Unit tests backend | ✅ | suite completa |
| IT backend (`PostgresTestResource`) | ✅ | **296 tests, 0 fallos, 0 errores**, base limpia (`electoral_db_v3`) |
| Observabilidad del pool | ✅ | `agroal_*` en `/q/metrics` |
| gzip en vivo | ✅ | `content-encoding: gzip` |
| EXPLAIN ANALYZE | ✅ | Index Scan / Bitmap Index Scan con 50K filas |
| Load test local | ✅ parcial | 100×50 contra Postgres local; **no** se corrió el escenario de 50×10 de JMeter ni contra Neon |
| E2E flujo Observer | ⏸ | fuera de alcance por instrucción del usuario |
| CI real (push) | ⏸ | pendiente de push |

### Nota sobre la suite completa

La primera ejecución de la suite completa contra una base compartida
(`electoral_db_ci`, con datos residuales del contract suite) dio 2 fallos y 25
errores, todos por la FK `incidencia_jrv_jrv_id_fkey` al limpiar `jrv`: es
contaminación de datos entre suites, no un defecto del código. Contra una base
limpia la suite da **296/296 verde**.

---

## Phase F: Documentation

- [x] `documents/PERFORMANCE_OPTIMIZATION.md`
- [x] `documents/INDEXING_GUIDE.md`
- [x] `documents/CACHING_STRATEGY.md` (ya existía)

---

## Desviaciones del SDD

| SDD pedía | Se hizo | Motivo |
|-----------|---------|--------|
| Migraciones `011`/`012` | `048`/`049` | `011` y `012` ya están ocupados |
| Índices `ix_acta_*` | `idx_acta_*` | convención viva desde `044` |
| `idx_acta_estado` | no se creó | ya existe en `044` |
| `idx_acta_detalle_acta_id` | no se creó | redundante con `UNIQUE(acta_id, candidato_id)` |
| `quarkus.datasource.min-size` / `max-size` | `quarkus.datasource.jdbc.*` | las claves del SDD no existen en Quarkus 3.40: sin `jdbc.` el pool se quedaba en 20 y la fase D.2 no hacía nada |
| `quarkus.http.min-gzip-size` | no se puso | no existe en Quarkus 3.40; el filtro es por media type |
| EAGER → LAZY sin más | LAZY + `@BatchSize(25)` | la premisa del SDD (21 queries para 20 usuarios) es falsa: EAGER genera un join, no N+1. LAZY sin batch sí habría creado N+1 |
| `@RolesAllowed({"OBSERVER"})` | `Autorizador.exigir("acta:read")` | el mecanismo de autorización del proyecto es `Autorizador`, no `@RolesAllowed` (AGENTS.md) |
| Fase C reimplementar | no se tocó | ya estaba hecha y documentada |
| `PERFORMANCE_OPTIMIZATION.md` + `CACHING_STRATEGY.md` | + `INDEXING_GUIDE.md` | el SDD pedía los tres |
| Métricas del pool: no pedidas explícitamente | añadidas | sin ellas el gate D.2 ("pool al 60-80 %") no es observable: el export solo traía HTTP y caché |

## Brecha cerrada: consumidor frontend del endpoint batch

La brecha registrada antes (endpoint sin consumidor) quedó cerrada en `d5ce6a8`.
`observer.api.ts` expone `getCatalogoDeJrv(jrvId)`, que consume
`GET /observer/cargos-with-candidatos` en una sola petición y enriquece
candidatos con partidos. `registrar-acta.page.ts` y `modificar-acta.page.ts`
usan el catálogo por JRV en vez de pedir candidatos por cargo.

Intenté delegar la migración de páginas a `gentle-ai-worker` (3 sub-tareas
acotadas, como pidió el usuario); los workers devolvieron estado completado sin
diff. Implementé la migración inline, con specs actualizadas y suite completa
en verde.

## Lo que queda fuera

- **E2E del flujo Observer**: no se ejecutó (instrucción explícita del usuario).
- **Load test contra Neon**: las cifras de pool y latencia son contra Postgres
  local. La red es el factor dominante en producción; el 60-80 % de uso de pool
  que pide el SDD hay que medirlo con la base real.
- **JMeter con el escenario exacto de 50×10**: se usó un script propio con
  100×50, que es más exigente que el del SDD.
- **Los números de impacto del SDD (P95, throughput) son proyecciones**: lo
  medido es contra Postgres local, no contra la topología de producción.
