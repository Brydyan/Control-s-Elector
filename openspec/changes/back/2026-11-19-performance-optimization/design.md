# Design — Performance Optimization: Indexing, Caching & Infrastructure

---

## A. Database Schema Changes

### Índices Faltantes

**Tabla `acta` - Migration SQL:**

```sql
-- Crear índices simples para campos de búsqueda frecuentes
CREATE INDEX ix_acta_registrada_por ON acta(registrada_por);
CREATE INDEX ix_acta_estado ON acta(estado);
CREATE INDEX ix_acta_jrv_id ON acta(jrv_id);
CREATE INDEX ix_acta_cargo_id ON acta(cargo_id);
CREATE INDEX ix_acta_fecha_desc ON acta(fecha_registro DESC);

-- Índice compuesto para queries complejas (QueryDSL/JPA)
CREATE INDEX ix_acta_jrv_cargo_estado ON acta(jrv_id, cargo_id, estado);

-- Verificar (debe retornar todos los índices creados)
SELECT indexname FROM pg_indexes WHERE tablename = 'acta';
```

**Tabla `acta_detalle` - Migration SQL:**

```sql
-- Indexar foreign keys y campos de búsqueda
CREATE INDEX ix_acta_detalle_acta_id ON acta_detalle(acta_id);
CREATE INDEX ix_acta_detalle_candidato_id ON acta_detalle(candidato_id);
```

---

## B. Backend ORM Configuration

### B.1 Cambio Lazy Loading

**Archivo:** `/backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/auth/domain/Usuario.java`

```java
// ANTES:
@ManyToOne(fetch = FetchType.EAGER)  // ← Carga automática (BAD)
@JoinColumn(name = "rol_id", nullable = false)
Rol rol;

// DESPUÉS:
@ManyToOne(fetch = FetchType.LAZY)   // ← Carga bajo demanda (GOOD)
@JoinColumn(name = "rol_id", nullable = false)
Rol rol;
```

**Impacto:**
```
BEFORE: 
  Query 1: SELECT * FROM usuario WHERE id = ?
  Query 2-21: SELECT * FROM rol WHERE id = ? (automático por cada usuario)
  Total: 21 queries para 20 usuarios
  
AFTER:
  Query 1: SELECT * FROM usuario WHERE id = ?
  Query 2: SELECT * FROM rol WHERE id IN (...)  (1 query bulk, si accedes a rol)
  Total: 2 queries para 20 usuarios (90% reduction)
```

---

### B.2 Batch Endpoint: Cargos con Candidatos

**Crear nuevo endpoint:** `/api/v1/observer/cargos-with-candidatos?jrvId={id}`

**Implementación:**

```java
// Archivo: ObserverResource.java (nuevo método)

@GET
@Path("/cargos-with-candidatos")
@RolesAllowed({"OBSERVER"})
public CargosConCandidatosResponse getCargosWithCandidatos(
    @QueryParam("jrvId") Long jrvId) {
    
    // 1 Query: SELECT cargos con relaciones
    List<Cargo> cargos = entityManager.createQuery(
        "SELECT c FROM Cargo c " +
        "LEFT JOIN FETCH c.candidatos " +
        "LEFT JOIN FETCH c.jrvCargos jc WHERE jc.jrv_id = ?1",
        Cargo.class)
        .setParameter(1, jrvId)
        .getResultList();
    
    // 1 Query: SELECT todos los partidos (caché)
    List<Partido> partidos = entityManager.createQuery(
        "SELECT p FROM Partido p",
        Partido.class)
        .getResultList();
    
    // Mapear a DTO
    return new CargosConCandidatosResponse(
        cargos.stream()
            .map(c -> new CargoWithCandidatos(
                c.getId(),
                c.getNombre(),
                c.getCandidatos(),
                partidos
            ))
            .collect(toList())
    );
}
```

**DTO Response:**

```java
class CargosConCandidatosResponse {
    List<CargoWithCandidatos> cargos;
}

class CargoWithCandidatos {
    Long id;
    String nombre;
    List<CandidatoDto> candidatos;
    List<PartidoDto> partidos;  // Included, no need separate call
}
```

**Comparación (antes vs después):**

```
ANTES (3 requests separadas + N+1):
  GET /cargos?jrvId=1 → 580ms
  GET /candidatos?cargoId=1 → 565ms
  GET /partidos → 566ms
  GET /candidatos?cargoId=2 → 550ms
  GET /partidos → 566ms (duplicate)
  ...
  Total: 5 requests × 565ms avg = 2.8s + N+1 queries en BD
  
DESPUÉS (1 request):
  GET /cargos-with-candidatos?jrvId=1 → 580ms (similar, pero con más data)
  Total: 1 request = 580ms (77% menos requests)
  
Savings: 2.2 segundos por formulario de registro
```

---

## C. Frontend Caching

### C.1 Service-level Cache: Observer API

**Archivo:** `/frontend/app/src/app/features/observer/data/observer.api.ts`

```typescript
// ANTES:
getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
  return forkJoin({
    candidatos: this.http.get<CandidatoDto[]>(
      `${this.configUrl}/candidatos?cargoId=${cargoId}`,
    ),
    partidos: this.http.get<PartidoDto[]>(`${this.configUrl}/partidos`),  // ← No cache
  })
}

// DESPUÉS:
private partidosCache$: Observable<PartidoDto[]> | null = null;

getCandidatosPorCargo(cargoId: number): Observable<CandidatoInfo[]> {
  // Lazy-init cache
  if (!this.partidosCache$) {
    this.partidosCache$ = this.http.get<PartidoDto[]>(
      `${this.configUrl}/partidos`
    ).pipe(shareReplay(1));  // ← Cache: 1st call fetches, rest use cached value
  }
  
  return forkJoin({
    candidatos: this.http.get<CandidatoDto[]>(
      `${this.configUrl}/candidatos?cargoId=${cargoId}`,
    ),
    partidos: this.partidosCache$,  // ← Reuse
  })
}
```

**Comportamiento:**

```
1st call: GET /partidos → HTTP request (565ms) → cache
2nd call: GET /partidos → Cache (1ms) ← No HTTP
3rd call: GET /partidos → Cache (1ms) ← No HTTP
...

Savings: 4 × 565ms = 2.26s (en 5 cargos con partidos)
```

---

### C.2 Service-level Cache: Dashboard API

**Archivo:** `/frontend/app/src/app/features/dashboard/data/dashboard.api.ts`

```typescript
// ANTES:
getResultados(): Observable<ResultadoCargo[]> {
  return this.http.get<ResultadoCargo[]>(`${this.baseUrl}/resultados`);  // ← No cache
}

// DESPUÉS:
private resultadosCache$: Observable<ResultadoCargo[]> | null = null;

getResultados(): Observable<ResultadoCargo[]> {
  if (!this.resultadosCache$) {
    this.resultadosCache$ = this.http.get<ResultadoCargo[]>(
      `${this.baseUrl}/resultados`
    ).pipe(shareReplay(1));
  }
  return this.resultadosCache$;
}

// También aplicar a progreso, resumen, novedades
private progresoCache$: Observable<ProgresoDto[]> | null = null;
private resumenCache$: Observable<ResumenDto> | null = null;
private novedadesCache$: Observable<NovedadDto[]> | null = null;
```

**Comportamiento (Tabs del Dashboard):**

```
Tabs: "Resumen" | "Resultados" | "Progreso" | "Novedades"

ANTES:
  Click Resumen → GET /resumen → HTTP (1.17s)
  Click Resultados → GET /resultados → HTTP (1.69s)
  Click Progreso → GET /progreso → HTTP (2.03s)
  Click Novedades → GET /novedades → HTTP (1.37s)
  Total: 4 requests × ~1.5s = 6s
  
DESPUÉS:
  Click Resumen → GET /resumen → HTTP (1.17s) [cache created]
  Click Resultados → GET /resultados → HTTP (1.69s) [cache created]
  Click Progreso → GET /progreso → HTTP (2.03s) [cache created]
  Click Novedades → GET /novedades → HTTP (1.37s) [cache created]
  Click Resumen again → Cache (1ms) [reuse]
  Total: 4 requests + 1 cache hit = 6.26s → 5.26s (savings: 1s)
```

---

## D. Infrastructure Configuration

### D.1 HTTP Compression

**Archivo:** `/backend/electoral-api-quarkus/src/main/resources/application.properties`

```properties
# HTTP Compression (gzip)
quarkus.http.enable-compression=true
quarkus.http.compression-level=6              # Balance: 6-9 good, default is 4
quarkus.http.min-gzip-size=1024               # Only compress if > 1KB

# Compression reduces response size:
# - 500 KB → 50 KB (10× reduction)
# - 4G latency: 400ms → 40ms (90% improvement)
```

---

### D.2 Connection Pool

**Archivo:** `/backend/electoral-api-quarkus/src/main/resources/application.properties`

```properties
# DataSource Connection Pool (Agroal)

# Pool sizing
quarkus.datasource.min-size=10                # Connections to maintain
quarkus.datasource.max-size=50                # Max concurrent connections
                                              # (1410 users / 28 reqs per user = 50 needed)

# Timeout & lifecycle
quarkus.datasource.jdbc.acquisition-timeout=30s   # Max wait for connection from pool
quarkus.datasource.jdbc.idle-removal-interval=5m  # Reap idle connections
quarkus.datasource.jdbc.max-lifetime=30m          # Max lifetime of connection
```

**Cálculo de Size:**

```
1410 usuarios simultáneos
Promedio 2-3 requests por usuario (concurrent)
= 1410 × 2.5 = 3,525 requests

Pero NO todos simultáneos:
- 70% idle
- 20% waiting
- 10% active

Active: 1410 × 0.1 = 141 usuarios activos
Requests por usuario: 2-3 (picos)
Conexiones por request: 1
+ Buffer: 20%

Needed: 141 × 3 × 1.2 = ~507 conexiones

Pero:
- Requests son cortas (~50-500ms)
- Pool reusa conexiones rápido
- 50-100 conexiones es suficiente

Elegimos: 50 (conservative), monitor y aumentar si alcanza límite
```

---

## E. Verification & Testing

### EXPLAIN ANALYZE Queries

**Verificar que índices se usan (no Seq Scan):**

```sql
-- Query 1: Actas por observer
EXPLAIN ANALYZE 
SELECT * FROM acta 
WHERE registrada_por = $1 
ORDER BY fecha_registro DESC 
LIMIT 20;

-- Expected: "Index Scan using ix_acta_registrada_por"
-- Bad: "Seq Scan on acta"

-- Query 2: Actas por estado
EXPLAIN ANALYZE
SELECT * FROM acta 
WHERE estado = $1 
LIMIT 20;

-- Expected: "Index Scan using ix_acta_estado"

-- Query 3: Dashboard JOIN
EXPLAIN ANALYZE
SELECT ... FROM acta_detalle ad 
LEFT JOIN acta a ON a.id = ad.acta_id 
WHERE a.estado <> 'ANULADA';

-- Expected: "Index Scan using ix_acta_detalle_acta_id on acta_detalle ad"
-- NOT: "Seq Scan on acta_detalle"
```

---

### Load Test: Expected Results

```
Test: 100 usuarios simultáneos, 50 requests cada uno = 5,000 total

BEFORE (without optimizations):
  Response time P95: 10-15s (timeout)
  Response time P99: 20-30s (timeout/error)
  Throughput: ~100 req/s
  Pool exhaustion: YES (20 conn < 300 needed)
  
AFTER (with optimizations):
  Response time P95: 1.5-2s ✓
  Response time P99: 2.5-3s ✓
  Throughput: ~1000 req/s (10× better)
  Pool utilization: ~80% (50 conn sufficient)
```

---

## F. Files Changed Summary

| Archivo | Cambio | Líneas | Prioridad |
|---------|--------|--------|-----------|
| `migration-011-*.sql` | Crear índices | 15 | P0 |
| `Usuario.java` | Eager→Lazy | 1 | P0 |
| `ObserverResource.java` | Agregar `/cargos-with-candidatos` | 25 | P1 |
| `observer.api.ts` | Cachear partidos | 15 | P1 |
| `dashboard.api.ts` | Cachear resultados/progreso/etc | 20 | P1 |
| `application.properties` | Gzip + Pool | 8 | P2 |

---

## G. Rollback Plan

**Si algo falla:**

1. **Revert índices:** `DROP INDEX IF EXISTS ix_acta_*;` (reversible, sin data loss)
2. **Revert Lazy:** `fetch = FetchType.EAGER` (safe, N+1 returns)
3. **Revert caché:** Remove `shareReplay(1)` (safe, just makes requests again)
4. **Revert infra:** Restore `application.properties` (safe, settings only)

**Zero data loss.** Todo es reversible en 5 minutos.

