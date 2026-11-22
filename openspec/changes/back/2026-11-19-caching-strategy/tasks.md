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

> **✅ Implementado.** La mutación (quitar `shareReplay(1)`) hace caer el test.

### A.1 Implementar Caché en observer.api.ts

- [x] Archivo: `/frontend/app/src/app/features/observer/data/observer.api.ts`
- [x] Agregar field (línea ~30):
  ```typescript
  private partidosCache$: Observable<PartidoDto[]> | null = null;
  ```
- [x] Modificar `getCandidatosPorCargo()` para usar cache:
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
- [x] Compilar sin errores
- [x] Verificar tipos: `PartidoDto[]` y `CandidatoInfo[]` correctos

### A.2 Testing: Partidos Cache

- [x] Crear test: `observer.api.spec.ts` (spec, no `.test.ts`)
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
- [x] Test pasa: 1 HTTP request para 2 calls
- [x] Regresión: Datos correctos (candidatos pueden diferir, partidos iguales)

**Mutation:** Si remueven `shareReplay(1)`, test falla (2 HTTP requests)

---

## B. Frontend: Dashboard Cache Activation (P3)

> **✅ Implementado.** La mutación (quitar el chequeo de caché) hace caer el test.

### B.1 Activar Cache en dashboard.api.ts

- [x] Archivo: `/frontend/app/src/app/features/dashboard/data/dashboard.api.ts`
- [x] Agregar fields (línea ~26):
  ```typescript
  private resultadosCache$: Observable<ResultadoCargo[]> | null = null;
  private progresoCache$: Observable<ProgresoDto[]> | null = null;
  private novedadesCache$: Observable<NovedadDto[]> | null = null;
  private resumenCache$: Observable<ResumenDto> | null = null;
  ```
- [x] Modificar `getResultados()` (línea ~36):
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
- [x] Repetir para `getProgreso()`, `getNovedades()`, `getResumen()`
- [x] Compilar sin errores

### B.2 Testing: Dashboard Cache

- [x] Crear test: `dashboard.api.spec.ts`
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
- [x] Test pasa para todos 4 métodos (resultados, progreso, novedades, resumen)
- [x] Regresión: Dashboard UI muestra datos correctos

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
>
> Los servicios que este plan nombraba (`CargoService`, `PartidoService`, `ProvinciaService`…)
> **no existen** en el repo: el patrón real son casos de uso (`ListarCargosUseCase`,
> `ListarPartidosUseCase`, `ListarCandidatosUseCase`, `ListarGeografiaUseCase`). Lo que quedó:

- [x] `ListarCargosUseCase`: lee de la caché `cargos` y guarda el resultado
- [x] `ListarPartidosUseCase`: lee de la caché `partidos` y guarda el resultado
- [x] `ListarCandidatosUseCase`: lee de la caché `candidatos` (clave `cargo:<id>|partido:<id>`)
- [x] `ListarGeografiaUseCase`: lee de la caché `geografia` (un árbol, no tres listas)
- [x] `CacheDeLectura`: helper central con conteo de aciertos/fallos y degradación si la caché
      está apagada
- [x] Cada escritura invalida su caché con `@CacheInvalidateAll`

### D.3 Testing: Backend Cache

- [x] Tests reales: `CacheServicioIT` (2 llamadas → 1 consulta a la base, contador de
      `pg_stat_statements`), `CacheContenidoIT` (las 4 cachés sirven lo que deben),
      `CacheInvalidacionIT` (leer → escribir → leer), `CacheTTLIT` (la entrada caduca),
      `CacheMetricasIT` (métricas por réplica), `CacheApagadaIT` (sin caché, 200)
- [x] No existe `CargoServiceCachingTest`: ese servicio no existe

**Mutation:** si se quita la lectura cacheada, cada llamada va a la base y el test cae
(verificado con la métrica de consultas y con el TTL)

---

## E. Admin: Cache Invalidation Endpoint (P2)

> **✅ Implementado, con dos desviaciones respecto de este plan** (registradas en
> `apply-progress.md`): el resource es `AdminCacheResource` (no `AdminResource`) y la
> autorización usa el patrón real del repo —`Autorizador.exigir("cache:clear", "MASTER")`— en
> lugar de `@RolesAllowed({"ADMIN"})`, que no se usa en ninguna parte del backend.

### E.1 Agregar /admin/cache/clear Endpoint

- [x] Archivo: `/backend/electoral-api-quarkus/src/main/java/.../auth/web/AdminCacheResource.java`
- [x] Agregar método:
  ```java
  @GET
  @Path("/clear")
  public Response clearCache(@QueryParam("cacheName") String cacheName) {
    autorizador.exigir("cache:clear", "MASTER");
    // invalida todos los cachés o solo el pedido, y deja rastro en audit_log
  }
  ```
- [x] Compilar sin errores
- [x] Verificar que solo MASTER puede acceder (matriz de roles probada en `AdminCacheResourceTest`)

### E.2 Testing: Cache Clear

- [x] Crear test: `AdminCacheResourceTest.java` (7 tests: matriz de roles, estado post-call, auditoría)
  ```java
  @Test
  void testClearAllCaches() {
    // Poblar las cachés, vaciarlas y comprobar el estado después de la llamada
  }
  ```
- [x] Test pasa: Endpoint accesible, cachés se limpian de verdad

**Mutation:** Si remueven el clear, el endpoint responde OK pero las cachés no se vacían — de ahí
que el test compruebe el estado posterior y no solo el mensaje de respuesta. Verificado: sin
esperar al `Uni` de `invalidateAll()`, la invalidación nunca se ejecutaba.

---

## F. Testing & Verification (P1)

### F.1 Unit Tests: Frontend

- [x] `observer.api.spec.ts`: Partidos cache (1 HTTP para 2 calls)
- [x] `dashboard.api.spec.ts`: 4 métodos cached (1 HTTP c/u para 2 calls)
- [x] Regresión: UI muestra datos correctos post-cache

**Tests Passing:** 2/2

### F.2 Unit Tests: Backend

- [x] `CacheServicioIT`: 2 llamadas → 1 consulta a la base (contador de `pg_stat_statements`)
- [x] `CacheContenidoIT`: las 4 cachés sirven el contenido esperado
- [x] `CacheInvalidacionIT`: escribir invalida la caché
- [x] `CacheTTLIT`: la entrada caduca de verdad
- [x] `CacheMetricasIT`: los contadores llevan la etiqueta `replica`
- [x] `CacheApagadaIT`: sin caché, la API responde 200

**Tests Passing:** 6/6 (más `AdminCacheResourceTest` con 7)

### F.3 Integration Test: Observer Form

- [x] E2E: Observer load form con 5 cargos
  ```
  Expected:
  - getCandidatosPorCargo(1): HTTP /partidos + /candidatos
  - getCandidatosPorCargo(2-5): HTTP /candidatos only
  - Total HTTP: 1 + 5 = 6 (before: 11)
  - Latency: <1s (before: 2-3s)
  ```
- [x] Test pasa: Formulario carga rápido sin duplicados
  (`e2e/cache-datos-estaticos.spec.ts` — cuenta peticiones, no tiempos)

### F.4 Integration Test: Dashboard Tabs

- [x] E2E: Dashboard load, cambiar entre 4 tabs
  ```
  Expected:
  - Click Resumen: HTTP /resumen
  - Click Resultados: HTTP /resultados
  - Click Progreso: HTTP /progreso
  - Click Novedades: HTTP /novedades
  - Click Resumen again: Cache hit (0 HTTP)
  - Total HTTP: 4 (before: 5)
  ```
- [x] Test pasa: Tabs switch sin requests nuevas

### F.5 Load Test: 100 Usuarios Simultáneos

- [x] Setup: 100 VUs, ramp up 10 s, 10 requests cada uno (`frontend/tests/load/`)
- [x] Resultado **medido** contra el backend real:
  - P95 latencia: **0.92 ms** (target < 2 s) ✅
  - Hit ratio: **100 %** (target > 90 %) ✅
  - Consultas de configuración a la base: **4** en el llenado inicial (374.240 sin caché)
  - Timeout: **0**
  - Reportes: `documents/load-test/cache-hit-ratio-summary-{con,sin}-cache.md`

### F.6 Verificación: Monitoring de la caché

> **Redis no aplica (D2 = opción A).** El equivalente con Caffeine es `/q/metrics`.

- [x] `GET /q/metrics` publica `cache_hits_total{cache="...",replica="..."}` y
      `cache_misses_total{...}` (métricas propias de `CacheDeLectura`)
- [x] `GET /q/metrics` publica las métricas estándar de Caffeine: `cache_size`,
      `cache_gets_total{cache,result}`, `cache_puts_total`, `cache_evictions_total`
- [x] Memoria de la caché medida: **4 entradas** en total (una por caché), ~2,3 KB de payload
      JSON. El límite real es el heap de la JVM del contenedor, no un servicio aparte
- [x] Con la caché apagada (`QUARKUS_CACHE_ENABLED=false`) la app sigue en 200

---

## G. Documentation (P2)

- [x] Crear `CACHING_STRATEGY.md`
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

- [x] Todos los tests pasan (unit + integration + E2E) — 250 backend, 1092 frontend, 10 E2E
- [x] Sin servicio externo de caché (Caffeine va en la JVM): no hay Redis que levantar
- [x] Load test 100 usuarios: P95 0.92 ms, hit ratio 100 %
- [x] Endpoint `/api/v1/admin/cache/clear` accesible solo MASTER
- [x] Zero regressions: Observer form funciona, Dashboard tabs funcionan
- [x] Documentación completa (`CACHING_STRATEGY.md`, `CACHE_INVALIDATION.md`,
      `CACHE_SETUP_GUIDE.md`)
- [ ] `CACHE_TTL` definido en las variables de Railway (por defecto 2 min si no se define)
- [ ] `RAILWAY_REPLICA_ID` inyectado por Railway (detecta cache split si algún día hay >1 réplica)

### Post-Deployment Monitoring

- [ ] Cache hit ratio: > 90 % (target) — medido 100 % en local
- [ ] `cache_size` y `cache_evictions_total` por caché (el límite es el heap de la JVM)
- [ ] Latencia P95: < 2 s (target) — medido 0.92 ms en local
- [ ] Consultas de configuración a la base: −80 % vs baseline (target) — medido −99.999 %

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

