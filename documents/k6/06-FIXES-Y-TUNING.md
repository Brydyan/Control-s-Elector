# 06: Fixes y Tuning — Si Los Tests Fallan

**Status:** Si obtuviste ⚠️ CAUTION o ❌ FAIL en los tests

---

## Por Qué Fallan los Tests

Los tests fallan típicamente por una de estas razones:

```
🔴 ROOT CAUSES (en orden de frecuencia):

1. DB Connection Pool Agotado (40% de casos)
   → max-size muy pequeño
   → Queries lentas bloquean conexiones
   → Fix: Aumentar max-size, optimizar queries

2. CPU/Memory Insuficiente (30% de casos)
   → Pods con replicas insuficientes
   → No hay HPA
   → Fix: Aumentar replicas, agregar HPA

3. Queries Lentas (20% de casos)
   → Falta de índices
   → N+1 queries
   → Complex joins
   → Fix: Optimizar query, agregar índice

4. Falta de Caché (10% de casos)
   → Datos referenciales consultados N veces
   → Fix: Agregar Redis cache
```

---

## Fix 1: Database Connection Pool Tuning

### Síntoma

```
Error: "Timed out waiting for a pooled object"
Pool exhausted: 25/25 conexiones en uso
```

### Causa

```
Agroal pool size (default: 10) es muy pequeño
Con 30 req/min registrando actas (cada uno 150-200ms en lock):
  30 × 150ms = 4.5s de ocupación total
  → Con pool 10: baja concurrencia
  
Con 100 VUs simultáneos:
  Necesitas mínimo 20-30 conexiones concurrentes
```

### Solución

**En `application.properties`:**

```properties
# ANTES:
# (defaults sin configuración explícita)

# DESPUÉS:
quarkus.datasource.jdbc.max-size=35
quarkus.datasource.jdbc.min-size=5
quarkus.datasource.jdbc.idle-removal-interval=10m
quarkus.datasource.jdbc.acquisition-timeout=5000
quarkus.datasource.jdbc.validation-query-sql=SELECT 1
```

### Explicación

```
max-size=35
  → Máximo 35 conexiones simultáneas
  → Anterior: 10
  → Aumento: 3.5x

min-size=5
  → Mínimo 5 conexiones en standby
  → Evita crear conexión nueva en cada request

idle-removal-interval=10m
  → Cerrar conexiones idle después de 10 min
  → Ahorra resources

acquisition-timeout=5000
  → Esperar max 5 segundos por conexión
  → Si no hay disponible, fail fast

validation-query-sql=SELECT 1
  → Verificar conexión está viva antes de usar
  → Evita usar conexión rota
```

### Retest

```bash
# 1. Update application.properties
# 2. Rebuild/redeploy backend
# 3. Rerun pico test
./run.sh pico

# Verificar en logs
grep "Agroal" logs/application.log | tail -5
# Debe mostrar: Pool size: 5-35
```

---

## Fix 2: Kubernetes Horizontal Pod Autoscaling (HPA)

### Síntoma

```
CPU utilization: 85%
p95 latency: 4.8s (near limit)
```

### Causa

```
2 pods × 250m CPU = 500m total
Con pico de 150 VUs + complex queries:
  CPU se satura
  → Sistema no puede procesar más requests
  → Latencias suben
```

### Solución

**En `k8s/backend.yaml`:**

```yaml
# ANTES:
replicas: 2
resources:
  requests:
    cpu: 250m
  limits:
    cpu: 1000m

# DESPUÉS:
replicas: 3  # Aumentar de 2 a 3
autoscaling:
  enabled: true
  minReplicas: 2
  maxReplicas: 8
  targetCPUUtilizationPercentage: 70
  targetMemoryUtilizationPercentage: 75
  
resources:
  requests:
    cpu: 250m
    memory: 512Mi
  limits:
    cpu: 1200m
    memory: 1.2Gi
```

### Explicación

```
replicas: 3
  → Cambiar de 2 a 3 pods inicialmente
  → Reduce per-pod CPU

autoscaling:
  minReplicas: 2
    → Nunca bajar de 2 (redundancia)
  
  maxReplicas: 8
    → Máximo escalar a 8 pods en spike
  
  targetCPUUtilizationPercentage: 70
    → Si CPU > 70%, agregar pod nuevo
    → Mantiene headroom
  
  targetMemoryUtilizationPercentage: 75
    → Si memoria > 75%, agregar pod
```

### Deploy

```bash
# 1. Update k8s/backend.yaml
# 2. Apply:
kubectl apply -f k8s/backend.yaml

# 3. Verify HPA:
kubectl get hpa
# Output: backend-hpa: 2/3 (min/max available)

# 4. Rerun pico test:
./run.sh pico

# 5. Watch scaling:
kubectl get pods -w
# Pods should scale up during peak
```

---

## Fix 3: Query Optimization

### Síntoma

```
Registrar Acta p99: 9.5s (over 8s SLA)
In logs: Query took 2500ms
```

### Causa

```
SELECT ... FROM acta WHERE jrv_id=? AND cargo_id=?
  → Full table scan (no index)
  → Tarda 2-3 segundos en 2500 actas
  
O: SELECT COUNT(*) FROM acta_detalle WHERE acta_id = ?
  → Sin índice en acta_id
  → Tabla crece con cada voto
```

### Solución A: Agregar Índices

**En migration `changelog-indexes.sql`:**

```sql
-- Índices en tabla acta
CREATE INDEX IF NOT EXISTS idx_acta_jrv_cargo 
  ON acta(jrv_id, cargo_id);

CREATE INDEX IF NOT EXISTS idx_acta_estado 
  ON acta(estado);

CREATE INDEX IF NOT EXISTS idx_acta_created_at 
  ON acta(created_at DESC);

-- Índices en tabla acta_detalle
CREATE INDEX IF NOT EXISTS idx_acta_detalle_acta_id 
  ON acta_detalle(acta_id);

-- Índices en tabla evidencia
CREATE INDEX IF NOT EXISTS idx_evidencia_acta_id 
  ON evidencia(acta_id);

-- Analizar tabla para optimizador
ANALYZE acta;
ANALYZE acta_detalle;
```

### Solución B: Optimizar Consulta

**Antes (lento):**
```java
@Query("""
  SELECT a FROM Acta a 
  JOIN FETCH a.detalles d
  WHERE a.jrvId = :jrvId AND a.cargoId = :cargoId
""")
```

**Después (rápido - batch queries):**
```java
@Query("SELECT a FROM Acta a WHERE a.jrvId = :jrvId AND a.cargoId = :cargoId")
Acta findByJrvAndCargo(Long jrvId, Long cargoId);

// Luego lazy-load detalles si necesario
// O use @EntityGraph para fetch strategy
```

### Retest

```bash
# 1. Run migration to add indexes
./mvnw liquibase:update

# 2. Restart backend
# 3. Rerun pico test:
./run.sh pico

# Verify in logs:
grep "Query took" logs/application.log
# Should now show: Query took 150-300ms (vs 2500ms before)
```

---

## Fix 4: Caché Backend (Referential Data)

### Síntoma

```
Dashboard p95: 4.2s (acceptable, but could be better)
Frequent: SELECT * FROM candidato WHERE cargo_id = ?
  → Same data queried 100+ times
```

### Causa

```
Datos referenciales (JRV, candidatos, cargos) nunca cambian
Pero se consultan en cada request
→ Load innecesaria en BD
```

### Solución: Redis Cache

**En `application.properties`:**

```properties
quarkus.redis.hosts=redis://localhost:6379
quarkus.cache.type=redis
```

**En código (Spring @Cacheable pattern):**

```java
@Service
public class CandidatoService {
  
  @Cacheable("candidatos")
  public List<Candidato> getCandidatos(Long cargoId) {
    return repository.findByCargoId(cargoId);
  }
  
  @CacheEvict(value = "candidatos", allEntries = true)
  public void invalidateCache() {
    log.info("Cache invalidated");
  }
}
```

**TTL Configuration:**

```properties
quarkus.cache.redis.default.ttl=8h
quarkus.cache.redis.jrvs.ttl=24h
quarkus.cache.redis.candidatos.ttl=8h
```

### Impact

```
Before: 100 requests × 1 DB query = 100 queries
After:  1 cache query + 99 cache hits = 1 query

Result: 99% reduction en queries referencial data
→ BD load drasticamente reducida
→ Latencia general mejora 10-20%
```

### Retest

```bash
# 1. Add Redis to docker-compose.yaml
# 2. Implement caching code
# 3. Restart backend
# 4. Rerun pico test:
./run.sh pico

# Monitor cache hits:
redis-cli
> MONITOR
# (Verá que hits aumentan after primer request)
```

---

## Fix Priority Matrix

| Fix | Impact | Effort | Time | Priority |
|-----|--------|--------|------|----------|
| Pool Tuning | ⭐⭐⭐ | ⭐ | 30 min | 🔴 1 |
| HPA/Replicas | ⭐⭐⭐ | ⭐⭐ | 1 hour | 🔴 2 |
| Query Optimization | ⭐⭐ | ⭐⭐⭐ | 3 hours | 🟠 3 |
| Redis Cache | ⭐⭐ | ⭐⭐⭐⭐ | 4-5 hours | 🟡 4 |

---

## Recommended Fix Order

### If you have 24 hours:
```
1. Pool Tuning (30 min) → Retest
2. HPA/Replicas (1h) → Retest
3. Query Optimization (2h) → Retest
→ Should pass tests by hour 4-5
```

### If you have 1 week:
```
1. Pool Tuning (30 min)
2. HPA/Replicas (1h)
3. Query Optimization (2h)
4. Redis Cache (4h)
→ Performance optimized
```

### If you have 1 hour (emergency):
```
1. Only: Pool Tuning (30 min) → Retest
→ Should reduce failures from 15% to 8-10%
→ Still not ideal, but better
```

---

## Monitoring During Fixes

**Watch these metrics:**

```bash
# Terminal 1: Backend logs
tail -f logs/application.log | grep -i "duration\|error\|pool"

# Terminal 2: K8s pods
kubectl get pods -w

# Terminal 3: DB connections
watch -n 1 'psql -c "SELECT count(*) FROM pg_stat_activity;"'

# Terminal 4: k6 test
./run.sh pico
```

---

## Rollback if Fix Breaks

```bash
# If fix causes worse results:

# 1. Revert change
git revert HEAD

# 2. Rebuild
./mvnw clean build

# 3. Redeploy
kubectl apply -f k8s/backend.yaml

# 4. Verify
./run.sh baseline  # Check health first
./run.sh pico      # Check pico performance
```

---

## Success Criteria After Fixes

```
✓ p95 latency < 5s
✓ p99 latency < 8s
✓ Failures < 10%
✓ CPU < 80%
✓ Pool has headroom (< 80% utilization)

If all ✓ → Ready for Go Live
```

---

**Last Updated:** 2026-11-05
