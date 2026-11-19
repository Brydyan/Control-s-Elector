# Apply Progress — Caching Strategy: Frontend + Backend Optimization

```markdown
# Apply Progress — Caching Strategy

## Estado: PENDIENTE

Esperando asignación a Builder (post-arquitecto review de D1-D5).

## Commits aplicados
(ninguno aún)

## Notas de Implementación
(a llenar durante build)

---

## A. Frontend: Observer Partidos Cache (P1)

### A.1: Implementar Caché en observer.api.ts

- [ ] Field `partidosCache$` agregado
- [ ] Método `getCandidatosPorCargo()` modificado
  - Usa `shareReplay(1)` para caché
  - Primera llamada: HTTP request
  - Siguientes llamadas: Cache reusado
- [ ] Compilación sin errores

**Commits:** 
- TBD

---

### A.2: Testing - Partidos Cache

- [ ] Test creado: `ObserverApiService.test.ts`
- [ ] 1er call → HTTP request
- [ ] 2do call → Cache hit (1ms)
- [ ] Solo 1 HTTP request para partidos en ambas

**Tests Passing:** 0/1

**Commits:**
- TBD

---

## B. Frontend: Dashboard Cache Activation (P3)

### B.1: Activar Cache en dashboard.api.ts

- [ ] Fields agregados: `resultadosCache$`, `progresoCache$`, `novedadesCache$`, `resumenCache$`
- [ ] Métodos modificados:
  - [ ] `getResultados()` → `shareReplay(1)`
  - [ ] `getProgreso()` → `shareReplay(1)`
  - [ ] `getNovedades()` → `shareReplay(1)`
  - [ ] `getResumen()` → `shareReplay(1)`
- [ ] Compilación sin errores

**Commits:**
- TBD

---

### B.2: Testing - Dashboard Cache

- [ ] Test creado: `DashboardApiService.test.ts`
- [ ] Tests verifican caché entre tabs ✓
- [ ] 4 métodos, 1 HTTP cada uno

**Tests Passing:** 0/4

**Commits:**
- TBD

---

## C. Backend: Redis Configuration (P2)

### C.1: Configurar Redis en application.properties

- [ ] application.properties modificado
  - `quarkus.redis.hosts=localhost:6379`
  - `quarkus.cache.type=redis`
  - `quarkus.cache.redis.expire-after-write=10m`
- [ ] Testing local: Redis running (`redis-cli ping` → PONG)
- [ ] Compilación sin errores

**Commits:**
- TBD

---

## D. Backend: Service-level Caching (P2)

### D.1: Agregar @Cacheable a Servicios

- [ ] CargoService.findAll() → `@Cacheable("cargos")`
- [ ] PartidoService.findAll() → `@Cacheable("partidos")`
- [ ] CandidatoService.findAll() → `@Cacheable("candidatos")`
- [ ] ProvinciaService.findAll() → `@Cacheable("provincias")`
- [ ] CantonService.findAll() → `@Cacheable("cantones")`
- [ ] ParroquiaService.findAll() → `@Cacheable("parroquias")`
- [ ] Compilación sin errores

**Commits:**
- TBD

---

### D.2: Testing - Backend Cache

- [ ] `CargoServiceCachingTest.java`: 2 calls → 1 BD query
- [ ] `PartidoServiceCachingTest.java`: 2 calls → 1 BD query
- [ ] `CandidatoServiceCachingTest.java`: 2 calls → 1 BD query
- [ ] GeographyServicesCachingTest: Provincia/Canton/Parroquia (3)

**Tests Passing:** 0/6

**Commits:**
- TBD

---

## E. Admin: Cache Invalidation Endpoint (P2)

### E.1: Agregar /admin/cache/clear

- [ ] AdminResource.java modificado
- [ ] Nuevo método: `clearCache(@QueryParam("cacheName") String cacheName)`
- [ ] Solo ADMIN puede acceder (@RolesAllowed)
- [ ] Compilación sin errores

**Commits:**
- TBD

---

### E.2: Testing - Cache Clear

- [ ] AdminResourceTest.java: Endpoint accesible
- [ ] Caches se vacían post-call (verificar via redis-cli)
- [ ] Solo ADMIN puede acceder

**Tests Passing:** 0/1

**Commits:**
- TBD

---

## F. Integration Tests & Verification (P1)

### F.1: E2E - Observer Form

- [ ] Observer load form con 5 cargos
  - Expected: 1 + 5 HTTP = 6 (before: 11)
  - Expected latency: < 1s (before: 2-3s)
- [ ] Test pasa

**Result:** ✓ / ✗

---

### F.2: E2E - Dashboard Tabs

- [ ] Dashboard load, cambiar entre 4 tabs
  - Expected: 4 HTTP (first time) + cache hits
  - Expected latency: < 0.5s (before: 4-5s)
- [ ] Test pasa

**Result:** ✓ / ✗

---

### F.3: Load Test - 100 Usuarios Simultáneos

- [ ] Setup: 100 threads, 10 requests cada uno
  - Expected P95: < 2s
  - Expected hit ratio: > 90%
  - Expected timeouts: 0
  - Result: TBD

**Result:** ✓ / ✗

---

### F.4: Verificación - Redis Monitoring

- [ ] redis-cli: INFO stats muestra hit/miss counts
- [ ] redis-cli: MEMORY USAGE < 100MB
- [ ] Redis keys present (cargos, partidos, candidatos, geografía)

**Result:** ✓ / ✗

---

### F.5: Regresión Testing

| Feature | Antes | Después | Status |
|---------|-------|---------|--------|
| Observer form | ✓ | ✓ | PASS |
| Dashboard tabs | ✓ | ✓ | PASS |
| Admin cache clear | N/A | ✓ | NEW |
| Cargo list | ✓ | ✓ (cached) | PASS |
| Partido list | ✓ | ✓ (cached) | PASS |

**Result:** ✓ / ✗

---

## G. Documentation (P2)

- [ ] `CACHING_STRATEGY.md` creado ✓
  - Patrón RxJS shareReplay(1)
  - Cuándo usar
  - Invalidación manual

- [ ] `REDIS_SETUP_GUIDE.md` creado ✓
  - Setup local
  - Setup producción
  - Monitoring

- [ ] `CACHE_INVALIDATION.md` creado ✓
  - Manual clear endpoint
  - TTL auto-invalidation
  - Fase 2: Event-driven

**Commits:**
- TBD

---

## Performance Impact Summary

### Métricas Antes vs Después

| Métrica | Antes | Después | Mejora |
|---------|-------|---------|--------|
| **Latencia observer form** | 2-3s | <1s | 66% ↓ |
| **Latencia dashboard tabs** | 4-5s | 0.5-1s | 80% ↓ |
| **Requests (cargos form)** | 11 | 6 | 45% ↓ |
| **BD queries (100 users)** | 3,300/min | 11/min | 99.6% ↓ |
| **P95 latencia** | 2.5s | 1.5s | 40% ↓ |
| **Cache hit ratio** | N/A | > 90% | ✓ |
| **Redis memory** | N/A | < 100MB | ✓ |

---

## Regressions & Verification

| Feature | Antes | Después | Status |
|---------|-------|---------|--------|
| Observer login flow | ✓ | ✓ | PASS |
| Observer registrar acta | ✓ | ✓ | PASS |
| Dashboard load | ✓ | ✓ | PASS |
| Admin cache clear | N/A | ✓ | NEW |
| Cache hits | N/A | ✓ | NEW |
| Redis stable | N/A | ✓ | NEW |

**Result:** ✓ / ✗

---

## Post-Implementation Monitoring

### Métricas a Monitorear en Producción

| Métrica | Target | Crítico Si |
|---------|--------|-----------|
| Cache hit ratio | > 90% | < 70% (check logic) |
| Redis memory | < 100MB | > 500MB (scale up) |
| P95 latencia | < 2s | > 3s (bottleneck) |
| BD query time avg | < 100ms | > 500ms (indexing) |
| Observer form latency | < 1s | > 2s (cache miss) |
| Dashboard latency | < 0.5s | > 1s (cache miss) |

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
- [ ] Adjust TTL if needed (target: 10 min default)
- [ ] Collect feedback on performance improvement
- [ ] Consider event-driven invalidation (future)
- [ ] Consider cache warmer on startup (optional)

---

## Notes & Issues

(a llenar durante implementación)

```
