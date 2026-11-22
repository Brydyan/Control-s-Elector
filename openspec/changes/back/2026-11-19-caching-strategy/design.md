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

## C. Backend Caching: Caffeine en memoria

> **Decisión D2 (tomada): Caffeine en memoria, no Redis.** El diseño original elegía Redis
> para que varias réplicas compartieran caché. Con el despliegue real —backend en **Railway
> con 1 réplica**, base en **Neon**— el problema que Redis resolvía no existe, y Redis
> añadiría un servicio, un coste y un modo de falla. Justificación completa en
> `documents/CACHE_SETUP_GUIDE.md` (sección 3) y en `apply-progress.md`.
>
> Redis queda como **fase 2**, condicionado a que el backend corra en más de una réplica.

### C.1 Configuration

**Archivo:** `/backend/electoral-api-quarkus/src/main/resources/application.properties`

```properties
# Caché de datos estáticos: Caffeine (en memoria del proceso).
# TTL corto a propósito: acota el desfase si algún día hubiera más de una réplica.
quarkus.cache.caffeine.cargos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.caffeine.partidos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.caffeine.candidatos.expire-after-write=${CACHE_TTL:2m}
quarkus.cache.caffeine.geografia.expire-after-write=${CACHE_TTL:2m}

# Métricas estándar de Caffeine por caché (entradas, evictions, acierto/fallo)
quarkus.cache.caffeine.cargos.metrics-enabled=true
quarkus.cache.caffeine.partidos.metrics-enabled=true
quarkus.cache.caffeine.candidatos.metrics-enabled=true
quarkus.cache.caffeine.geografia.metrics-enabled=true

# Identificador de la réplica (Railway inyecta RAILWAY_REPLICA_ID por réplica)
replica.id=${RAILWAY_REPLICA_ID:local}
```

`CACHE_TTL` ajusta el TTL por entorno sin tocar el código. `quarkus.cache.enabled` apaga la
extensión completa (se fija en tiempo de build).

### C.2 Service-level Caching

La lectura cacheada no usa `@CacheResult` sino un acceso explícito con
`CacheDeLectura` (`shared/cache/CacheDeLectura.java`): la anotación de Quarkus no deja ver
si el acierto vino de la caché o de la base, y sin esa visibility el hit ratio —la métrica
que dice si la caché está sirviendo— queda a ciegas.

```java
// Patrón real (casos de uso ListarCargosUseCase, ListarPartidosUseCase,
// ListarCandidatosUseCase y ListarGeografiaUseCase):
Optional<CargoDto> enCache = cache.leer("cargos", "todos");
if (enCache.isPresent()) {
  return enCache.get();
}
List<CargoDto> cargos = repository.listAll().stream().map(this::toDto).toList();
cache.guardar("cargos", "todos", cargos);
return cargos;
```

**Cachés, claves y TTL:**

| Caso de uso | Caché | Clave | TTL | Razón |
|-------------|-------|-------|-----|-------|
| `ListarCargosUseCase` | `cargos` | `todos` | 2 min | Configuración, cambia en Setup |
| `ListarPartidosUseCase` | `partidos` | `todos` | 2 min | Configuración, cambia en Setup |
| `ListarCandidatosUseCase` | `candidatos` | `cargo:<cargoId>\|partido:<partidoId>` | 2 min | Configuración, cambia en Setup |
| `ListarGeografiaUseCase` | `geografia` | `todos` | 2 min | Un solo árbol provincia→canton→parroquia→JRV |

**No se cachea** nada del pipeline de actas: cambia en vivo y no es un dato estático.

### C.3 Query Impact

Lo que sigue son las **cifras medidas** contra el backend real (Postgres 16 con el rol
`electoral_app` sin BYPASSRLS, 100 VUs). La métrica comparable es el **desglose de consultas
de configuración**, no el total absoluto de consultas al servidor: el total depende del ruido
de arranque (login, auditoría, metadatos) y no se puede comparar entre corridas.

| | Sin caché | Con caché |
|---|---|---|
| Hit ratio | 0 % | **100 %** |
| p95 | 3.70 ms | **0.97 ms** |
| Consultas de configuración a la base | 374.240 | **4** (el llenado inicial) |

Reportes: `documents/load-test/cache-hit-ratio-summary-{con,sin}-cache.md`.

---

## D. Infrastructure: Invalidation & Monitoring

### D.1 Manual Invalidation Endpoint

**Archivo:** `/backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/web/AdminCacheResource.java`

La autorización usa el patrón real del repo —`Autorizador.exigir("cache:clear", "MASTER")`— y
no `@RolesAllowed`, que no se usa en ninguna parte del backend. ADMIN no escribe en este
dominio y vaciar la caché es una escritura sobre el estado operativo.

```java
@GET
@Path("/clear")
public Response clearCache(@QueryParam("cacheName") String cacheName, ...) {
  autorizador.exigir("cache:clear", "MASTER");
  // ... invalida todos los cachés o solo el pedido, y deja rastro en audit_log
}
```

> **Alcance: el proceso que atiende la petición.** Con Caffeine en memoria, vaciar la caché
> borra la del proceso que recibe el request. Con una sola réplica (lo actual) eso es toda la
> caché; con varias sería solo una de ellas.

### D.2 Monitoring

**Métricas a exponer (via Micrometer + Prometheus), en `/q/metrics`:**

```properties
quarkus.micrometer.enabled=true
quarkus.micrometer.export.prometheus.enabled=true

# Métricas propias, publicadas por CacheDeLectura:
# - cache_hits_total{cache="...",replica="..."}
# - cache_misses_total{cache="...",replica="..."}
#
# Métricas estándar de Caffeine (metrics-enabled=true):
# - cache_size{cache="..."}
# - cache_gets_total{cache="...",result="hit|miss"}
# - cache_evictions_total{cache="..."}
```

La etiqueta `replica` no es adorno: Railway **agrega** las métricas de todas sus réplicas en
un solo tablero, así que sin ella no se puede distinguir "el hit ratio bajó" de "una réplica
quedó desfasada respecto de las otras".

**Alertas recomendadas:**
- Hit ratio < 70 % → revisar la lógica de invalidación
- `cache_evictions_total` en ascenso → la caché está más pequeña de lo que el tráfico necesita
- Si aparecen **más de un valor** de `replica=` → hay más de una réplica en servicio: revisar
  el guardrail de réplicas de `documents/CACHE_SETUP_GUIDE.md`
- Memoria del proceso cerca del límite de Railway → el límite es el heap del contenedor, no
  la memoria de Redis

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

### E.3 Backend Unit Tests (Caffeine Caching)

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
    
    // 2nd call: should come from the cache (spy on repository)
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

2. **Revert backend cache:** quitar la llamada a `CacheDeLectura` en los casos de uso, o
   apagar la extensión con `QUARKUS_CACHE_ENABLED=false` (reconstruyendo la imagen)
   - Risk: None. Queries go back to BD directly

3. **Clear the cache:** `GET /api/v1/admin/cache/clear` endpoint
   - Risk: None. TTL will auto-invalidate anyway

**Zero data loss. Todo reversible en 5 minutos.**

---

## G. Files Changed Summary

| Archivo | Cambio | Prioridad |
|---------|--------|-----------|
| `observer.api.ts` | Agregar `partidosCache$` + `shareReplay(1)` | P1 |
| `dashboard.api.ts` | Activar cache en 4 métodos | P3 |
| `ListarCargosUseCase` | Leer y guardar en la caché `cargos` | P2 |
| `ListarPartidosUseCase` | Leer y guardar en la caché `partidos` | P2 |
| `ListarCandidatosUseCase` | Leer y guardar en la caché `candidatos` | P2 |
| `ListarGeografiaUseCase` | Leer y guardar en la caché `geografia` | P2 |
| `CacheDeLectura.java` | Helper de lectura cacheada con hit ratio medido | P2 |
| `application.properties` | Config Caffeine, TTL, métricas y `replica.id` | P2 |
| `AdminCacheResource.java` | Endpoint `/cache/clear` con auditoría | P2 |
