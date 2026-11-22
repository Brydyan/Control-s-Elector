# Apply Progress — Performance Optimization

## Estado: IMPLEMENTADO — verificado, con E2E ejecutado

El código de las fases A, B, D y F está aplicado y compila. La fase C ya estaba
implementada antes de esta sesión. Los gates que necesitan PostgreSQL se
ejecutaron contra un Postgres local en Docker (Postgres 16), no contra Neon.

**El E2E del flujo Observer sí se ejecutó** (contra la API real, 3 proyectos de
navegador, 53 tests en verde). Lo que sigue pendiente es solo medición contra
Neon, que es post-deploy por definición.

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
| Gates | `5935684` | test(perf): afirmar en el catalogo los indices de acta y acta_detalle |
| Docs | `40f6f29` | docs(perf): marcar el estado real de las tareas del SDD de performance |
| Docs | `65f891a` | docs(perf): agregar el reporte de verificacion del SDD de performance |

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

#### Criterio de D.2 redefinido para la topología real

El criterio original del SDD ("pool al 60-80 % de ocupación") **no es observable**
en producción: Neon pone un PgBouncer (`pool_mode=transaction`) delante de cada
compute, que multiplexa las conexiones del cliente sobre pocas conexiones
reales. Ese porcentaje es de PgBouncer, no del backend.

El criterio que aplica y que sí se puede medir:

| # | Criterio | Estado |
|---|----------|--------|
| D.2-a | 0 conexiones esperando (`agroal_active_count` < `max-size`, blocking time estable) | ✅ medido local |
| D.2-b | 0 errores de adquisición ("unable to acquire connection") | ✅ medido local (5000 requests) |
| D.2-c | Pool cliente chico respecto de los slots del compute | ⏸ post-deploy (requiere Neon) |
| D.2-d | 0 errores de conexión tras el cold start de Neon | ⏸ post-deploy (requiere Neon) |

D.2-a y D.2-b quedaron CONFIRMADOS contra Postgres local (sin PgBouncer).
D.2-c y D.2-d exigen Neon y quedan PENDIENTES: son la medición fina que decide
si el pool calibrado en D.2 sirve en producción. La medición fina no se simula:
se hace con el compute real.

> Decisión del humano: **redefinir el criterio**, no simular la medición.

#### Riesgo abierto: pool sin calibrar para Neon

El pool **se deja como está** (no se recalibra a ciegas sin medir contra Neon) y
el riesgo queda documentado en `tasks.md` ("Riesgo abierto: pool sin calibrar").
Resumen de los tres riesgos:

1. **`max-size=50` es innecesario con PgBouncer delante**: el límite real lo pone
   `default_pool_size` de PgBouncer (`0.9 × max_connections` del compute), no el
   pool del backend. 50 sockets no compran concurrencia; compran slots del
   compute.
2. **`min-size=10` + `idle-removal-interval=5m` chocan con la suspensión de
   Neon** (~5 min de inactividad en los planes Free/Launch): las conexiones se
   cierran justo en la ventana en que el compute se duerme, y el síntoma son
   errores transitorios de conexión tras el cold start, no saturación del pool.
3. **La reanudación puede findar slots del compute**: 0.25 CU expone
   `max_connections=112`; con `min-size=10` el backend retiene slots
   permanentemente.

Compatibilidad del backend con PgBouncer **verificada**: el RLS usa
`set_config('app.current_user_id', ..., true)` (= `SET LOCAL`, soportado en
transaction mode) y el código no usa advisory locks, `LISTEN`/`NOTIFY`, tablas
temporales ni `SET` de sesión.

---

## Phase E: Testing & Verification

| Gate | Estado | Evidencia |
|------|--------|-----------|
| Compilación backend | ✅ | `mvnw clean verify` |
| Spotless | ✅ | `spotless:check` en verde |
| Unit tests backend | ✅ | suite completa |
| IT backend (`PostgresTestResource`) | ✅ | **298 tests, 0 fallos, 0 errores**, base limpia (`electoral_db_v4`) |
| Observabilidad del pool | ✅ | `agroal_*` en `/q/metrics` |
| gzip en vivo | ✅ | `content-encoding: gzip` |
| EXPLAIN ANALYZE | ✅ | Index Scan / Bitmap Index Scan con 50K filas |
| Load test local | ✅ parcial | 100×50 contra Postgres local; **no** se corrió el escenario de 50×10 de JMeter ni contra Neon |
| E2E flujo Observer | ✅ | **53 tests en verde** contra la API real, 3 proyectos de navegador |
| CI real (push) | ✅ | 9/9 jobs verdes en `5935684` |

### E2E del flujo Observer: cómo se ejecutó

El montaje (lo que faltaba):

| Pieza | Cómo |
|---|---|
| Postgres 16 + Redis 7.4 + RustFS | `docker compose --profile dev --profile s3 up -d` |
| Rol `electoral_app` | `docker compose --profile dev run --rm db-app-role` — sin superusuario ni BYPASSRLS, para que el RLS sea real |
| Migraciones | jar local con `APP_MIGRATE_ONLY=true` y contextos `seed,bootstrap-admin`, corriendo como owner |
| Backend | `java -jar target/quarkus-app/quarkus-run.jar` en :8080, con `DB_USER=electoral_app` |
| Frontend | `pnpm start` en :4200 |

**Resultado:** `E2E_REQUIRE_BACKEND=1 pnpm test:e2e --grep "@guardian"` →
**53 passed, 0 failed, 4 skipped** (Desktop Chrome, Mobile Chrome Pixel 7,
Mobile Safari iPhone 12).

Dos cosas que la corrida destapó y que quedaron resueltas:

1. **El rate limit de `observer-jrvs` (60/min por usuario) no aguanta la suite
   completa.** La primera corrida dio 18 fallos, todos "jrvs is not iterable":
   era el 429 deserializado como si fuera el array. El defecto era el
   diagnóstico, no el rate limit: ahora el spec valida el status de la respuesta
   y explica el 429. Para la corrida, el backend va con
   `APP_RATELIMIT_OBSERVER_JRVS_LIMIT=100000` (variable de entorno ya
   soportada).
2. **WebKit no manda cookies `Secure` por `http://`.** La cookie de refresh se
   emite con `Secure`; Chromium la acepta en `http://localhost` (contexto
   confiable) y WebKit no. Se resolvió en el entorno con un proxy TLS local
   (certificado autofirmado) para replicar la condición de producción, donde
   el backend ya está detrás de https. Sin tocar el código del producto.

### Nota sobre la suite completa

La primera ejecución de la suite completa contra una base compartida
(`electoral_db_ci`, con datos residuales del contract suite) dio 2 fallos y 25
errores, todos por la FK `incidencia_jrv_jrv_id_fkey` al limpiar `jrv`: es
contaminación de datos entre suites, no un defecto del código. Contra una base
limpia la suite da **298/298 verde**.

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

- **Medición del pool contra Neon**: es lo único que queda. El criterio de D.2
  se redefinió a lo que sí es observable con PgBouncer delante (0 conexiones
  esperando, 0 errores de adquisición, pool cliente chico, 0 errores tras el
  cold start). Los dos primeros están medidos en local; los dos últimos son
  post-deploy, porque PgBouncer y la suspensión del compute solo existen en
  Neon.
- **Recalibración del pool**: por decisión del humano, el pool se deja como está
  y el riesgo queda documentado en `tasks.md` ("Riesgo abierto: pool sin
  calibrar"), con los valores recomendados y el procedimiento para decidir con
  dato.
- **Load test contra Neon**: las cifras de pool y latencia son contra Postgres
  local. La red es el factor dominante en producción.
- **JMeter con el escenario exacto de 50×10**: se usó un script propio con
  100×50, que es más exigente que el del SDD.
- **Los números de impacto del SDD (P95, throughput) son proyecciones**: lo
  medido es contra Postgres local, no contra la topología de producción.
