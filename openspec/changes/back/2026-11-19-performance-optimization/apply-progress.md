# Apply Progress — Performance Optimization: Indexing, Caching & Infrastructure

```markdown
# Apply Progress — Performance Optimization

## Estado: PENDIENTE

Esperando asignación a Builder (post-arquitecto review de D1-D5).

## Commits aplicados
(ninguno aún)

## Notas de Implementación
(a llenar durante build)

---

## Phase A: Database Indexing

### A.1: Migration 011 - Índices Tabla `acta`

- [ ] Migration SQL creada (011-add-acta-indices.sql)
- [ ] 5 índices simples creados ✓
- [ ] 1 índice compuesto creado ✓
- [ ] Testing local: 6 índices visibles en pg_indexes
- [ ] No errores en BD

**Commits:** 
- TBD

---

### A.2: Migration 012 - Índices Tabla `acta_detalle`

- [ ] Migration SQL creada (012-add-acta-detalle-indices.sql)
- [ ] 2 índices creados ✓
- [ ] Testing local: índices visibles
- [ ] No errores

**Commits:**
- TBD

---

### A.3: Verification - EXPLAIN ANALYZE

- [ ] `EXPLAIN ANALYZE` verificado para:
  - [ ] `SELECT * FROM acta WHERE registrada_por = ?`
        Result: "Index Scan using ix_acta_registrada_por" ✓
  - [ ] `SELECT * FROM acta WHERE estado = ?`
        Result: "Index Scan using ix_acta_estado" ✓
  - [ ] `SELECT * FROM acta WHERE jrv_id = ? AND cargo_id = ?`
        Result: "Index Scan using ix_acta_jrv_cargo_estado" ✓
  - [ ] Dashboard JOIN query
        Result: "Index Scan using ix_acta_detalle_acta_id" ✓

**Result:** ✓ / ✗

---

## Phase B: Backend ORM Optimization

### B.1: Cambiar Eager → Lazy Loading

- [ ] Usuario.java modificado (línea 50)
  - From: `fetch = FetchType.EAGER`
  - To: `fetch = FetchType.LAZY`
- [ ] Código compilado sin errores
- [ ] Tests verifican que NO causa LazyInitializationException ✓

**Commits:**
- TBD

---

### B.2: Crear Batch Endpoint - Cargos with Candidatos

- [ ] ObserverResource.java modificado
  - Nuevo método: `getCargosWithCandidatos(@QueryParam jrvId)`
  - Usa LEFT JOIN FETCH para candidatos
  - Retorna DTOs: CargosConCandidatosResponse + CargoWithCandidatos
- [ ] DTOs creados ✓
- [ ] Tests: Comparar respuesta vs 3 endpoints (mismo data, 1 request)
- [ ] Integration test pasa ✓

**Commits:**
- TBD

---

## Phase C: Frontend Caching

### C.1: Caché Partidos (observer.api.ts)

- [ ] Field `partidosCache$` agregado
- [ ] Método `getCandidatosPorCargo()` modificado
  - Usa `shareReplay(1)` para caché
  - Primera llamada: HTTP request
  - Siguientes llamadas: Cache reusado
- [ ] Tests verifican:
  - [ ] 1er call → HTTP request
  - [ ] 2do call → Cache hit (1ms)
  - [ ] Solo 1 HTTP request para partidos en ambas

**Tests Passing:** 0/3

**Commits:**
- TBD

---

### C.2: Caché Dashboard (dashboard.api.ts)

- [ ] Fields agregados: resultadosCache$, progresoCache$, resumenCache$, novedadesCache$
- [ ] Métodos modificados:
  - [ ] `getResultados()` → `shareReplay(1)`
  - [ ] `getProgreso()` → `shareReplay(1)`
  - [ ] `getResumen()` → `shareReplay(1)`
  - [ ] `getNovedades()` → `shareReplay(1)`
- [ ] Tests verifican caché entre tabs ✓

**Tests Passing:** 0/4

**Commits:**
- TBD

---

## Phase D: Infrastructure Configuration

### D.1: HTTP Compression (gzip)

- [ ] application.properties modificado
  - `quarkus.http.enable-compression=true`
  - `quarkus.http.compression-level=6`
  - `quarkus.http.min-gzip-size=1024`
- [ ] Testing: Response headers muestran `content-encoding: gzip` ✓
- [ ] Respuestas grandes comprimidas (500 KB → 50 KB) ✓

**Commits:**
- TBD

---

### D.2: Connection Pool Configuration

- [ ] application.properties modificado
  - `quarkus.datasource.min-size=10`
  - `quarkus.datasource.max-size=50`
  - `quarkus.datasource.jdbc.acquisition-timeout=30s`
  - `quarkus.datasource.jdbc.idle-removal-interval=5m`
  - `quarkus.datasource.jdbc.max-lifetime=30m`
- [ ] Testing: Logs NO muestran "unable to acquire connection" errors ✓
- [ ] Pool size monitoreable en logs ✓

**Commits:**
- TBD

---

## Phase E: Testing & Verification

### E.1: Backend Unit Tests

- [ ] `UsuarioRepositoryTest.testLazyLoadingNoExtraQueries()` ✓
  - Verifica Lazy loading dispara 1 bulk query (not N)
- [ ] `ActaRepositoryTest.testActaQueriesUseIndices()` ✓
  - Verifica queries usan índices (not seq scan)
- [ ] `BatchEndpointTest.testCargosWithCandidatos()` ✓
  - Verifica endpoint retorna estructura correcta

**Tests Passing:** 0/3

---

### E.2: Frontend Unit Tests

- [ ] `ObserverApiTest.testCachePpartidosAndReuse()` ✓
- [ ] `DashboardApiTest.testCacheResultadosAcrossMultipleCalls()` ✓

**Tests Passing:** 0/2

---

### E.3: Integration Tests

- [ ] Load test 50 usuarios simultáneos, 10 requests cada uno
  - Expected P95: < 2s
  - Expected P99: < 3s
  - Expected timeouts: 0
  - Result: TBD

- [ ] E2E: Observer complete flow
  - Login: TBD (target < 2s)
  - Ver mis-jrvs: TBD (target < 1s)
  - Registrar acta (5 cargos): TBD (target < 3s)

**Result:** ✓ / ✗

---

### E.4: Verificación SQL

- [ ] EXPLAIN ANALYZE ejecutado para todas las queries críticas
  - Todas muestran "Index Scan" (not Seq Scan) ✓

**Result:** ✓ / ✗

---

## Phase F: Documentation

- [ ] `PERFORMANCE_OPTIMIZATION.md` creado ✓
  - Cambios realizados
  - Matemática del problema
  - Impacto esperado
  - Monitores

- [ ] `INDEXING_GUIDE.md` creado ✓
  - Cuándo crear índices
  - EXPLAIN ANALYZE guide
  - Naming conventions

- [ ] `CACHING_STRATEGY.md` creado ✓
  - Patrón RxJS shareReplay(1)
  - Cuándo usar
  - Invalidación manual

**Commits:**
- TBD

---

## Performance Impact Summary

### Métricas Antes vs Después

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| **Latencia login** | 2.73s | <1s | 63% ↓ |
| **Latencia listar actas** | 1.5-2.7s | <0.5s | 70% ↓ |
| **Latencia dashboard** | 3-5s | 0.5-1s | 80% ↓ |
| **Requests (cargos form)** | 5 | 2-3 | 50% ↓ |
| **Banda ancha (gzip)** | 500 KB | 50 KB | 90% ↓ |
| **Pool exhaustion** | Sí (20 conn) | No (50 conn) | ✓ |
| **P95 latencia (50 users)** | 10-15s | 1.5-2s | 87% ↓ |
| **Throughput** | ~100 req/s | ~1000 req/s | 10× ↑ |

---

## Regressions & Verification

| Feature | Antes | Después | Status |
|---------|-------|---------|--------|
| Observer login flow | ✓ | ✓ | PASS |
| Observer registrar acta | ✓ | ✓ | PASS |
| Admin listar usuarios | ✓ | ✓ | PASS |
| Dashboard | ✓ | ✓ | PASS |
| Tabs switching | ✓ | ✓ (cached) | PASS |
| Lazy loading usuarios | N/A | ✓ | NEW |
| Índices en BD | N/A | ✓ (8 total) | NEW |
| Gzip compression | N/A | ✓ | NEW |

**Result:** ✓ / ✗

---

## Post-Implementation Monitoring

### Métricas a Monitorear en Producción

| Métrica | Target | Crítico Si |
|---------|--------|-----------|
| Avg query time | <100ms | >500ms |
| P95 latencia | <2s | >3s |
| P99 latencia | <3s | >5s |
| Pool usage | 40-60% | >80% (aumento a 100) |
| Active connections | <50 | >60 (bottleneck) |
| Response size (gzipped) | <100 KB | >500 KB (check gzip) |
| Requests per sec | >500 | <100 (degradation) |
| Cache hit ratio | >90% | <70% (invalidate) |

---

## Sign-Off

**Builder:** _______________  
**Date:** _______________  

**QA:** _______________  
**Date:** _______________  

**Ready for Merge:** [ ] Yes [ ] No

**Merge Commit:** TBD

---

## Post-Merge Next Steps (Phase 2)

- [ ] Monitor production metrics for 1 week
- [ ] Adjust pool size if needed (target: 60-80% usage)
- [ ] Collect feedback on performance improvement
- [ ] Consider WebSocket for real-time notifications (future)
- [ ] Consider additional caching: Redis (optional)

---

## Notes & Issues

(a llenar durante implementación)

```
