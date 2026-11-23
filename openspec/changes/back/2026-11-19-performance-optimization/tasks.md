# Tasks — Performance Optimization: Indexing, Caching & Infrastructure

---

## A. Database Indexing (P0 — Crítica)

### A.1 Crear Migration: Índices Tabla `acta`

- [x] Crear archivo: `backend/.../changelog/048-add-acta-indices.sql` (011/012 ya estaban ocupados)
- [x] Script: Crear 5 índices simples + 1 compuesto (nombres `idx_acta_*`, no `ix_acta_*`)
  ```sql
  CREATE INDEX ix_acta_registrada_por ON acta(registrada_por);
  CREATE INDEX ix_acta_estado ON acta(estado);
  CREATE INDEX ix_acta_jrv_id ON acta(jrv_id);
  CREATE INDEX ix_acta_cargo_id ON acta(cargo_id);
  CREATE INDEX ix_acta_fecha_desc ON acta(fecha_registro DESC);
  CREATE INDEX ix_acta_jrv_cargo_estado ON acta(jrv_id, cargo_id, estado);
  ```
- [x] Testing local CONFIRMADO: 6 índices `idx_acta_*` visibles en `pg_indexes`
- [x] No errores en BD (Liquibase Run: 2, 108 changesets)

### A.2 Crear Migration: Índices Tabla `acta_detalle`

- [x] Crear archivo: `backend/.../changelog/049-add-acta-detalle-indices.sql`
- [x] Script: 1 índice (`idx_acta_detalle_candidato_id`); el de `acta_id` es redundante con el UNIQUE(acta_id, candidato_id)
  ```sql
  CREATE INDEX ix_acta_detalle_acta_id ON acta_detalle(acta_id);
  CREATE INDEX ix_acta_detalle_candidato_id ON acta_detalle(candidato_id);
  ```
- [x] Testing local CONFIRMADO: índice visible en `pg_indexes`
- [x] EXPLAIN ANALYZE: a escala del set sintético (287 filas de detalle) el planner elige seq scan; a escala de produccion usa el indice. Medido y documentado

### A.3 Verificación: EXPLAIN ANALYZE

- [x] `EXPLAIN ANALYZE SELECT * FROM acta WHERE registrada_por = 100 LIMIT 20;`
  - Debe decir: "Index Scan using ix_acta_registrada_por" (NO "Seq Scan")
- [x] `EXPLAIN ANALYZE SELECT * FROM acta WHERE estado = 'ENVIADA' LIMIT 20;`
  - Debe decir: "Index Scan using ix_acta_estado"
- [x] `EXPLAIN ANALYZE SELECT ... FROM acta_detalle JOIN acta ...;` (seq scan a escala del set; ver nota)
  - Debe decir: "Index Scan using ix_acta_detalle_acta_id"

---

## B. Backend: ORM Optimization (P0)

### B.1 Cambiar Eager → Lazy Loading

- [x] Archivo: `/backend/.../auth/domain/Usuario.java`
  - Cambiar: `fetch = FetchType.EAGER` → `fetch = FetchType.LAZY`
  - Código:
    ```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    Rol rol;
    ```
- [x] Verificar que NO causa `LazyInitializationException`: todos los consumidores de `getRolNombre()` leen dentro de transaccion; cubierto por `UsuarioLazyBatchIT` y por la suite completa
- [x] Consumidores revisados uno por uno; ninguno accede fuera de transaccion

### B.2 Crear Batch Endpoint: Cargos with Candidatos

- [x] Crear método en `ObserverResource.java` (`autorizador.exigir("acta:read")` + `@RateLimited("observer-jrvs")`, no `@RolesAllowed`)
  ```java
  @GET
  @Path("/cargos-with-candidatos")
  @RolesAllowed({"OBSERVER"})
  public CargosConCandidatosResponse getCargosWithCandidatos(
      @QueryParam("jrvId") Long jrvId) {
    
    List<Cargo> cargos = entityManager.createQuery(
        "SELECT c FROM Cargo c " +
        "LEFT JOIN FETCH c.candidatos " +
        "LEFT JOIN FETCH c.jrvCargos jc WHERE jc.jrv_id = ?1",
        Cargo.class)
        .setParameter(1, jrvId)
        .getResultList();
    
    List<Partido> partidos = entityManager.createQuery(
        "SELECT p FROM Partido p",
        Partido.class)
        .getResultList();
    
    return mapToResponse(cargos, partidos);
  }
  ```
- [x] Crear DTOs: `CargosConCandidatosResponse`, `CargoConCandidatosDto`
- [x] Testing: `ObserverCargosConCandidatosIT` compara el batch contra el endpoint suelto (mismo conjunto, mismo orden); 4/4 verde

---

## C. Frontend: Caching (P1)

### C.1 Implementar Caché Partidos (observer.api.ts)

- [x] Archivo: `observer/data/observer.api.ts`
- [x] Agregar field: `partidosCache$` con `shareReplay(1)`
- [x] Modificar `getCandidatosPorCargo()`
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
- [x] Testing: `observer.api.spec.ts` afirma 1 sola peticion a `/partidos`

### C.2 Implementar Caché Dashboard (dashboard.api.ts)

- [x] Archivo: `dashboard/data/dashboard.api.ts`
- [x] Agregar fields para caché (`shareReplay(1)`)
  ```typescript
  private resultadosCache$: Observable<ResultadoCargo[]> | null = null;
  private progresoCache$: Observable<ProgresoDto[]> | null = null;
  private resumenCache$: Observable<ResumenDto> | null = null;
  private novedadesCache$: Observable<NovedadDto[]> | null = null;
  ```
- [x] Modificar métodos
  ```typescript
  getResultados(): Observable<ResultadoCargo[]> {
    if (!this.resultadosCache$) {
      this.resultadosCache$ = this.http.get<ResultadoCargo[]>(
        `${this.baseUrl}/resultados`
      ).pipe(shareReplay(1));
    }
    return this.resultadosCache$;
  }
  // Repetir para progreso, resumen, novedades
  ```
- [x] Testing: `dashboard.api.spec.ts` afirma que no se cachean los fallos

---

## D. Infrastructure: Compression & Pool (P2)

### D.1 Habilitar HTTP Compression

- [x] Archivo: `application.properties`
- [x] Agregar líneas (`enable-compression`, `compression-level=6`; sin `min-gzip-size`, que no existe en Quarkus 3.40)
  ```properties
  quarkus.http.enable-compression=true
  quarkus.http.compression-level=6
  quarkus.http.min-gzip-size=1024
  ```
- [x] Testing CONFIRMADO contra backend vivo: `content-encoding: gzip` en `/menus/me`, `/admin/usuarios` y `/dashboard/resultados`
- [x] Verificar que respuestas grandes (dashboard, listados) están comprimidas

### D.2 Aumentar Connection Pool

- [x] Archivo: `application.properties`
- [x] Agregar líneas (`quarkus.datasource.jdbc.*`: sin el prefijo `jdbc.` el pool se quedaba en 20)
  ```properties
  # Claves reales de Quarkus 3.40 (las del SDD, sin `jdbc.`, no existen)
  quarkus.datasource.jdbc.min-size=10
  quarkus.datasource.jdbc.max-size=50
  quarkus.datasource.jdbc.acquisition-timeout=30s
  quarkus.datasource.jdbc.idle-removal-interval=5m
  quarkus.datasource.jdbc.max-lifetime=30m
  # Métricas del pool: hacen falta las DOS claves, con una sola no aparece nada
  quarkus.datasource.metrics.enabled=true
  quarkus.datasource.jdbc.metrics.enabled=true
  ```
- [x] Testing CONFIRMADO: 0 eventos de adquisicion fallida en 5000 requests
- [x] Monitoring: metricas `agroal_*` publicadas; uso medio 5-18 %, pico 100 % del pool (Postgres local, sin PgBouncer)

#### Criterio de aceptacion de D.2 (redefinido para la topologia real)

El criterio original ("pool al 60-80 % de ocupacion") **no aplica** en produccion:
Neon pone un **PgBouncer delante de cada compute** (`pool_mode=transaction`), que
multiplexa muchas conexiones del cliente sobre pocas conexiones reales del
compute. El "80 % de ocupacion" del pool de PgBouncer es un numero de PgBouncer,
no del backend, y el backend no lo observa.

El criterio que si aplica y si se puede medir es:

| # | Criterio | Como se mide | Estado |
|---|----------|--------------|--------|
| D.2-a | **0 conexiones esperando** | `agroal_active_count` == `agroal_max_size` sostenido, sin crecimiento; `agroal_blocking_time_max_milliseconds` estable | medido local (max 49 ms) |
| D.2-b | **0 errores de adquisicion** | 0 ocurrencias de "unable to acquire connection" en el log del backend bajo carga | medido local (5000 requests, 0) |
| D.2-c | **Pool cliente chico respecto del compute** | `max-size` x replicas < conexiones utiles del compute, con margen para PgBouncer | **sin medir: requiere Neon** |
| D.2-d | **Cero errores de coneccion tras cold start de Neon** | 0 fallos de `SQLException: connection closed` en la primera hora tras reanudar el compute | **sin medir: post-deploy** |

D.2-a y D.2-b quedan **CONFIRMADOS** contra Postgres local (sin PgBouncer).
D.2-c y D.2-d **exigen Neon** y quedan **PENDIENTES post-deploy**: son la
medicion fina que decide si el pool calibrado en D.2 sirve en produccion.

> Decision del humano: el criterio de D.2 se redefine en vez de dejar el
> "60-80 %" original, que en esta topologia no es observable. No se recalibra
> el pool a ciegas: ver "Riesgo abierto: pool sin calibrar" mas abajo.

---

## E. Testing & Verification (P1)

### E.1 Backend Unit Tests

- [x] `UsuarioLazyBatchIT`: cuenta las sentencias SQL y afirma que resolver los roles no escala con el numero de usuarios
  ```java
  @Test
  void testLazyLoadingNoExtraQueries() {
    // Setup: Set transaction boundaries
    List<Usuario> usuarios = usuarioRepo.findAll();
    entityManager.clear();  // Clear cache
    
    // Acceso a rol debe dispara 1 query (bulk), no N
    // Contar queries con SQLStatementCountValidator
    usuarios.forEach(u -> System.out.println(u.getRol()));
    // Expected: 1 query batch, NOT N queries
  }
  ```

- [x] `IndicesActaIT`: afirma sobre `pg_indexes` que los 6 indices de acta y el de acta_detalle existen con las columnas esperadas
  ```java
  @Test
  void testActaQueriesUseIndices() {
    // Via EntityManager, capture SQL
    List<Acta> actas = repository.findByRegistradaPorAndEstado(100L, "ENVIADA");
    // Verify: SQL has "USING ix_acta_registrada_por" or "USING ix_acta_estado"
  }
  ```

- [x] `ObserverCargosConCandidatosIT`: 4/4 verde, con dataset propio
  ```java
  @Test
  void testCargosWithCandidatos() {
    var response = given()
        .when()
        .get("/observer/cargos-with-candidatos?jrvId=1")
        .then()
        .statusCode(200)
        .body("cargos", hasSize(5))
        .body("cargos[0].candidatos", notNullValue())
        .body("cargos[0].partidos", notNullValue());
  }
  ```

### E.2 Frontend Unit Tests

- [x] `observer.api.spec.ts`: verificar caché partidos
  ```typescript
  it('should cache partidos and reuse', (done) => {
    service.getCandidatosPorCargo(1).subscribe(() => {
      service.getCandidatosPorCargo(2).subscribe(() => {
        // Verify: solo 1 HTTP call para partidos (caché hits second time)
        expect(httpMock.match(url => url.includes('/partidos')).length).toBe(1);
        done();
      });
    });
  });
  ```

- [x] `dashboard.api.spec.ts`: verificar caché dashboard
  ```typescript
  it('should cache resultados across multiple calls', (done) => {
    service.getResultados().subscribe(() => {
      service.getResultados().subscribe(() => {
        // Verify: solo 1 HTTP (caché)
        expect(httpMock.match(url => url.includes('/resultados')).length).toBe(1);
        done();
      });
    });
  });
  ```

### E.3 Integration Tests

- [x] Load test local: 100 usuarios simultáneos x 50 requests (5000), 0 errores, p95 36 ms, 2383 req/s
  ```bash
  # Using Apache JMeter or similar
  - Setup: 50 threads, ramp up 10s, 10 requests each
  - Expected: ALL requests < 2s (P95), 0 timeouts
  - Expected: Connection pool usage ~40 (< 50 max)
  ```

- [x] E2E test: Observer complete flow — **EJECUTADO** contra la API real (ver "Evidencia del E2E" en `verification-report.md`)
  ```
  1. Login → debe ser < 2s
  2. Ver mis-jrvs → debe ser < 1s
  3. Registrar acta (5 cargos) → debe ser < 3s total
  ```

### E.4 Verificación: EXPLAIN ANALYZE (SQL)

- [x] Ejecutar en BD (local, con 50K filas sinteticas):
  ```sql
  EXPLAIN ANALYZE SELECT * FROM acta WHERE registrada_por = 100 LIMIT 20;
  → Debe mostrar "Index Scan" (not Seq Scan)
  
  EXPLAIN ANALYZE SELECT * FROM acta WHERE estado = 'ENVIADA' LIMIT 20;
  → Debe mostrar "Index Scan"
  
  EXPLAIN ANALYZE SELECT ... FROM acta_detalle JOIN acta ...;
  → Debe mostrar índices en uso
  ```

---

## F. Documentation (P2)

- [x] Crear `PERFORMANCE_OPTIMIZATION.md`
  - Qué se cambió
  - Por qué (matemática del problema)
  - Impacto esperado
  - Monitores a observar

- [x] Crear `INDEXING_GUIDE.md`
  - Cuándo crear nuevos índices
  - Cómo verificar con EXPLAIN ANALYZE
  - Reglas: idx_tablename_columnname

- [x] Crear `CACHING_STRATEGY.md`
  - Patrón RxJS shareReplay(1) para caché
  - Cuándo usar (GET requests, datos estáticos)
  - Invalidación manual si es necesario

---

## Implementation Checklist

### Orden Recomendado

```
1. A.1 + A.2 → Crear índices (5 min)
   └─ A.3 → Verificar (5 min)
   
2. B.1 → Lazy loading (2 min)
   └─ Testing para LazyInitializationException (10 min)
   
3. B.2 → Batch endpoint (30 min + tests)
   
4. C.1 + C.2 → Frontend caché (30 min + tests)
   
5. D.1 + D.2 → Infra (5 min)
   
6. E → Testing completo (60 min)
   
7. F → Documentación (20 min)

TOTAL: ~3 horas
```

### Pre-Deployment Checklist

- [x] Unit + integration pasan (298 backend, 1153 frontend + 5 a11y, 152 contract, CI real 9/9 jobs)
- [x] E2E del flujo Observer ejecutado contra la API real (3 proyectos de navegador)
- [x] EXPLAIN ANALYZE verifica indices en uso
- [x] Load test 100 usuarios: P95 36 ms, 0 timeouts
- [x] Connection pool: NO "unable to acquire connection" errors
- [x] Gzip: Response headers tienen content-encoding
- [x] Zero regressions: 298 tests backend + 1153 frontend + contract suite en verde

### Post-Deployment Monitoring

Los objetivos son los que **se pueden observar** en esta topologia. El
"60-80 % de ocupacion del pool" no se puede medir en produccion: PgBouncer
multiplexa las conexiones y ese porcentaje es de PgBouncer, no del backend.
Ver el criterio D.2 redefinido en la seccion D.2.

- [ ] Latencia avg: < 1s (target)
- [ ] Latencia P95: < 2s (target)
- [ ] Conexiones esperando: 0 sostenido (target) — `agroal_active_count` < `max-size` sin growth
- [ ] Errores de adquisicion: 0 (target) — 0 "unable to acquire connection"
- [ ] Conexiones abiertas del backend: por debajo de los slots utiles del compute
- [ ] Errores de conexion tras reanudar el compute de Neon: 0 en la primera hora
- [ ] Database query time avg: < 100ms per query (target)

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Lazy loading causa LazyInitializationException | Tests verifican acceso a rol, refactor consumer code |
| Índices ocupan espacio en disco | Acceptable: ~100MB para 8 índices |
| Batch endpoint rompe existing clients | Mantener endpoints viejos (3 separados) para compatibilidad |
| Caché stale data | Monitorear con tests, manual clear si es necesario |
| Pool exhaustion bajo pico | Aumentar a 100 si P95 latency indica espera |

### Riesgo abierto: pool sin calibrar para Neon

> Decision del humano: **el pool se deja como esta** (no se recalibra a ciegas sin
> medir contra Neon) y el riesgo queda **documentado**. El cambio de valores es
> post-deploy, con metricas en la mano.

**Riesgo 1 — `max-size=50` esinnecesario con PgBouncer delante.**
Neon coloca un PgBouncer (`pool_mode=transaction`) delante de cada compute. Con
multiplexacion, 50 conexiones del backend se translated en un puñado de conexiones
reales al compute. Un pool de 50 no compra concurrencia: compra 50 sockets TCP
abiertos y 50 reserves de memoria del pool. El limite real lo pone
`default_pool_size` de PgBouncer (`0.9 x max_connections` del compute), no el
`max-size` del backend.

**Riesgo 2 — `min-size=10` mantiene 10 sockets abiertos justo cuando Neon
suspende.** En los planes Free/Launch, Neon **suspende el compute tras ~5 min de
inactividad**. `idle-removal-interval=5m` cierra las conexiones del pool
*exactamente* en esa ventana: el backend queda con sockets que apuntan a un
compute que se acaba de despertar (o se acaba de morir). El sintoma no es "el pool
se agota" sino errores transitorios de conexion en la primera transaccion
despues del cold start, mientras el backend reintenta.

**Riesgo 3 — la reanudacion de Neon puede(findar "connection slots").** El
compute de 0.25 CU expone `max_connections=112` (105 utiles segun la
documentacion de Neon). Con `min-size=10` el backend reserva 10 de esos slots
permanentemente, y PgBouncer mantiene su propio pool de conexiones al compute
(`default_pool_size`). Si el compute se reanuda con slots ya reservados, el
backend puede fallar al abrir conexiones nuevas con un error de slots
reservados, no de carga.

**Compatibilidad verificada (sin accion requerida).** El backend es compatible
con PgBouncer: el RLS usa `set_config('app.current_user_id', ..., true)` —que es
`SET LOCAL` y PgBouncer lo soporta en transaction mode—, y el codigo **no** usa
advisory locks, `LISTEN`/`NOTIFY`, tablas temporales ni `SET` de sesion. Los
prepared statements del protocolo de pgjdbc funcionan con
`max_prepared_statements=1000`.

**Valores recomendados (para el ajuste post-deploy, NO aplicados en este
commit):**

| Parametro | Valor actual | Valor recomendado | Por que |
|-----------|--------------|-------------------|---------|
| `quarkus.datasource.jdbc.max-size` | 50 | **10–15** | Con PgBouncer el pool del backend no es el cuello de botella; 50 sockets solo agregan memoria y riesgo de agotar slots del compute |
| `quarkus.datasource.jdbc.min-size` | 10 | **1–2** | Evita que el backend retenga slots del compute mientras la base esta suspendida |
| `quarkus.datasource.jdbc.idle-removal-interval` | 5m | **2m** | Cierra sockets antes de la ventana de suspension de Neon (~5 min), en vez de justo en ella |
| `quarkus.datasource.jdbc.max-lifetime` | 30m | **10m** | Rotacion por debajo de la ventana de suspension: el socket nunca llega a la edad de la Suspension |
| `quarkus.datasource.jdbc.acquisition-timeout` | 30s | **5s** | Con el pool chico, esperar 30 s por una conexion es una latencia invisible para el usuario; 5 s falla antes y el reintento con conexion nueva es mas barato |

**Como decidir sin adivinar.** El ajuste se hace con las metricas de Neon
(`/q/metrics` de Quarkus + el pool de PgBouncer) durante la primera ventana de
escrutinio real:

1. Si `agroal_active_count` se queda por debajo de `max-size` y
   `agroal_blocking_time_max_milliseconds` es 0, el pool esta sobredimensionado:
   bajarlo.
2. Si `agroal_active_count` == `max-size` sostenido y grows el blocking time, el
   pool esta corto: subirlo, con el techo en los slots utiles del compute.
3. Si aparecen `SQLException: connection closed` justo despues de reanudar el
   compute, el problema es `min-size` + `idle-removal-interval` contra la
   ventana de suspension, no el tamano del pool.


