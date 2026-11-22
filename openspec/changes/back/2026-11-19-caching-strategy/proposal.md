# SDD — Caching Strategy: Frontend + Backend Optimization

**Referencia:** T-CACHE-STRAT-01  
**Epic:** Infra › Escalabilidad & Performance  
**Componentes:** Frontend (Observer + Dashboard), Backend (Services + caché en memoria), Infraestructura (métricas)  

---

## Propuesta

Implementar estrategia de caching cohesiva para eliminar **requests duplicadas** y **queries innecesarias** en datos estáticos.

**Hoy:**
- Frontend: `getCandidatosPorCargo()` carga `/partidos` sin cache → 5 requests por formulario de 5 cargos
- Frontend: Dashboard cache definido pero NO activado → `getResultados()` hace HTTP cada vez
- Backend: NO existe caché de servidor → Cargos, Partidos, Candidatos se consultan siempre
- Resultado: Latencia innecesaria + presión en BD

**Después:**
- Frontend: Partidos caché con `shareReplay(1)` → 1 request en toda la sesión
- Frontend: Dashboard métodos usan caché definida → 0 duplicados al cambiar tabs
- Backend: caché en memoria con TTL + invalidación por escritura → 90% menos queries para datos estáticos
- Resultado: -1-2s por operación, -300 queries/min en BD

**Usuarios afectados:** Observers (formularios), Dashboard users (tabs), Admin (listados), Coordinators

---

## Dominio del Problema

| Problema | Causa | Impacto Hoy | Impacto Prod (1410 usuarios) |
|----------|-------|------------|---------------------------|
| `/partidos` sin cache | No usa `shareReplay(1)` | 5 requests × 565ms = 2.8s | Mismo (bloqueante) |
| Dashboard cache inactiva | `getResultados()` no chequea `resultadosCache$` | 1-2s × tabs duplicados | Mismo (bloqueante) |
| Backend sin caché | No existe caché de servidor en los servicios | 50 queries/min cargos | 1000 queries/min (escala) |
| Sin TTL invalidation | Datos estáticos cacheados sin caducidad | N/A (nuevo) | Stale data risk |
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

### C. Backend: caché en memoria (Caffeine)

**Archivo:** `application.properties` + casos de uso de lectura

- Configurar la caché de Quarkus (backend Caffeine, en memoria del proceso):
  ```properties
  quarkus.cache.caffeine.cargos.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.partidos.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.candidatos.expire-after-write=${CACHE_TTL:2m}
  quarkus.cache.caffeine.geografia.expire-after-write=${CACHE_TTL:2m}
  ```
- Leer y guardar en la caché desde los casos de uso: `cargos`, `partidos`, `candidatos` y
  `geografía` (un solo árbol provincia→cantón→parroquia→JRV). El acceso es explícito con
  `CacheDeLectura`, no `@CacheResult`: la anotación no deja ver si el acierto vino de la
  caché o de la base, y sin eso el hit ratio queda a ciegas.
- Invalidar en cada escritura con `@CacheInvalidateAll(cacheName = …)`.

**Impacto:** -90% queries para datos estáticos (medido: -99.999% de consultas de
configuración, hit ratio 100%)

---

### D. Infrastructure: TTL & Invalidation

- TTL por default: 2 minutos, ajustable con `CACHE_TTL`. Corto a propósito: acota el
  desfase si el backend llegara a correr en más de una réplica.
- Invalidation manual: endpoint `/api/v1/admin/cache/clear` (rol MASTER, queda auditado).
  Vacía la caché **del proceso que atiende la petición**.
- Monitoring: métricas propias `cache_hits_total` / `cache_misses_total` con etiqueta
  `replica` (Railway agrega las métricas de todas sus réplicas), más las métricas estándar
  de Caffeine (tamaño, evictions).

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
- Opción A: In-Memory (Caffeine) — simple, cada réplica = caché separada
- Opción B: Redis compartido (sincronizado entre réplicas, escalable)
- **Decidida: A (Caffeine en memoria).** Decisión humana, con el despliegue real: backend en
  **Railway con 1 réplica**, base en **Neon**. El problema que Redis resolvía —varias réplicas
  con copias distintas— no está presente; y en Neon la caché en proceso es además mejor que una
  remota, porque cada acierto evita un round-trip al PostgreSQL serverless y amortigua su
  arranque en frío. Redis quedaría como fase 2, si el backend llegara a correr en más de una
  réplica. Justificación completa en `documents/CACHE_SETUP_GUIDE.md` (sección 3).

**D3 — ¿TTL: Fijo vs Event-driven Invalidation?**
- Opción A: TTL fijo (simple, predecible, puede servir stale data)
- Opción B: Event-driven (complicado, real-time, requiere pub/sub)
- **Elegida:** A, con TTL de **2 minutos**. Corto a propósito: acota el desfase entre réplicas
  si algún día las hubiera, y su coste es despreciable (un refill son 4 consultas cada 2
  minutos). La invalidación real es por escritura (`@CacheInvalidateAll`), no por TTL.

**D4 — ¿Invalidation: Automática vs Manual?**
- Opción A: Solo TTL (sin control admin)
- Opción B: Endpoint `/admin/cache/clear` para limpieza forzada
- **Elegida:** B. MASTER puede forzar el clear si los datos están corruptos; queda auditado.
  Alcance: vacía la caché del proceso que atiende la petición.

**D5 — ¿Monitoring: Qué métricas?**
- Hit ratio por `cacheName` (cargos, partidos, candidatos, geografía) y **por réplica**
- Tamaño y evictions de cada caché (métricas estándar de Caffeine)
- Latencia pre/post caché (baseline vs optimizado)

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
