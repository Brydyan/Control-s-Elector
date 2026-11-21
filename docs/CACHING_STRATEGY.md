# Caching Strategy — T-FE-15

## Overview

Frontend y backend utilizan caché para optimizar request handling de datos estáticos (cargos, partidos, candidatos, geografía). La estrategia reduce latencia y carga de BD de forma dramática.

**Objetivo**: 
- Frontend: 1 HTTP request para múltiples suscriptores (Observer form con 5 cargos: 1 request de partidos + 5 de candidatos = 6 total vs 11 antes)
- Backend: 1 BD query para múltiples requests HTTP (Cargos: 2 HTTP requests → 1 BD query)

---

## Frontend Caching: RxJS shareReplay(1)

### Patrón

```typescript
private cache$: Observable<T[]> | null = null;

getCached(): Observable<T[]> {
  if (!this.cache$) {
    this.cache$ = this.http.get<T[]>(url).pipe(
      shareReplay(1)  // ← Clave
    );
  }
  return this.cache$;
}
```

### Comportamiento

- **Primera suscripción**: HTTP request se envía
- **Subsecuentes suscripciones**: Reciben **la misma respuesta** (desde ReplaySubject)
- **Multicast automático**: Sin crear subjects manualmente
- **Error handling**: `resetOnError: true` → si falla, el siguiente suscriptor reintenta
- **Limpieza**: Manual vía `invalidateCache()` en componentes/tests

### Usado en

| API | Métodos cachados | TTL |
|-----|------------------|-----|
| Observer | `getCandidatosPorCargo()` → partidos | Lifetime |
| Dashboard | `getResumen()`, `getResultados()`, `getProgreso()`, `getNovedades()` | Lifetime |

### Cuándo usar shareReplay(1)

✅ **SÍ**:
- GET requests a datos estáticos (never change during session)
- Múltiples suscriptores a los mismos datos
- Queremos evitar HTTP duplicados

❌ **NO**:
- Datos que cambian (votes, state changes, real-time)
- Mutations/POST/PUT/DELETE
- Si necesitas TTL automático (usar backend cache)

---

## Backend Caching: Quarkus @CacheResult

### Patrón

```java
@ApplicationScoped
public class ListarCargosUseCase {
  
  @CacheResult(cacheName = "cargos")
  @Transactional
  public List<Cargo> execute() {
    return Cargo.listAll();  // 1 BD query
  }
}
```

### Comportamiento

- **Primera invocación**: Ejecuta método, almacena resultado en caché
- **Subsecuentes invocaciones**: Retorna desde caché sin ejecutar
- **TTL**: 10 minutos por defecto (configurable)
- **Key generation**: Automática desde parámetros del método
- **Error handling**: No cachea errores (exception no queda almacenada)

### Implementación

| UseCase | Cache Name | Key | TTL |
|---------|-----------|-----|-----|
| ListarCargosUseCase.execute() | `cargos` | (vacío) | 10m |
| ListarPartidosUseCase.execute() | `partidos` | (vacío) | 10m |
| ListarCandidatosUseCase.execute(Long, Long) | `candidatos` | cargoId + partidoId | 10m |
| ListarGeografiaUseCase.execute() | `geografia` | (vacío) | 10m |

### Backend usado

- **Dev/Test**: Caffeine (in-memory, local a cada instancia)
- **Producción**: Caffeine ahora; Redis en Phase 2 (para multi-instance)

### Configuración (application.properties)

```properties
# Configuración por cache (Caffeine)
quarkus.cache.caffeine.cargos.expire-after-write=10m
quarkus.cache.caffeine.partidos.expire-after-write=10m
quarkus.cache.caffeine.candidatos.expire-after-write=10m
quarkus.cache.caffeine.geografia.expire-after-write=10m
```

### Cuándo usar @CacheResult

✅ **SÍ**:
- UseCase/Service que retorna List o DTO estáticos
- Queremos TTL automático (distinto de lifetime)
- Multi-instance setup (Redis backend en Phase 2)

❌ **NO**:
- Métodos con side effects (logging, eventos)
- Datos específicos del request (userId, sessionId)
- Queremos control fino del cache (usar manual Cache API)

---

## Cache Invalidation

### Manual: Admin Endpoint

```bash
# Limpiar TODOS los caches
curl -X GET http://localhost:8080/api/v1/admin/cache/clear

# Limpiar cache específico
curl -X GET http://localhost:8080/api/v1/admin/cache/clear?cacheName=partidos
```

**Response (200 OK)**:
```json
{
  "success": true,
  "message": "All caches cleared successfully",
  "clearedCaches": ["cargos", "partidos", "candidatos", "geografia"]
}
```

### Automático: TTL

Backend caches se expiran automáticamente después de 10 minutos.

### Frontend: invalidateCache()

```typescript
// En componente o test
this.dashboardApi.invalidateCache();
this.observerApi.invalidateCache();  // si existe
```

---

## Troubleshooting

| Problema | Causa | Solución |
|----------|-------|----------|
| "Datos viejos en frontend" | Cache no expirado | Usar `invalidateCache()` o recargar página |
| "1 observador ve datos nuevos, otro no" | shareReplay sin sync | Ambos suscriptores ven igual (porque multicast) |
| "BD query x2 pese a @CacheResult" | Cache miss por parámetro diferente | Verificar que ListarCandidatosUseCase reciba mismos (cargoId, partidoId) |
| "Memory crecer indefinidamente" | Caché sin límite de tamaño | Configurar size limit en Caffeine (Phase 2) |

---

## Monitoring

### Frontend

Abrir DevTools → Network → filtrar `/partidos`, `/resultados`, etc.
- Si ves múltiples requests al mismo recurso = cache no funciona
- Si ves 1 request para N suscriptores = cache ✓

### Backend

```bash
# Verificar caché activo (Caffeine stats)
# En logs, buscar: CacheManager.getCache("cargos")

# Invalidar desde CLI
curl -X GET http://localhost:8080/api/v1/admin/cache/clear
```

---

## Phase 2: Distributed Caching

Cuando haya múltiples instancias backend:

1. **Cambiar a Redis**: `quarkus-cache-redis` en pom.xml
2. **Config**: 
   ```properties
   quarkus.cache.type=redis
   quarkus.redis.hosts=${REDIS_HOSTS:redis://localhost:6379}
   ```
3. **Mismo código**: `@CacheResult` sin cambios, funciona con Redis automático

---

## References

- [RxJS shareReplay](https://rxjs.dev/api/operators/shareReplay)
- [Quarkus Cache](https://quarkus.io/guides/cache)
- [Caffeine Cache](https://github.com/ben-manes/caffeine)
