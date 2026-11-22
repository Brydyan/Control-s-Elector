# ODD — Phase 2: Distributed Caching with Valkey

**Estado:** Propuesta QA Review  
**Fecha:** 2026-11-22  
**Referencia:** Phase 1 (`documents/CACHING_STRATEGY.md`, `openspec/changes/back/2026-11-19-caching-strategy/`)  
**Decisión Licensing:** No Redis (closed source). Valkey (open source, SSPL, Linux Foundation).

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

| Fase | Tarea | Cambios |
|------|-------|---------|
| **A** | Agregar dependencia Valkey (Jedis o Lettuce) | pom.xml |
| **B** | Reescribir `CacheDeLectura` para Valkey | 1 clase (backend/.../shared/cache/CacheDeLectura.java) |
| **C** | Configurar Valkey en `application.properties` | environment variables + defaults |
| **D** | Tests: `CacheDeLecturaBIT` + testcontainers Valkey | 1 test file |
| **E** | Documentar fallback + limpieza de Caffeine config | CACHING_STRATEGY.md + CACHE_SETUP_GUIDE.md |
| **F** | *(Optional)* Load test: Valkey hit ratio vs. Caffeine | scenarios/cache-hit-ratio.js (reutilizable) |

---

## Checklist

- [ ] **A.1** Agregar `quarkus-redis-client` (Quarkus native Redis support)
- [ ] **A.2** Verificar compatibility: Jedis/Lettuce vs. Valkey (drop-in protocol)
- [ ] **B.1** Reescribir `CacheDeLectura.leer()`: Valkey GET en lugar de Caffeine.getIfPresent()
- [ ] **B.2** Reescribir `CacheDeLectura.guardar()`: Valkey SET EX en lugar de Caffeine.put()
- [ ] **B.3** Mantener try-catch para IllegalStateException (fallback a "sin caché")
- [ ] **B.4** Métricas: incrementar contadores manualmente (Valkey no expone hit/miss automático)
- [ ] **C.1** `quarkus.redis.hosts`: por defecto localhost:6379, configurable por env
- [ ] **C.2** TTL: mantener 2 min (compatible con Valkey EXPIRE)
- [ ] **C.3** Validar que `RAILWAY_REPLICA_ID` aún etiqueta métricas
- [ ] **D.1** `CacheDeLecturaValkeyIT`: test unitario con Testcontainers Valkey
- [ ] **D.2** Verificar invalidación por escritura: `@CacheInvalidateAll` aún funciona
- [ ] **E.1** Actualizar `CACHING_STRATEGY.md` Section "Advertencia: Caffeine está acoplado"
- [ ] **E.2** Deprecate Caffeine config en `application.properties` (comentar)
- [ ] **F.1** *(Optional)* Ejecutar load test contra Valkey, comparar con Caffeine baseline

---

## Errores Resueltos

| Error | Criticidad | Fix |
|-------|-----------|-----|
| **Stale cache en réplicas múltiples** | 🔴 | Valkey como shared backend |
| **Licensing (no Redis)** | 🔴 | Valkey (open source) |
| **Acoplamiento Caffeine en helper** | 🟡 | Reescribir a Valkey protocol (igual riesgo que hoy) |

---

## Notas

- **Valkey protocol:** 100% compatible con Redis. Jedis/Lettuce funcionan igual.
- **Fallback:** Si Valkey no está disponible (offline), el helper captura excepción y sigue funcionando sin caché (hit ratio 0 %). Mismo behavior que hoy con Caffeine.
- **Métricas:** Valkey no expone hit/miss automático. El helper va a contar manualmente `cache.hits` y `cache.misses` en `/q/metrics`.
- **Invalidación:** `@CacheInvalidateAll` en Write UseCases sigue igual. El evento va a invalidar Valkey con `DEL *` (scope a la clave).
- **TTL:** Mismo 2 min. Valkey EXPIRE es nativo.
- **Testing:** Testcontainers soporta Valkey como de Redis (mismo image, mismo protocolo).

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

---

## Próximos Pasos

✅ **Listo para `/02-builder-odd odd/tasks/phase2-caching-valkey-distributed.md`**

---

## Referencia

- Phase 1 SDD: `openspec/changes/back/2026-11-19-caching-strategy/`
- Docs: `documents/CACHING_STRATEGY.md`, `documents/CACHE_SETUP_GUIDE.md`
- Current implementation: `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/shared/cache/CacheDeLectura.java`
- Valkey docs: https://valkey.io/
- Jedis: https://github.com/redis/jedis
