# Design — Caching Strategy: Frontend + Backend Optimization

---

## A. Frontend Caching: Observer Partidos

**Archivo:** `/frontend/app/src/app/features/observer/data/observer.api.ts`

**Comportamiento Actual:**
```typescript
getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
  return forkJoin({
    candidatos: this.http.get<CandidatoDto[]>(
      `${this.configUrl}/candidatos?cargoId=${cargoId}`,
    ),
    partidos: this.http.get<PartidoDto[]>(`${this.configUrl}/partidos`),  // ← No cache
  })
}
```

**Problema:**
- Formulario con 5 cargos: 5 calls a esta función → 5 HTTP GET /partidos
- Cada GET: 565ms
- Total: 2.8 segundos desperdiciados

**Solución:**
```typescript
private partidosCache$: Observable<PartidoDto[]> | null = null;

getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
  // Lazy-init cache
  if (!this.partidosCache$) {
    this.partidosCache$ = this.http.get<PartidoDto[]>(
      `${this.configUrl}/partidos`
    ).pipe(shareReplay(1));  // ← Cache: 1st call fetches, rest reuse
  }
  
  return forkJoin({
    candidatos: this.http.get<CandidatoDto[]>(
      `${this.configUrl}/candidatos?cargoId=${cargoId}`,
    ),
    partidos: this.partidosCache$,  // ← Reuse cached observable
  })
}
```

**Comportamiento Post:**
- 1st call (cargo 1): HTTP request (565ms) → cache created
- 2nd call (cargo 2): Cache hit (1ms)
- 3rd call (cargo 3): Cache hit (1ms)
- ...
- Total para 5 cargos: 565ms (antes: 2.8s) → **80% reduction**

---

## B. Frontend Caching: Dashboard Activation

**Archivo:** `/frontend/app/src/app/features/dashboard/data/dashboard.api.ts`

**Comportamiento Actual (líneas 26-30):**
```typescript
private resultadosDetalles$ = this.http.get<ResultadoCargo[]>(
  `${this.baseUrl}/resultados-detalle`
);
```

Cache está definida pero **nunca se usa** en los métodos públicos:

```typescript
// PROBLEMA: No usa la cache
getResultados(): Observable<ResultadoCargo[]> {
  return this.http.get<ResultadoCargo[]>(`${this.baseUrl}/resultados`);
}

getProgreso(): Observable<ProgresoDto[]> {
  return this.http.get<ProgresoDto[]>(`${this.baseUrl}/progreso`);
}
```

**Solución:**
```typescript
private resultadosCache$: Observable<ResultadoCargo[]> | null = null;
private progresoCache$: Observable<ProgresoDto[]> | null = null;
private novedadesCache$: Observable<NovedadDto[]> | null = null;
private resumenCache$: Observable<ResumenDto> | null = null;

getResultados(): Observable<ResultadoCargo[]> {
  if (!this.resultadosCache$) {
    this.resultadosCache$ = this.http.get<ResultadoCargo[]>(
      `${this.baseUrl}/resultados`
    ).pipe(shareReplay(1));
  }
  return this.resultadosCache$;
}

getProgreso(): Observable<ProgresoDto[]> {
  if (!this.progresoCache$) {
    this.progresoCache$ = this.http.get<ProgresoDto[]>(
      `${this.baseUrl}/progreso`
    ).pipe(shareReplay(1));
  }
  return this.progresoCache$;
}

// Repetir para getNovedades() y getResumen()
```

**Comportamiento (Tabs del Dashboard):**

```
ANTES:
  Click Resumen → GET /resumen → HTTP (1.17s)
  Click Resultados → GET /resultados → HTTP (1.69s)
  Click Progreso → GET /progreso → HTTP (2.03s)
  Click Novedades → GET /novedades → HTTP (1.37s)
  Click Resumen again → GET /resumen → HTTP (1.17s) ← DUPLICATE
  Total: 5 requests × ~1.5s = 7.4s

DESPUÉS:
  Click Resumen → GET /resumen → HTTP (1.17s) [cache created]
  Click Resultados → GET /resultados → HTTP (1.69s) [cache created]
  Click Progreso → GET /progreso → HTTP (2.03s) [cache created]
  Click Novedades → GET /novedades → HTTP (1.37s) [cache created]
  Click Resumen again → Cache (1ms) [reuse]
  Total: 4 requests + 1 cache hit = 6.26s → 5.26s (savings: 2.1s)
```

---

## C. Backend Caching: Redis Architecture

> **⚠️ Sección C describe la opción NO implementada.** La implementación real usa
> **Caffeine en memoria** (`quarkus-cache-caffeine`), no Redis. Lo que sigue queda como
> referencia del diseño original y **no describe el código vigente**. La decisión
> Redis-vs-Caffeine (D2) está **PENDIENTE de decisión humana**: ver
> "Decision D2 pendiente" en `apply-progress.md`. La sección C.3 (impacto en consultas)
> sí se midió con la implementación real, pero con la métrica reproducible de
> "consultas de configuración" (ver `documents/load-test/`).

### C.1 Configuration

**Archivo:** `/backend/electoral-api-quarkus/src/main/resources/application.properties`

```properties
# Redis Caching Setup
quarkus.redis.hosts=localhost:6379
quarkus.cache.type=redis
quarkus.cache.redis.expire-after-write=10m

# Optional: Redis cluster (production)
# quarkus.redis.cluster-hosts=redis-node1:6379,redis-node2:6379,redis-node3:6379

# Optional: Redis sentinel (HA)
# quarkus.redis.sentinel-master-name=mymaster
```

### C.2 Service-level Caching

**Archivo:** `/backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/configuracion/application/CargoService.java`

```java
@ApplicationScoped
public class CargoService {
  
  @Inject
  CargoRepository repository;
  
  // Cache para findAll: 10 minutos (datos estáticos)
  @Cacheable(cacheName = "cargos")
  public List<CargoDto> findAll() {
    return repository.findAll()
        .stream()
        .map(this::toDto)
        .collect(toList());
  }
  
  // NO cachear: findById (puede cambiar)
  public CargoDto findById(Long id) {
    return repository.findById(id)
        .map(this::toDto)
        .orElseThrow(() -> new NotFoundException("Cargo not found"));
  }
}
```

**Archivos a modificar (mismo patrón):**

| Clase | Método | Cache Name | TTL | Razón |
|-------|--------|-----------|-----|-------|
| `CargoService` | `findAll()` | `cargos` | 10m | Estático, cambia raro |
| `PartidoService` | `findAll()` | `partidos` | 10m | Estático, nunca cambia |
| `CandidatoService` | `findAll()`, `findByCargoId(id)` | `candidatos` | 10m | Estático por ciclo electoral |
| `ProvinciaService` | `findAll()` | `provincias` | 1h | Estático, geográfico |
| `CantonService` | `findAll()`, `findByProvinciaId(id)` | `cantones` | 1h | Estático, geográfico |
| `ParroquiaService` | `findAll()`, `findByCantonId(id)` | `parroquias` | 1h | Estático, geográfico |

### C.3 Query Impact

**Before Redis:**
```
Observer form load (5 cargos):
  GET /cargos?jrvId=1 → CargoService.findByJrv() → Repository query
  GET /candidatos → CandidatoService.findAll() → Repository query × 5
  GET /partidos → PartidoService.findAll() → Repository query × 5
  Total: 11 BD queries

Backend under 100 users load:
  Cada usuario hace 2-3 formularios
  100 × 3 × 11 = 3,300 queries/min
```

**After Redis (hit ratio > 90%):**
```
Observer form load (5 cargos):
  GET /cargos?jrvId=1 → Cache miss (1st time) → 1 BD query
  GET /candidatos → Cache miss (1st time) → 1 BD query
  GET /partidos → Cache miss (1st time) → 1 BD query
  Total: 3 BD queries (first user)
  
  User 2 onwards:
  All cargos/candidatos/partidos from Redis (0 BD queries)

Backend under 100 users load:
  First user: 11 queries
  Users 2-100: 0 queries (cache hits)
  Total: 11 queries/min (instead of 3,300)
  Savings: 99.6%
```

---

## D. Infrastructure: Invalidation & Monitoring

### D.1 Manual Invalidation Endpoint

**Archivo:** `/backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/admin/AdminResource.java`

```java
@GET
@Path("/cache/clear")
@RolesAllowed({"ADMIN"})
public Response clearCache(@QueryParam("cacheName") String cacheName) {
  if (cacheName == null || cacheName.isEmpty()) {
    // Clear all caches
    cacheManager.getCacheNames().forEach(cacheManager::getCache);
    return Response.ok("All caches cleared").build();
  } else {
    // Clear specific cache
    cacheManager.getCache(cacheName).ifPresent(Cache::invalidateAll);
    return Response.ok("Cache '" + cacheName + "' cleared").build();
  }
}
```

### D.2 Monitoring

**Métricas a exponer (via Micrometer + Prometheus):**

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true

# Metrics exposed:
# - cache_entries_total (by cache name)
# - cache_hits_total (by cache name)
# - cache_misses_total (by cache name)
# - redis_memory_used_bytes
```

**Alertas recomendadas:**
- Redis memory > 500MB → scale up
- Cache hit ratio < 70% → check invalidation logic
- TTL expiration lag > 1s → check Redis performance

---

## E. Testing Strategy

### E.1 Frontend Unit Tests (Observer Partidos Cache)

```typescript
describe('ObserverApiService - Partidos Cache', () => {
  
  it('should cache partidos and reuse on second call', (done) => {
    service.getCandidatosPorCargo(1).subscribe(() => {
      // 1st HTTP request fired
      
      service.getCandidatosPorCargo(2).subscribe(() => {
        // Should NOT fire new HTTP for partidos
        const matches = httpMock.match(url => url.includes('/partidos'));
        expect(matches.length).toBe(1);  // Only 1 HTTP request
        done();
      });
    });
  });
  
  it('should return same data on cache hit', (done) => {
    let firstData: PartidoDto[];
    
    service.getCandidatosPorCargo(1).subscribe((data1) => {
      firstData = data1.partidos;
      
      service.getCandidatosPorCargo(2).subscribe((data2) => {
        expect(data2.partidos).toEqual(firstData);  // Same reference or value
        done();
      });
    });
  });
});
```

### E.2 Frontend Unit Tests (Dashboard Cache)

```typescript
describe('DashboardApiService - Cache Activation', () => {
  
  it('should not make duplicate HTTP calls for getResultados', (done) => {
    service.getResultados().subscribe(() => {
      service.getResultados().subscribe(() => {
        const matches = httpMock.match(url => url.includes('/resultados'));
        expect(matches.length).toBe(1);  // 1 HTTP only
        done();
      });
    });
  });
  
  it('should cache all 4 methods independently', (done) => {
    service.getResultados().subscribe(() => {
      service.getProgreso().subscribe(() => {
        service.getNovedades().subscribe(() => {
          service.getResumen().subscribe(() => {
            expect(httpMock.match(url => url.includes('/resultados')).length).toBe(1);
            expect(httpMock.match(url => url.includes('/progreso')).length).toBe(1);
            expect(httpMock.match(url => url.includes('/novedades')).length).toBe(1);
            expect(httpMock.match(url => url.includes('/resumen')).length).toBe(1);
            done();
          });
        });
      });
    });
  });
});
```

### E.3 Backend Unit Tests (Redis Caching)

```java
@QuarkusTest
public class CargoServiceCachingTest {
  
  @Inject
  CargoService service;
  
  @Inject
  CargoRepository repository;
  
  @Test
  void testFindAllCached() {
    // 1st call: should hit repository
    List<CargoDto> cargos1 = service.findAll();
    assertNotNull(cargos1);
    
    // 2nd call: should come from Redis (spy on repository)
    List<CargoDto> cargos2 = service.findAll();
    assertEquals(cargos1, cargos2);
    
    // Verify repository was called only once
    // (Using Mockito spy or query counter)
  }
}
```

### E.4 Integration Test: E2E Flow

```
Test: Observer registers acta with form (5 cargos)
Expected:
  - getCandidatosPorCargo(1) → HTTP: /partidos, /candidatos
  - getCandidatosPorCargo(2-5) → HTTP: /candidatos only (partidos cached)
  - Total HTTP: 1 + 5 = 6 (before: 11)
  - Latency: <1s (before: 2-3s)
```

---

## F. Rollback Plan

**Si algo falla:**

1. **Revert frontend cache:** Remove `shareReplay(1)` from observer.api.ts + dashboard.api.ts
   - Risk: None. Code returns to making duplicate requests (slower but correct)

2. **Revert Redis:** Remove `@Cacheable` annotations, disable Redis in application.properties
   - Risk: None. Queries go back to BD directly

3. **Clear Redis cache:** `GET /admin/cache/clear` endpoint
   - Risk: None. TTL will auto-invalidate anyway

**Zero data loss. Todo reversible en 5 minutos.**

---

## G. Files Changed Summary

| Archivo | Cambio | Líneas | Prioridad |
|---------|--------|--------|-----------|
| `observer.api.ts` | Agregar `partidosCache$` + `shareReplay(1)` | 15 | P1 |
| `dashboard.api.ts` | Activar cache en 4 métodos | 30 | P3 |
| `CargoService.java` | Agregar `@Cacheable("cargos")` | 2 | P2 |
| `PartidoService.java` | Agregar `@Cacheable("partidos")` | 2 | P2 |
| `CandidatoService.java` | Agregar `@Cacheable("candidatos")` | 2 | P2 |
| `Provincia/Canton/Parroquia.java` | Agregar `@Cacheable` geo | 6 | P2 |
| `application.properties` | Redis config | 5 | P2 |
| `AdminResource.java` | Agregar `/cache/clear` endpoint | 15 | P2 |
