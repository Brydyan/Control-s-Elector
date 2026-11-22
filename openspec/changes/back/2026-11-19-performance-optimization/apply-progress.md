# Apply Progress — Performance Optimization

## Estado: IMPLEMENTADO — verificación de integración pendiente

El código de las fases A, B, D y F está aplicado y compila. La fase C ya estaba
implementada antes de esta sesión. Los gates que necesitan PostgreSQL (fase E)
no se pudieron ejecutar en el entorno de desarrollo: no hay Docker ni base local.
No se declaran en verde.

## Commits aplicados

| Fase | Commit | Mensaje |
|------|--------|---------|
| A | `063138e` | `perf(db): add missing acta and acta_detalle indexes` |
| B.1 | `c395d69` | `perf(auth): load usuario.rol lazily with batch size` |
| B.2 | `25527f0` | `perf(observer): add batch endpoint for JRV cargos with candidatos` |
| D | `6fe24ac` | `perf(infra): enable gzip and raise the datasource pool` |
| F | TBD | docs: performance optimization and indexing guide |

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
- [ ] Testing local: índices visibles en `pg_indexes` — **bloqueado, sin BD**

### A.2: Índices en `acta_detalle`

- [x] Migración creada: `database/changelog/049-add-acta-detalle-indices.sql`
- [x] `idx_acta_detalle_candidato_id`
- [x] Registrada en `db.changelog-master.yaml`
- [ ] Testing local — **bloqueado, sin BD**

### A.3: EXPLAIN ANALYZE

- [ ] Verificado — **bloqueado, sin BD**. Las queries y el resultado esperado
      quedan documentados en `documents/INDEXING_GUIDE.md` para correrlos en CI
      o en una base real.

---

## Phase B: Backend ORM Optimization

### B.1: EAGER → LAZY

- [x] `Usuario.rol`: `FetchType.LAZY` + `@BatchSize(size = 25)`
- [x] Compila (`mvnw -DskipTests test-compile`)
- [x] Consumidores de `getRolNombre()` verificados uno por uno: todos corren
      dentro de una transacción (`LoginUseCase`, `RefreshTokenUseCase`,
      `ActivarUsuarioPorTokenUseCase`, `InvitacionService`,
      `UserInvitationService`, `UsuarioAdminService.listar`), así que no hay
      `LazyInitializationException`
- [x] `UsuarioMapperTest`: 8/8 en verde
- [ ] Test de conteo de queries — **bloqueado, sin BD**

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
- [ ] IT ejecutado — **bloqueado, sin BD**

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
- [x] `quarkus.http.min-gzip-size=1024`
- [ ] Header `content-encoding: gzip` verificado — **bloqueado, requiere app viva**

### D.2: Pool

- [x] `min-size=${DB_POOL_MIN_SIZE:10}`
- [x] `max-size=${DB_POOL_MAX_SIZE:50}`
- [x] `acquisition-timeout=${DB_POOL_ACQUISITION_TIMEOUT:30s}`
- [x] `idle-removal-interval=5m`, `max-lifetime=30m`
- [ ] Sin errores de adquisición bajo carga — **bloqueado, requiere load test**

---

## Phase E: Testing & Verification

| Gate | Estado |
|------|--------|
| Compilación backend | ✅ |
| Spotless | ✅ |
| Unit tests backend (muestra) | ✅ 8/8 |
| Suite frontend | ✅ 1153 + 5 |
| IT backend (`PostgresTestResource`) | ⏳ sin Docker/PostgreSQL |
| Load test 50 usuarios | ⏳ sin backend vivo |
| EXPLAIN ANALYZE | ⏳ sin BD migrada |

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
| EAGER → LAZY sin más | LAZY + `@BatchSize(25)` | la premisa del SDD (21 queries para 20 usuarios) es falsa: EAGER genera un join, no N+1. LAZY sin batch sí habría creado N+1 |
| Fase C reimplementar | no se tocó | ya estaba hecha y documentada |
| `PERFORMANCE_OPTIMIZATION.md` + `CACHING_STRATEGY.md` | + `INDEXING_GUIDE.md` | el SDD pedía los tres |

## Bloqueos

- **Sin Docker ni PostgreSQL local**: los IT con `PostgresTestResource` no
  corren. En CI o con `-Dtest.postgres.url=...` sí.
- **Load test (E.3)**: requiere backend desplegado.
- Los números de impacto del SDD (P95, throughput) son proyecciones, no
  mediciones: no se midió nada en esta sesión.
