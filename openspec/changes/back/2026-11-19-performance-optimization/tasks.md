# Tasks — Performance Optimization: Indexing, Caching & Infrastructure

---

## A. Database Indexing (P0 — Crítica)

### A.1 Crear Migration: Índices Tabla `acta`

- [ ] Crear archivo: `backend/.../changelog/011-add-acta-indices.sql`
- [ ] Script: Crear 5 índices simples + 1 compuesto
  ```sql
  CREATE INDEX ix_acta_registrada_por ON acta(registrada_por);
  CREATE INDEX ix_acta_estado ON acta(estado);
  CREATE INDEX ix_acta_jrv_id ON acta(jrv_id);
  CREATE INDEX ix_acta_cargo_id ON acta(cargo_id);
  CREATE INDEX ix_acta_fecha_desc ON acta(fecha_registro DESC);
  CREATE INDEX ix_acta_jrv_cargo_estado ON acta(jrv_id, cargo_id, estado);
  ```
- [ ] Testing local: `SELECT indexname FROM pg_indexes WHERE tablename = 'acta'` → 6 índices visible
- [ ] No errores en BD

### A.2 Crear Migration: Índices Tabla `acta_detalle`

- [ ] Crear archivo: `backend/.../changelog/012-add-acta-detalle-indices.sql`
- [ ] Script: Crear 2 índices
  ```sql
  CREATE INDEX ix_acta_detalle_acta_id ON acta_detalle(acta_id);
  CREATE INDEX ix_acta_detalle_candidato_id ON acta_detalle(candidato_id);
  ```
- [ ] Testing local: Verificar índices creados
- [ ] EXPLAIN ANALYZE: Dashboard query debe usar índices

### A.3 Verificación: EXPLAIN ANALYZE

- [ ] `EXPLAIN ANALYZE SELECT * FROM acta WHERE registrada_por = 100 LIMIT 20;`
  - Debe decir: "Index Scan using ix_acta_registrada_por" (NO "Seq Scan")
- [ ] `EXPLAIN ANALYZE SELECT * FROM acta WHERE estado = 'ENVIADA' LIMIT 20;`
  - Debe decir: "Index Scan using ix_acta_estado"
- [ ] `EXPLAIN ANALYZE SELECT ... FROM acta_detalle JOIN acta ...;`
  - Debe decir: "Index Scan using ix_acta_detalle_acta_id"

---

## B. Backend: ORM Optimization (P0)

### B.1 Cambiar Eager → Lazy Loading

- [ ] Archivo: `/backend/.../auth/domain/Usuario.java` (línea 50)
  - Cambiar: `fetch = FetchType.EAGER` → `fetch = FetchType.LAZY`
  - Código:
    ```java
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rol_id", nullable = false)
    Rol rol;
    ```
- [ ] Verificar que NO causa `LazyInitializationException` (ver testing)
- [ ] Si accedes a `rol` fuera de transacción → error (es EXPECTED, debe refactorizar consumer code)

### B.2 Crear Batch Endpoint: Cargos with Candidatos

- [ ] Crear método en `ObserverResource.java`:
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
- [ ] Crear DTOs: `CargosConCandidatosResponse`, `CargoWithCandidatos`
- [ ] Testing: Comparar respuesta vs 3 endpoints separados (mismo data, 1 request)

---

## C. Frontend: Caching (P1)

### C.1 Implementar Caché Partidos (observer.api.ts)

- [ ] Archivo: `observer/data/observer.api.ts`
- [ ] Agregar field: `private partidosCache$: Observable<PartidoDto[]> | null = null;`
- [ ] Modificar `getCandidatosPorCargo()`:
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
- [ ] Testing: Llamar `getCandidatosPorCargo(1)` y `getCandidatosPorCargo(2)` → ambas usan 1 HTTP request para partidos

### C.2 Implementar Caché Dashboard (dashboard.api.ts)

- [ ] Archivo: `dashboard/data/dashboard.api.ts`
- [ ] Agregar fields para caché:
  ```typescript
  private resultadosCache$: Observable<ResultadoCargo[]> | null = null;
  private progresoCache$: Observable<ProgresoDto[]> | null = null;
  private resumenCache$: Observable<ResumenDto> | null = null;
  private novedadesCache$: Observable<NovedadDto[]> | null = null;
  ```
- [ ] Modificar métodos:
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
- [ ] Testing: Cambiar entre tabs → solo 1 HTTP por tab (no duplicados)

---

## D. Infrastructure: Compression & Pool (P2)

### D.1 Habilitar HTTP Compression

- [ ] Archivo: `application.properties`
- [ ] Agregar líneas:
  ```properties
  quarkus.http.enable-compression=true
  quarkus.http.compression-level=6
  quarkus.http.min-gzip-size=1024
  ```
- [ ] Testing: Network tab → response headers tienen `content-encoding: gzip`
- [ ] Verificar que respuestas grandes (dashboard, listados) están comprimidas

### D.2 Aumentar Connection Pool

- [ ] Archivo: `application.properties`
- [ ] Agregar líneas:
  ```properties
  quarkus.datasource.min-size=10
  quarkus.datasource.max-size=50
  quarkus.datasource.jdbc.acquisition-timeout=30s
  quarkus.datasource.jdbc.idle-removal-interval=5m
  quarkus.datasource.jdbc.max-lifetime=30m
  ```
- [ ] Testing: Logs no muestran "unable to acquire connection" (timeout errors)
- [ ] Monitoring: Verificar pool usage bajo carga (target: 60-80%)

---

## E. Testing & Verification (P1)

### E.1 Backend Unit Tests

- [ ] `UsuarioRepositoryTest`: Verificar que Lazy loading NO dispara extra queries
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

- [ ] `ActaRepositoryTest`: Verificar que queries usan índices
  ```java
  @Test
  void testActaQueriesUseIndices() {
    // Via EntityManager, capture SQL
    List<Acta> actas = repository.findByRegistradaPorAndEstado(100L, "ENVIADA");
    // Verify: SQL has "USING ix_acta_registrada_por" or "USING ix_acta_estado"
  }
  ```

- [ ] `BatchEndpointTest`: Verificar `/cargos-with-candidatos`
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

- [ ] `ObserverApiTest`: Verificar caché partidos
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

- [ ] `DashboardApiTest`: Verificar caché dashboard
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

- [ ] Load test local: 50 usuarios simultáneos, 10 requests cada uno
  ```bash
  # Using Apache JMeter or similar
  - Setup: 50 threads, ramp up 10s, 10 requests each
  - Expected: ALL requests < 2s (P95), 0 timeouts
  - Expected: Connection pool usage ~40 (< 50 max)
  ```

- [ ] E2E test: Observer complete flow
  ```
  1. Login → debe ser < 2s
  2. Ver mis-jrvs → debe ser < 1s
  3. Registrar acta (5 cargos) → debe ser < 3s total
  ```

### E.4 Verificación: EXPLAIN ANALYZE (SQL)

- [ ] Ejecutar en BD (post-deployment):
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

- [ ] Crear `PERFORMANCE_OPTIMIZATION.md`
  - Qué se cambió
  - Por qué (matemática del problema)
  - Impacto esperado
  - Monitores a observar

- [ ] Crear `INDEXING_GUIDE.md`
  - Cuándo crear nuevos índices
  - Cómo verificar con EXPLAIN ANALYZE
  - Reglas: idx_tablename_columnname

- [ ] Crear `CACHING_STRATEGY.md`
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

- [ ] Todos los tests pasan (unit + integration + E2E)
- [ ] EXPLAIN ANALYZE verifica índices en uso
- [ ] Load test 50 usuarios: P95 < 2s, 0 timeouts
- [ ] Connection pool: NO "unable to acquire connection" errors
- [ ] Gzip: Response headers tienen content-encoding
- [ ] Zero regressions: observador can still register actas, admin can still manage

### Post-Deployment Monitoring

- [ ] Latencia avg: < 1s (target)
- [ ] Latencia P95: < 2s (target)
- [ ] Pool usage: 40-60% (target)
- [ ] Connections waiting: 0 (target)
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

