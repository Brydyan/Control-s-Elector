# Apply Progress — Caching Strategy: Frontend + Backend Optimization

**SDD:** `T-CACHE-STRAT-01` (`openspec/changes/back/2026-11-19-caching-strategy/`)
**Implementado por:** Builder (`/02-builder-sdd`) + rework de verificación (`/03-verify-sdd`)
**Última actualización:** 2026-11-21

## Estado: PARCIAL — backend y frontend implementados; falta cierre de observabilidad y E2E

---

## Resumen de lo entregado

| Bloque | Estado | Evidencia |
|---|---|---|
| A.1 Cache de partidos (observer) | ✅ | `observer.api.ts`: `partidosCache$` + `shareReplay(1)` + `invalidateCache()` |
| A.2 Test de cache de partidos | ✅ | `observer.api.spec.ts` — la mutacion (quitar `shareReplay`) cae el test |
| B.1 Cache del dashboard (4 metodos) | ✅ | `dashboard.api.ts`: `resumen$`, `resultados$`, `progreso$`, `novedades$` |
| B.2 Test de cache del dashboard | ✅ | `dashboard.api.spec.ts` — 4 metodos, mutacion verificada |
| C.1 Configuracion de cache | ⚠️ | Caffeine en memoria, no Redis (ver "Decision D2 pendiente") |
| D.1/D.2 `@CacheResult` en lectura | ✅ | 4 caches: `cargos`, `partidos`, `candidatos`, `geografia` (TTL 10m) |
| D.3 Invalidacion en escritura | ✅ | `@CacheInvalidateAll` en los 21 casos de uso de escritura |
| D.4 Test de invalidacion | ✅ | `CacheInvalidacionIT` — mutacion verificada (sin invalidar, el test cae) |
| E.1 Endpoint de vaciado | ✅ | `AdminCacheResource` — exige MASTER, deja rastro en `audit_log` |
| E.2 Test del vaciado | ✅ | `AdminCacheResourceTest` — 7 tests: matriz de roles, estado post-call, auditoria |
| F.1/F.2 Tests unitarios | ✅ (frontend) / ⚠️ (backend) | ver "Pendientes" |
| F.3/F.4 E2E | ❌ | sin cobertura Playwright del cache |
| F.5 Load test | ❌ | sin script k6 para cache |
| F.6 Monitoring | ❌ | sin metricas de hit ratio |
| G Documentacion | ⚠️ | 1 de 3 docs (`CACHING_STRATEGY.md`); faltan los otros dos |

---

## Commits

| Commit | Contenido |
|---|---|
| `1bf7dcb` | Cache frontend (observer + dashboard) + config Redis inicial |
| `5f5b25b` | `@CacheResult` + config Caffeine |
| `98f8337` | Endpoint `/api/v1/admin/cache/clear` |
| `c790336` | `CACHING_STRATEGY.md` |
| (rework) | Autorizacion MASTER + auditoria + vaciado real del endpoint |
| (rework) | `@CacheInvalidateAll` en las 21 escrituras + test de invalidacion |
| (rework) | Frontend: sin `done()` deprecado (CI rojo) + `invalidateCache()` en observer |

---

## Correcciones del rework de verificacion

Tres defectos que la verificacion encontro y que este bloque cierra:

1. **El vaciado de cache no vaciaba nada.** `Cache.invalidateAll()` devuelve un `Uni` lazy en
   Quarkus 3.40: sin suscribirse, la invalidacion no se ejecuta. El endpoint contestaba `200 OK`
   sin tocar la cache. Ahora se espera (`await().indefinitely()`) y el test comprueba el estado
   del cache despues del vaciado, no solo el mensaje de respuesta.
2. **El endpoint era publico.** Sin matriz de roles: cualquier usuario autenticado (un OBSERVER)
   podia vaciar todas las caches. Ahora exige `cache:clear` o el rol `MASTER`, con el patron del
   repo (`Autorizador.exigir`), y deja rastro en `audit_log`.
3. **La cache servia dato viejo.** Las escrituras no invalidaban: un cargo nuevo no aparecia en
   el listado hasta que expiraba el TTL (10 min). Ahora cada escritura invalida su cache, y
   `CacheInvalidacionIT` lo comprueba leyendo, escribiendo y volviendo a leer.

Ademas: el CI del frontend estaba en rojo por el `done()` deprecado de vitest 5 (6 errores no
manejados). Resuelto con promesas y `firstValueFrom`; la suite completa sale con codigo 0.

---

## Decision D2 pendiente (decision humana)

El diseno eligio **Redis** (D2, para que las 3+ replicas compartan cache). La implementacion
apunto a **Caffeine en memoria**: `k8s/backend.yaml` tiene `replicas: 2`, asi que cada replica
tendria su propia copia y el dato podria diferir entre ellas durante el TTL.

Estado: el codigo usa Caffeine y `CACHING_STRATEGY.md` lo documenta como "Redis en fase 2".
**Falta** que una persona decida y que `design.md`/`proposal.md` se actualicen para que no
contradigan al codigo.

---

## Pendientes

- [ ] **F.2**: tests de cache por servicio con contador de queries (6 servicios) — la mutacion
      "quitar `@CacheResult`" sigue sin cubrir en el camino del servicio, solo en el de la
      escritura.
- [ ] **F.3/F.4**: E2E Playwright del formulario del observer y de los 4 tabs del dashboard,
      contando peticiones.
- [ ] **F.5**: script k6 con 100 usuarios y reporte de hit ratio.
- [ ] **F.6 / D.5**: metricas de hit ratio por cache (micrometer + prometheus) y alertas de memoria.
- [ ] **G**: `REDIS_SETUP_GUIDE.md` y `CACHE_INVALIDATION.md` (o su equivalente Caffeine).
- [ ] Corregir `docs/CACHING_STRATEGY.md` duplicado: el original esta en `documents/`.
- [ ] Corregir referencias de commit: el SDD es `T-CACHE-STRAT-01`, no `T-FE-15` (colisiona con
      un SDD archivado sin relacion).
- [ ] Sacar `odd/tasks/invitacion-duplicada-race-condition.md` del commit de cache: no es de este
      SDD.

---

## Resultado de las suites

```
frontend  npx vitest run --exclude '**/*.a11y.spec.ts'  → 105 files / 1090 tests, exit 0
backend   ./mvnw -o test                                → 227+ tests, BUILD SUCCESS
backend   ./mvnw -o spotless:check                      → sin violaciones
frontend  pnpm format:check && pnpm lint                → sin observaciones
```

---

## Metricas de aceptacion (targets del SDD)

| Metrica | Target | Medido |
|---|---|---|
| Cache hit ratio | > 90% | sin metricas todavia (F.6) |
| Latencia formulario observer | < 1s | sin medicion E2E |
| Latencia tabs dashboard | < 0.5s | sin medicion E2E |
| P95 con 100 usuarios | < 2s | sin load test |
| Redis / cache memory | < 100MB | sin monitoring |

---

## Firma

**Builder:** _______________
**QA:** _______________
**Ready for Merge:** [ ] Yes [ ] No
