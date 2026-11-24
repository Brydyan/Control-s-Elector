# ODD — Phase 2: Distributed Caching with Valkey

**Estado:** Implementado y verificado ✅  
**Fecha:** 2026-11-22  
**Referencia:** Phase 1 (`documents/CACHING_STRATEGY.md`, `openspec/changes/back/2026-11-19-caching-strategy/`)  
**Decisión Licensing:** Valkey (BSD-3-Clause, Linux Foundation). Redis 7.4+ usa RSALv2/SSPL, que no es una licencia OSI; Redis 8+ es tri-license (con AGPLv3). Valkey es un fork de Redis 7.2.4 con el mismo protocolo (RESP).

---

## Propuesta

**Phase 1 (completada):** Caffeine in-memory cache (single-node backend, Railway).

**Phase 2 (este ODD):** Reemplazar Caffeine con Valkey para **distributed caching** cuando el backend escale a múltiples réplicas.

**Síntoma:** Hoy, si Railway despliega 2+ instancias del backend, cada una tiene su Caffeine local. Los cambios en una réplica no invalidan el caché de las otras → data stale en el observador.

**Solución:** Valkey como shared cache tier. Todas las réplicas hablan con el mismo servidor Valkey. TTL, invalidación y métricas funcionan igual que hoy.

---

## Clasificación

| Criterio | Evaluación |
|----------|-----------|
| **New work?** | No — refactor de Phase 1, mismo patrón, otro backend |
| **API change?** | No — `CacheDeLectura.leer/guardar()` signature no cambia |
| **Architecture?** | No — solo swap de storage (Caffeine → Valkey) |
| **Multi-screen?** | No — es backend, abstracción de caché |
| **Completación?** | ✅ Sí — completar estrategia de caché |

**→ ODD (Operational Document)** — refactor acotado, conocido, bajo riesgo.

---

## Scope

| Fase | Tarea | Cambios | Estado |
|------|-------|---------|--------|
| **A** | Agregar soporte de Valkey | pom.xml (`quarkus-redis-cache`, que trae `quarkus-redis-client`) | ✅ |
| **B** | Reescribir `CacheDeLectura` para Valkey | 1 clase (backend/.../shared/cache/CacheDeLectura.java) + 4 use cases | ✅ |
| **C** | Configurar Valkey en `application.properties` | environment variables + defaults | ✅ |
| **D** | Tests: `CacheDeLecturaValkeyIT` + Testcontainers Valkey | 3 test files (Valkey IT, backend caído IT, perfiles) | ✅ |
| **E** | Documentar fallback + limpieza de Caffeine config | CACHING_STRATEGY.md + CACHE_SETUP_GUIDE.md | ✅ |
| **F** | Load test: Valkey hit ratio vs. Caffeine | scenarios/cache-hit-ratio.js (reutilizable) | ✅ |

> **Desvío de A:** el ODD proponía Jedis o Lettuce. La implementación usa la
> extensión oficial `quarkus-redis-cache` sobre `quarkus-redis-client`: es el
> mismo protocolo RESP, evita mantener un cliente propio y conserva
> `@CacheInvalidateAll` vía `CacheManager`. Su estado es *preview* en Quarkus
> 3.40.1 — registrado como riesgo en `CACHE_SETUP_GUIDE.md` §3.2.

---

## Checklist

- [x] **A.1** Agregar `quarkus-redis-client` (Quarkus native Redis support) — vía `quarkus-redis-cache` 3.40.1 (`pom.xml`)
- [x] **A.2** Verificar compatibility: Jedis/Lettuce vs. Valkey (drop-in protocol) — no se usa Jedis ni Lettuce: el cliente es el de Quarkus (Vert.x) sobre RESP. Drop-in verificado contra Valkey real (`valkey/valkey:8-alpine`) en `CacheDeLecturaValkeyIT`
- [x] **B.1** Reescribir `CacheDeLectura.leer()`: Valkey GET en lugar de Caffeine.getIfPresent() — `CacheDeLectura.leerDeRedis()`
- [x] **B.2** Reescribir `CacheDeLectura.guardar()`: Valkey SET EX en lugar de Caffeine.put() — `CacheDeLectura.guardarEnRedis()` + `expire-after-write`
- [x] **B.3** Mantener try-catch para IllegalStateException (fallback a "sin caché") — `catch (Exception)` en ambos caminos; degradación probada en `CacheValkeyCaidaIT`
- [x] **B.4** Métricas: incrementar contadores manualmente (Valkey no expone hit/miss automático) — `cache.hits`/`cache.misses` con etiqueta `cache` y `replica`
- [x] **C.1** `quarkus.redis.hosts`: por defecto localhost:6379, configurable por env — `quarkus.redis.hosts=${REDIS_HOSTS:redis://localhost:6379}`
- [x] **C.2** TTL: mantener 2 min (compatible con Valkey EXPIRE) — `expire-after-write=${CACHE_TTL:2m}` en los 4 caches; TTL verificado en Valkey (`ttl` = 111 s de 120 s)
- [x] **C.3** Validar que `RAILWAY_REPLICA_ID` aún etiqueta métricas — `replica.id=${RAILWAY_REPLICA_ID:local}` + `CacheMetricasIT`
- [x] **D.1** `CacheDeLecturaValkeyIT`: test unitario con Testcontainers Valkey — 4 tests: round-trip con tipo genérico (ids preservados), invalidación, TTL, y otra réplica ve la entrada y su invalidación se propaga
- [x] **D.2** Verificar invalidación por escritura: `@CacheInvalidateAll` aún funciona — `CacheInvalidacionIT` (2 tests) verde
- [x] **E.1** Actualizar `CACHING_STRATEGY.md` Section "Advertencia: Caffeine está acoplado" — sección "Phase 2: Valkey distribuido (reemplaza Caffeine)" + el tipo genérico y el estado preview
- [x] **E.2** Deprecate Caffeine config en `application.properties` (comentar) — bloque Caffeine comentado, queda como referencia histórica
- [x] **F.1** *(Optional)* Ejecutar load test contra Valkey, comparar con Caffeine baseline — `documents/load-test/cache-hit-ratio-summary-valkey.md`: hit ratio 100.00 %, p95 1.07 ms, 0 peticiones fallidas, 4 consultas a la base en 457.160 lecturas

---

## Errores Resueltos

| Error | Criticidad | Fix |
|-------|-----------|-----|
| **Stale cache en réplicas múltiples** | 🔴 | Valkey como shared backend |
| **Licensing (Redis 7.4+ es RSALv2/SSPL, no OSI)** | 🔴 | Valkey (BSD-3-Clause, Linux Foundation) en compose, tests y CI |
| **Acoplamiento Caffeine en helper** | 🟡 | Reescribir a Valkey protocol (igual riesgo que hoy) |

---

## Notas

- **Valkey protocol:** 100% compatible con Redis (RESP). La implementación no usa Jedis ni Lettuce: usa el cliente de Quarkus (`quarkus-redis-client`, Vert.x), que habla el mismo protocolo. Las propiedades se llaman `quarkus.redis.*` porque ese es el nombre de la extensión, no porque el servidor sea Redis.
- **Fallback:** Si Valkey no está disponible (offline), el helper captura excepción y sigue funcionando sin caché (hit ratio 0 %). Mismo behavior que hoy con Caffeine. Probado en `CacheValkeyCaidaIT`.
- **Métricas:** Valkey no expone hit/miss automático. El helper va a contar manualmente `cache.hits` y `cache.misses` en `/q/metrics`.
- **Invalidación:** `@CacheInvalidateAll` en Write UseCases sigue igual. El evento va a invalidar Valkey con `DEL *` (scope a la clave).
- **TTL:** Mismo 2 min. Valkey EXPIRE es nativo.
- **Testing:** Testcontainers levanta la imagen de Valkey (`valkey/valkey:8-alpine`), la misma que usan compose y CI.
- **Tipo genérico en native:** la lectura necesita el tipo concreto (`List<Cargo>` y no `List<Object>`), y la API de `RedisCache` solo expone `Class<V>` o `TypeLiteral<V>`. El helper construye el `TypeLiteral` reflejando sobre `TypeLiteral.actualType`, que funciona en la imagen nativa **porque Quarkus registra ese campo para reflexión**. Es una dependencia implícita: por eso `ci-native.yml` afirma `cache_hits_total > 0` sobre el binario nativo. Ver `CACHE_SETUP_GUIDE.md` §3.1.

---

## Dependencias

- ✅ No depende de Phase 1 en runtime (Phase 1 ya fue a prod con Caffeine)
- ✅ Phase 1 está frozen (no cambios necesarios)
- 📦 Habilita: Escalado horizontal (múltiples réplicas con caché compartida)

---

## Riesgo

- **Bajo:** Abstracción `CacheDeLectura` aisla el swap. Cambio es 1 clase.
- **Testing:** Unit test sobre Valkey (Testcontainers), suite existente no cambia.
- **Fallback:** Helper ya tiene try-catch; funcionamiento degradado si Valkey falla.
- **Extensión en preview:** `quarkus-redis-cache` está en *preview* en Quarkus 3.40.1. Riesgo de mantenimiento ante una actualización de Quarkus, no de runtime.
- **Reflexión en native:** la deserialización genérica depende de que Quarkus registre `TypeLiteral.actualType` para reflexión. Verificado funcionando en la imagen nativa, y protegido por una aserción en `ci-native.yml`.

---

## Verificación

**Reporte completo:** `odd/tasks/phase2-caching-valkey-distributed.verification-report.md`

| Evidencia | Resultado |
|---|---|
| Suite de caché del backend | 27 → **34 tests** (7 nuevos: 4 en `CacheDeLecturaValkeyIT`, 3 en `CacheValkeyCaidaIT`), 0 fallos |
| Suite completa del backend (`mvn clean verify`) | **296 tests**, 0 fallos, cobertura OK |
| Suite de contrato contra el **binario nativo** | **152 tests**, 0 fallos |
| Backend real de caché | `io.quarkus.cache.redis.runtime.RedisCacheImpl` (confirmado por sonda) |
| Round-trip en native | `cache_hits_total{cache="partidos",replica="local"} = 1`, TTL 111 s, ids preservados |
| Load test (F.1) | hit ratio **100.00 %**, p95 **1.07 ms**, 0 fallos, 4 consultas a la base en 457.160 lecturas |
| `spotless:check` | BUILD SUCCESS |

---

## Próximos Pasos

✅ **Implementado y verificado.** Listo para `/02-builder-odd` (cerrado) → merge.

Pendiente de decisión de producto (no bloquea el código):
- **Escalado horizontal:** Phase 2 habilita compartir la cache entre réplicas, pero Railway sigue con 1 réplica. El benefit (invalidación entre réplicas) se vuelve real cuando haya 2+.

---

## Referencia

- Phase 1 SDD: `openspec/changes/back/2026-11-19-caching-strategy/`
- Reporte de verificación: `odd/tasks/phase2-caching-valkey-distributed.verification-report.md`
- Docs: `documents/CACHING_STRATEGY.md`, `documents/CACHE_SETUP_GUIDE.md`
- Load test Phase 2: `documents/load-test/cache-hit-ratio-summary-valkey.md`
- Current implementation: `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/shared/cache/CacheDeLectura.java`
- Tests nuevos: `shared/cache/CacheDeLecturaValkeyIT.java`, `shared/cache/CacheValkeyCaidaIT.java`
- Valkey docs: https://valkey.io/
- Licencias comparadas: https://redis.io/legal/licenses/
