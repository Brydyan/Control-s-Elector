# SDD — Caching Strategy: Frontend + Backend Optimization

**Referencia:** T-CACHE-STRAT-01  
**Epic:** Infra › Escalabilidad & Performance  
**Componentes:** Frontend (Observer + Dashboard), Backend (Services + Redis), Infraestructura (Redis setup)  

---

## Propuesta

Implementar estrategia de caching cohesiva para eliminar **requests duplicadas** y **queries innecesarias** en datos estáticos.

**Hoy:**
- Frontend: `getCandidatosPorCargo()` carga `/partidos` sin cache → 5 requests por formulario de 5 cargos
- Frontend: Dashboard cache definido pero NO activado → `getResultados()` hace HTTP cada vez
- Backend: NO existe Redis, NO existe `@Cacheable` → Cargos, Partidos, Candidatos se consultan siempre
- Resultado: Latencia innecesaria + presión en BD

**Después:**
- Frontend: Partidos caché con `shareReplay(1)` → 1 request en toda la sesión
- Frontend: Dashboard métodos usan caché definida → 0 duplicados al cambiar tabs
- Backend: Redis con TTL + `@Cacheable` → 90% menos queries para datos estáticos
- Resultado: -1-2s por operación, -300 queries/min en BD

**Usuarios afectados:** Observers (formularios), Dashboard users (tabs), Admin (listados), Coordinators

---

## Dominio del Problema

| Problema | Causa | Impacto Hoy | Impacto Prod (1410 usuarios) |
|----------|-------|------------|---------------------------|
| `/partidos` sin cache | No usa `shareReplay(1)` | 5 requests × 565ms = 2.8s | Mismo (bloqueante) |
| Dashboard cache inactiva | `getResultados()` no chequea `resultadosCache$` | 1-2s × tabs duplicados | Mismo (bloqueante) |
| Backend sin Redis | No existe `@Cacheable` en servicios | 50 queries/min cargos | 1000 queries/min (escala) |
| Sin TTL invalidation | Datos estáticos cached forever en Redis | N/A (nuevo) | Stale data risk |
| Sin monitoring | No hay visibilidad de cache hit ratio | N/A | Invisible if cache breaks |

---

## Scope

### A. Frontend: Observer Partidos Cache

**Archivo:** `/frontend/app/src/app/features/observer/data/observer.api.ts`

- Agregar field: `private partidosCache$: Observable<PartidoDto[]> | null = null;`
- Modificar `getCandidatosPorCargo()` para usar cache con `shareReplay(1)`
- Validar que primer call → HTTP, resto → cache (1ms)

**Impacto:** -1-2 segundos por formulario de 5+ cargos

---

### B. Frontend: Dashboard Cache Activation

**Archivo:** `/frontend/app/src/app/features/dashboard/data/dashboard.api.ts`

- Activar `getResultados()` → usar `resultadosCache$` existente
- Activar `getProgreso()` → usar `progresoCache$` existente
- Activar `getNovedades()` → crear `novedadesCache$` + usar
- Activar `getResumen()` → crear `resumenCache$` + usar
- Validar que cambiar tabs → 0 HTTP requests nuevas (caché hit)

**Impacto:** -1s por cambio de tab (hoy: cada tab hace HTTP)

---

### C. Backend: Redis Architecture

**Archivo:** `application.properties` + servicios base

- Configurar Redis en Quarkus:
  ```properties
  quarkus.redis.hosts=localhost:6379
  quarkus.cache.type=redis
  quarkus.cache.redis.expire-after-write=10m
  ```
- Agregar `@Cacheable(cacheName = "cargos")` en `CargoService.findAll()`
- Agregar `@Cacheable(cacheName = "partidos")` en `PartidoService.findAll()`
- Agregar `@Cacheable(cacheName = "candidatos")` en `CandidatoService.findAll()`
- Agregar `@Cacheable` en servicios de geografía (Provincias, Cantones, Parroquias)

**Impacto:** -90% queries para datos estáticos

---

### D. Infrastructure: TTL & Invalidation

- TTL por default: 10 minutos (datos estáticos no cambian en sesión)
- Invalidation manual: Endpoint `/admin/cache/clear` para limpieza forzada (admin only)
- Monitoring: Métricas de Redis (memory, keys count, TTL)

---

### E. Testing & Verification

**Frontend Unit Tests:**
- Partidos cache: 2 calls → 1 HTTP request
- Dashboard cache: cambiar tabs → 0 HTTP nuevas
- Regresión: Datos correctos post-cache (no stale data)

**Backend Unit Tests:**
- CargoService.findAll() cached: 2 calls → 1 BD query
- PartidoService.findAll() cached: múltiples usuarios, hit ratio > 90%

**Integration Tests:**
- E2E: Observer form 5 cargos → <1s (antes: 2-3s)
- Dashboard: 4 tabs → <0.5s (antes: 4-5s)

**Load Test:**
- 100 usuarios simultáneos, 10 requests cada uno
- Expected P95: < 2s (mejorado vs performance SDD baseline)
- Expected hit ratio: > 90%

---

### F. Documentation

- `CACHING_STRATEGY.md` — Patrón RxJS `shareReplay(1)`, cuándo usar
- `REDIS_SETUP_GUIDE.md` — Configuración local/producción, TTL, monitoring
- `CACHE_INVALIDATION.md` — Manual clear endpoint, event-driven future (Phase 2)

---

## Decisiones de Diseño

**D1 — ¿Frontend: RxJS shareReplay vs HttpClient Interceptor?**
- Opción A: HttpClient Interceptor global (automático pero opaco)
- Opción B: Service-level `shareReplay(1)` (explícito, predecible)
- **Elegida:** B. Mejor control. Fase 2: Considerar interceptor si patrones se repiten en 50+ endpoints.

**D2 — ¿Backend: Redis vs In-Memory Cache?**
- Opción A: In-Memory con `@Cacheable` (simple, pero cada réplica = cache separada)
- Opción B: Redis compartido (sincronizado entre réplicas, escalable)
- **Elegida:** B. Con 3+ réplicas de backend, Redis evita cache splits y stale data.

**D3 — ¿TTL: Fijo vs Event-driven Invalidation?**
- Opción A: TTL fijo 10 min (simple, predecible, puede servir stale data)
- Opción B: Event-driven (complicado, real-time, requiere pub/sub)
- **Elegida:** A. Datos estáticos no cambian en sesión. Fase 2: Event-driven para mutations.

**D4 — ¿Invalidation: Automática vs Manual?**
- Opción A: Solo TTL (sin control admin)
- Opción B: Endpoint `/admin/cache/clear` para limpieza forzada
- **Elegida:** B. Admin puede forzar clear si datos corruptos. TTL maneja invalidación automática.

**D5 — ¿Monitoring: Qué métricas?**
- Hit ratio por cacheName (cargos, partidos, candidatos, geografía)
- Memory usage de Redis (target: <100MB)
- Latencia pre/post cache (baseline vs optimized)

---

## Próximos Pasos

✅ **Documento SDD listo en:** `/openspec/changes/back/2026-11-19-caching-strategy/`

→ **Pasar a Arquitecto para feedback** en D1-D5:
- D1: ¿Service-level shareReplay o interceptor global?
- D2: ¿Redis compartido o in-memory por réplica?
- D3: ¿TTL fijo o event-driven desde el inicio?
- D4: ¿Endpoint de clear manual o solo TTL?
- D5: ¿Qué alertas para Redis memory?

**Impacto estimado post-implementación:**
- Frontend: -1-2s por operación (formularios observer, dashboard tabs)
- Backend: -90% queries para datos estáticos
- Latencia P95: 2.5s → 1.5s (40% mejora sobre Performance SDD baseline)
- Throughput: +20% (menos presión en BD)
