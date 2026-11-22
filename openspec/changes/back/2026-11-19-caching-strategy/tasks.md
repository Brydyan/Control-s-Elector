# Tasks — Caching Strategy: Frontend + Backend Optimization

> **Este es el plan original del SDD, conservado como registro.** No refleja lo que se
> implementó. La **decisión D2 se cerró como opción A (Caffeine en memoria, TTL de 2 min)**, sin
> Redis: el backend corre en Railway con **1 réplica**, así que el problema que Redis resolvía
> (copias distintas entre réplicas) no existe, y en Neon una caché en proceso es mejor que una
> remota. Las secciones de Redis quedan marcadas como sustituidas, y `@Cacheable` se implementó
> con `CacheDeLectura` para poder medir el hit ratio.
>
> El estado real de lo entregado, con los commits y las mediciones, está en
> `apply-progress.md`. El diseño vigente está en `design.md`.

---

## A. Frontend: Observer Partidos Cache (P1)

### A.1 Implementar Caché en observer.api.ts

- [ ] Archivo: `/frontend/app/src/app/features/observer/data/observer.api.ts`
- [ ] Agregar field (línea ~30):
  ```typescript
  private partidosCache$: Observable<PartidoDto[]> | null = null;
  ```
- [ ] Modificar `getCandidatosPorCargo()` para usar cache:
  ```typescript
  getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
    if (!this.partidosCache$) {
      this.partidosCache$ = this.http.get<PartidoDto[]>(
        `${this.configUrl}/partidos`
      ).pipe(shareReplay(1));
    }
    
    return forkJoin({
      candidatos: this.http.get<CandidatoDto[]>(
        `${this.configUrl}/candidatos?cargoId=${cargoId}`,
      ),
      partidos: this.partidosCache$,
    })
  }
  ```
- [ ] Compilar sin errores
- [ ] Verificar tipos: `PartidoDto[]` y `CandidatoInfo[]` correctos

### A.2 Testing: Partidos Cache

- [ ] Crear test: `ObserverApiService.test.ts`
  ```typescript
  it('should cache partidos and reuse', (done) => {
    service.getCandidatosPorCargo(1).subscribe(() => {
      service.getCandidatosPorCargo(2).subscribe(() => {
        expect(httpMock.match(url => url.includes('/partidos')).length).toBe(1);
        done();
      });
    });
  });
  ```
- [ ] Test pasa: 1 HTTP request para 2 calls
- [ ] Regresión: Datos correctos (candidatos pueden diferir, partidos iguales)

**Mutation:** Si remueven `shareReplay(1)`, test falla (2 HTTP requests)

---

## B. Frontend: Dashboard Cache Activation (P3)

### B.1 Activar Cache en dashboard.api.ts

- [ ] Archivo: `/frontend/app/src/app/features/dashboard/data/dashboard.api.ts`
- [ ] Agregar fields (línea ~26):
  ```typescript
  private resultadosCache$: Observable<ResultadoCargo[]> | null = null;
  private progresoCache$: Observable<ProgresoDto[]> | null = null;
  private novedadesCache$: Observable<NovedadDto[]> | null = null;
  private resumenCache$: Observable<ResumenDto> | null = null;
  ```
- [ ] Modificar `getResultados()` (línea ~36):
  ```typescript
  getResultados(): Observable<ResultadoCargo[]> {
    if (!this.resultadosCache$) {
      this.resultadosCache$ = this.http.get<ResultadoCargo[]>(
        `${this.baseUrl}/resultados`
      ).pipe(shareReplay(1));
    }
    return this.resultadosCache$;
  }
  ```
- [ ] Repetir para `getProgreso()`, `getNovedades()`, `getResumen()`
- [ ] Compilar sin errores

### B.2 Testing: Dashboard Cache

- [ ] Crear test: `DashboardApiService.test.ts`
  ```typescript
  it('should cache resultados and reuse', (done) => {
    service.getResultados().subscribe(() => {
      service.getResultados().subscribe(() => {
        expect(httpMock.match(url => url.includes('/resultados')).length).toBe(1);
        done();
      });
    });
  });
  ```
- [ ] Test pasa para todos 4 métodos (resultados, progreso, novedades, resumen)
- [ ] Regresión: Dashboard UI muestra datos correctos

**Mutation:** Si remueven cache checks, test falla (4 HTTP requests en lugar de 1)

---

## C. Backend: Redis Configuration (P2)

> **SUSTITUIDO por la decisión D2 (opción A: Caffeine en memoria).** Esta sección describe el
> diseño original con Redis. Con el despliegue real —backend en Railway con **1 réplica**, base
> en Neon— Redis no aporta: el problema que resolvía (copias distintas entre réplicas) no
> existe, y añadiría un servicio, un coste y un modo de falla. **No se ejecuta.** Lo que sí se
> implementó está en la sección D' y en `apply-progress.md` ("Decision D2: tomada").
>
> Redis queda como fase 2, condicionado a que el backend corra en más de una réplica.

### C.1 Configurar Redis en application.properties

- [ ] Archivo: `/backend/electoral-api-quarkus/src/main/resources/application.properties`
- [ ] Agregar líneas:
  ```properties
  # Redis Caching
  quarkus.redis.hosts=localhost:6379
  quarkus.cache.type=redis
  quarkus.cache.redis.expire-after-write=10m
  ```
- [ ] Testing local: `docker run -d -p 6379:6379 redis:latest`
- [ ] Verificar conexión: `redis-cli ping` → PONG
- [ ] Compilar sin errores

### C.2 Verificar Redis Connection

- [ ] Logs muestran: "Redis connection established"
- [ ] No errores de timeout o "unable to connect"
- [ ] redis-cli: `KEYS *` retorna claves después de llamadas a API

---

## D'. Backend: caché en memoria (Caffeine) — lo implementado (P2)

### D'.1 Configurar Caffeine en application.properties

- [x] Archivo: `backend/electoral-api-quarkus/src/main/resources/application.properties`
- [x] Agregar líneas:
  ```properties
  quarkus.cache.enabled=${QUARKUS_CACHE_ENABLED:true}
  quarkus.cache.caffeine.cargos.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.partidos.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.candidatos.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.geografia.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.cargos.metrics-enabled=true
  quarkus.cache.caffeine.partidos.metrics-enabled=true
  quarkus.cache.caffeine.candidatos.metrics-enabled=true
  quarkus.cache.caffeine.geografia.metrics-enabled=true
  replica.id=${RAILWAY_REPLICA_ID:local}
  ```
- [x] Sin servicio externo: Caffeine va en la JVM
- [x] TTL parametrizable por entorno con `CACHE_TTL`
- [x] Métricas etiquetadas por réplica para detectar cache split

### D'.2 Verificar que la caché sirve

- [x] `GET /q/metrics` publica `cache_hits_total{cache="...",replica="..."}` y
      `cache_misses_total{...}`
- [x] `GET /q/metrics` publica `cache_size{cache="..."}` (métricas estándar de Caffeine)
- [x] Con `QUARKUS_CACHE_ENABLED=false` la app sigue sirviendo 200 (degradación, no 500)

---

## D. Backend: Service-level Caching with @Cacheable (P2)

> **Implementado con `CacheDeLectura` en vez de `@Cacheable`** (desviación D.1/D.2, registrada en
> `apply-progress.md`). Motivo: la API pública de Quarkus solo expone `get(key, loader)` e
> `invalidate*`, así que `@Cacheable` no deja distinguir si el acierto vino de la caché o de la
> base; sin esa visibility el hit ratio —la métrica que demuestra que la caché sirve— no se
> puede medir. El helper centraliza además la degradación cuando la caché está apagada.
> La sección D' de arriba describe la configuración realmente implementada.

### D.1 Agregar @Cacheable a CargoService

- [ ] Archivo: `/backend/electoral-api-quarkus/src/main/java/.../CargoService.java`
- [ ] Agregar anotación a `findAll()`:
  ```java
  @Cacheable(cacheName = "cargos")
  public List<CargoDto> findAll() {
    return repository.findAll()
        .stream()
        .map(this::toDto)
        .collect(toList());
  }
  ```
- [ ] Compilar sin errores
- [ ] Verificar que NO aplicar a `findById()` (variable)

### D.2 Agregar @Cacheable a Otros Servicios

- [ ] `PartidoService.findAll()` → `@Cacheable("partidos")`
- [ ] `CandidatoService.findAll()` y `findByCargoId()` → `@Cacheable("candidatos")`
- [ ] `ProvinciaService.findAll()` → `@Cacheable("provincias")`
- [ ] `CantonService.findAll()` y `findByProvinciaId()` → `@Cacheable("cantones")`
- [ ] `ParroquiaService.findAll()` y `findByCantonId()` → `@Cacheable("parroquias")`
- [ ] Compilar sin errores
- [ ] Verificar tipos de respuesta correctos

**Mutation:** Si remueven `@Cacheable`, cada query va a BD (test debe validar con SQLStatementCounter)

### D.3 Testing: Backend Cache

- [ ] Crear test: `CargoServiceCachingTest.java`
  ```java
  @Test
  void testFindAllCached() {
    List<CargoDto> cargos1 = service.findAll();
    List<CargoDto> cargos2 = service.findAll();
    
    assertEquals(cargos1, cargos2);
    // Verify only 1 BD query (using spy or query counter)
  }
  ```
- [ ] Test pasa: 2 calls → 1 BD query
- [ ] Repetir para todos 6 servicios

---

## E. Admin: Cache Invalidation Endpoint (P2)

### E.1 Agregar /admin/cache/clear Endpoint

- [ ] Archivo: `/backend/electoral-api-quarkus/src/main/java/.../AdminResource.java`
- [ ] Agregar método:
  ```java
  @GET
  @Path("/cache/clear")
  @RolesAllowed({"ADMIN"})
  public Response clearCache(@QueryParam("cacheName") String cacheName) {
    if (cacheName == null || cacheName.isEmpty()) {
      cacheManager.getCacheNames().forEach(name -> 
        cacheManager.getCache(name).ifPresent(Cache::invalidateAll)
      );
      return Response.ok("All caches cleared").build();
    } else {
      cacheManager.getCache(cacheName).ifPresent(Cache::invalidateAll);
      return Response.ok("Cache '" + cacheName + "' cleared").build();
    }
  }
  ```
- [ ] Compilar sin errores
- [ ] Verificar que solo ADMIN puede acceder

### E.2 Testing: Cache Clear

- [ ] Crear test: `AdminResourceTest.java`
  ```java
  @Test
  void testClearAllCaches() {
    // Populate caches
    cargoService.findAll();
    
    // Clear all
    given()
      .when()
      .get("/admin/cache/clear")
      .then()
      .statusCode(200)
      .body(containsString("All caches cleared"));
    
    // Verify caches empty (via redis-cli)
  }
  ```
- [ ] Test pasa: Endpoint accesible, caches se limpian

**Mutation:** Si remueven clear logic, endpoint retorna OK pero caches no se vacían (test debe verificar estado post-call)

---

## F. Testing & Verification (P1)

### F.1 Unit Tests: Frontend

- [ ] `ObserverApiService.test.ts`: Partidos cache (1 HTTP para 2 calls)
- [ ] `DashboardApiService.test.ts`: 4 métodos cached (1 HTTP c/u para 2 calls)
- [ ] Regresión: UI muestra datos correctos post-cache

**Tests Passing:** 2/2

### F.2 Unit Tests: Backend

- [ ] `CargoServiceCachingTest.java`: 2 calls → 1 BD query
- [ ] `PartidoServiceCachingTest.java`: 2 calls → 1 BD query
- [ ] `CandidatoServiceCachingTest.java`: 2 calls → 1 BD query
- [ ] `GeographyServicesCachingTest.java`: Provincia/Canton/Parroquia (3 servicios)

**Tests Passing:** 6/6

### F.3 Integration Test: Observer Form

- [ ] E2E: Observer load form con 5 cargos
  ```
  Expected:
  - getCandidatosPorCargo(1): HTTP /partidos + /candidatos
  - getCandidatosPorCargo(2-5): HTTP /candidatos only
  - Total HTTP: 1 + 5 = 6 (before: 11)
  - Latency: <1s (before: 2-3s)
  ```
- [ ] Test pasa: Formulario carga rápido sin duplicados

### F.4 Integration Test: Dashboard Tabs

- [ ] E2E: Dashboard load, cambiar entre 4 tabs
  ```
  Expected:
  - Click Resumen: HTTP /resumen
  - Click Resultados: HTTP /resultados
  - Click Progreso: HTTP /progreso
  - Click Novedades: HTTP /novedades
  - Click Resumen again: Cache hit (0 HTTP)
  - Total HTTP: 4 (before: 5)
  - Latency: <0.5s (before: 4-5s)
  ```
- [ ] Test pasa: Tabs switch sin requests nuevas

### F.5 Load Test: 100 Usuarios Simultáneos

- [ ] Setup: 100 threads, ramp up 10s, 10 requests cada uno
- [ ] Expected:
  - P95 latencia: < 2s (baseline Performance SDD)
  - Hit ratio: > 90% (caches working)
  - BD query count: -90% vs sin caches
  - Timeout: 0
- [ ] Result: TBD (post-implementation)

### F.6 Verificación: Redis Monitoring

- [ ] redis-cli: `INFO stats` muestra hit/miss counts
- [ ] redis-cli: `MEMORY USAGE` < 100MB
- [ ] Redis keys present: `KEYS cargos:*`, `KEYS partidos:*`, etc.

**Result:** ✓ / ✗

---

## G. Documentation (P2)

- [ ] Crear `CACHING_STRATEGY.md`
  - Patrón RxJS `shareReplay(1)`
  - Cuándo usar (GET requests, datos estáticos)
  - Cuándo NO usar (mutable data, real-time updates)

- [x] Crear `CACHE_SETUP_GUIDE.md` (no hubo `REDIS_SETUP_GUIDE.md`: la decisión D2 fue opción A)
  - Sin servicio externo: Caffeine va en la JVM
  - Setup producción: variable `CACHE_TTL` en Railway
  - Identificador de réplica (`RAILWAY_REPLICA_ID`) para detectar cache split
  - Monitoring con Prometheus

- [x] Crear `CACHE_INVALIDATION.md`
  - Manual clear endpoint `/api/v1/admin/cache/clear` (rol MASTER)
  - TTL auto-invalidation (2 min, parametrizable con `CACHE_TTL`)
  - Fase 2: Event-driven invalidation

---

## Implementation Checklist

### Orden Recomendado

```
1. A.1 + A.2 → Observer partidos cache (30 min + tests 15 min)
   
2. B.1 + B.2 → Dashboard cache activation (20 min + tests 15 min)
   
3. C.1 + C.2 → Configuracion de Caffeine (10 min)
   
4. D.1 + D.2 + D.3 → CacheDeLectura en los casos de uso (30 min + tests 30 min)
   
5. E.1 + E.2 → Admin clear endpoint (15 min + tests 10 min)
   
6. F → Testing completo (60 min)
   
7. G → Documentación (20 min)

TOTAL: ~4-5 horas
```

### Pre-Deployment Checklist

- [ ] Todos los tests pasan (unit + integration + E2E)
- [ ] Redis correr local sin errores
- [ ] Load test 100 usuarios: P95 < 2s, hit ratio > 90%
- [x] Endpoint `/api/v1/admin/cache/clear` accesible solo MASTER
- [ ] Zero regressions: Observer form funciona, Dashboard tabs funcionan
- [ ] Documentación completa

### Post-Deployment Monitoring

- [ ] Cache hit ratio: > 90% (target)
- [ ] Redis memory: < 100MB (target)
- [ ] Latencia P95: < 2s (target)
- [ ] BD query count: -80% vs baseline (target)

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Cache apagada → la app sigue funcionando | Degradacion: las consultas van a la BD (mas lento, pero correcto). Lo sostiene `CacheApagadaIT` |
| Stale cache data | Invalidacion por escritura en cada caso de uso; TTL de 2 min como red de seguridad; vaciado manual |
| Cache hit/miss race conditions | RxJS shareReplay handles synchronization |
| Memoria de la cache | Caffeine va en el heap de la JVM: `cache_size` y `cache_evictions_total` por cache en `/q/metrics` |
| Cache split entre replicas | No hay split con 1 replica. Si se escala: TTL de 2 min acota el desfase y la etiqueta `replica` en las metricas lo hace visible |
| Admin endpoint abused | `Autorizador.exigir("cache:clear", "MASTER")` + audit logs |

