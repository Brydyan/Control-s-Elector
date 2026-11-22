# Apply Progress — Caching Strategy: Frontend + Backend Optimization

**SDD:** `T-CACHE-STRAT-01` (`openspec/changes/back/2026-11-19-caching-strategy/`)
**Implementado por:** Builder (`/02-builder-sdd`) + rework de verificación (`/03-verify-sdd`)
**Última actualización:** 2026-11-21

## Estado: PARCIAL — implementacion completa (backend, frontend, tests, metricas, E2E, k6, docs); falta la decision D2 y ejecutar el load test

---

## Resumen de lo entregado

| Bloque | Estado | Evidencia |
|---|---|---|
| A.1 Cache de partidos (observer) | ✅ | `observer.api.ts`: `partidosCache$` + `shareReplay(1)` + `invalidateCache()` |
| A.2 Test de cache de partidos | ✅ | `observer.api.spec.ts` — la mutacion (quitar `shareReplay`) cae el test |
| B.1 Cache del dashboard (4 metodos) | ✅ | `dashboard.api.ts`: `resumen$`, `resultados$`, `progreso$`, `novedades$` |
| B.2 Test de cache del dashboard | ✅ | `dashboard.api.spec.ts` — 4 metodos, mutacion verificada |
| C.1 Configuracion de cache | ⚠️ | Caffeine en memoria, no Redis (ver "Decision D2 pendiente") |
| D.1/D.2 Lectura cacheada | ✅ | 4 caches: `cargos`, `partidos`, `candidatos`, `geografia` (TTL 10m). Acceso explicito con `CacheDeLectura` (no `@CacheResult`: sin el, el hit ratio no se puede medir) |
| D.3 Invalidacion en escritura | ✅ | `@CacheInvalidateAll` en los 21 casos de uso de escritura |
| D.4 Test de invalidacion | ✅ | `CacheInvalidacionIT` — mutacion verificada (sin invalidar, el test cae) |
| E.1 Endpoint de vaciado | ✅ | `AdminCacheResource` — exige MASTER, deja rastro en `audit_log` |
| E.2 Test del vaciado | ✅ | `AdminCacheResourceTest` — 7 tests: matriz de roles, estado post-call, auditoria |
| F.1/F.2 Tests unitarios | ✅ | frontend (`observer.api.spec.ts`, `dashboard.api.spec.ts`) y backend (`CacheServicioIT`, `CacheContenidoIT`, 4 caches) |
| F.3/F.4 E2E | ✅ | `e2e/cache-datos-estaticos.spec.ts` — 2 tests, mutacion verificada |
| F.5 Load test | ✅ (script) / ⏳ (sin ejecutar) | `frontend/tests/load/scenarios/cache-hit-ratio.js` — falta correrlo con el stack arriba |
| F.6 Monitoring | ✅ | micrometer + prometheus: `cache_hits_total` / `cache_misses_total` (`CacheMetricasIT`) |
| G Documentacion | ✅ | `CACHING_STRATEGY.md`, `CACHE_INVALIDATION.md`, `CACHE_SETUP_GUIDE.md` (duplicado de `docs/` borrado) |

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
| `7906806` | `CacheServicioIT`: contador de consultas — la 2a lectura no toca la base (mutacion verificada) |
| `a7d7e0b` | Metricas de hit ratio (micrometer + prometheus) y lectura explicita de la cache |
| `f1ba1b7` | E2E Playwright: formulario del observer (F.3) y tabs del dashboard (F.4) |
| `55d1a7d` | Script k6 de hit ratio con 100 VUs (F.5) |
| `b6835f5` | Referencias `T-FE-15` → `T-CACHE-STRAT-01` en el codigo de este SDD |
| `4ba1ed6` | Invalidacion del frontend cableada + `CacheContenidoIT` + correcciones de doc |
| (verif. v4) | `Cache.as()` lanza `IllegalStateException`: con la cache apagada el backend devolvia 500 |

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

## Desviaciones del diseno (declaradas)

| Desviacion | Diseno | Implementacion | Por que |
|---|---|---|---|
| **C.2** — numero de cachés | 6 cachés (`provincias`, `cantones`, `parroquias` a TTL 1h, aparte de cargos/partidos/candidatos) | **4 cachés**: `cargos`, `partidos`, `candidatos` y `geografia` (TTL 10m) | La geografia se sirve como **un arbol** provincia → canton → parroquia → JRV en una sola consulta (`ListarGeografiaUseCase`); cachear tres listas por separado obligaria a recomponer el arbol en cada lectura y a tres TTL que pueden desincronizarse. El TTL unico de 10m tambien cierra el hueco de la invalidacion cruzada entre réplicas (D2). |
| **D.1/D.2** — mecanismo de lectura | `@CacheResult` | Acceso explicito con `CacheDeLectura` | `@CacheResult` no deja ver si el acierto vino de la cache o de la base: el hit ratio (la metrica que dice si la cache esta sirviendo) quedaria a ciegas. |
| **E.1** — rol del vaciado | `@RolesAllowed({"ADMIN"})` | `Autorizador.exigir("cache:clear", "MASTER")` | `@RolesAllowed` no se usa en ninguna parte del backend; ADMIN no escribe en este dominio y vaciar la cache es una escritura sobre el estado operativo. |

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

- [ ] **D2 (decision humana)**: Redis compartido vs Caffeine en memoria. Sin esto, el codigo y el
      `design.md` se contradicen, y con 2 replicas la cache se parte.
- [ ] Marcar en `design.md` D2 y en `proposal.md` Scope C la decision como PENDIENTE (hoy dicen
      "Elegida: B. Redis"), para que el documento de diseno no afirme una decision que nadie ha
      tomado.
- [ ] **F.5 (ejecucion)**: correr `k6 run scenarios/cache-hit-ratio.js` con el stack arriba (no hay
      Postgres ni Docker en este entorno) y pegar el reporte con p95 y hit ratio reales.
- [ ] Alertas de memoria de la cache (parte de F.6 que no depende de D2).
- [x] Corregir referencias `T-FE-15` → `T-CACHE-STRAT-01` en el codigo de este SDD (endpoint,
      tests de cache, `observer.api.ts`, `dashboard.api.ts`, `application.properties`). Las
      referencias de `dashboard.model.ts` y de las paginas de resultados/progreso se dejan: esas
      vienen del SDD archivado `T-FE-15` de analitica, que si es al que apuntan.
- [ ] Sacar `odd/tasks/invitacion-duplicada-race-condition.md` del commit de cache (`1bf7dcb`): no
      es de este SDD. No se puede deshacer sin reescribir historia (excluido por decision); el
      archivo esta en su carpeta correcta (`odd/tasks/`) y queda en el commit equivocado.

---

## Resultado de las suites

```
frontend  npx vitest run --exclude '**/*.a11y.spec.ts'  → 106 files / 1092 tests, exit 0
backend   ./mvnw -o test                                → 247 tests, BUILD SUCCESS
backend   ./mvnw -o spotless:check                      → sin violaciones
frontend  pnpm format:check && pnpm lint                → sin observaciones
e2e       npx playwright test e2e/cache-datos-estaticos.spec.ts
          → 4 tests (Desktop Chrome + Mobile Chrome), exit 0
```

---

## Metricas de aceptacion (targets del SDD)

| Metrica | Target | Medido |
|---|---|---|
| Cache hit ratio | > 90% | medible (`/q/metrics`); el valor real sale del k6 (F.5, sin ejecutar) |
| Latencia formulario observer | < 1s | no medida (el E2E cuenta peticiones, no tiempos) |
| Latencia tabs dashboard | < 0.5s | no medida (idem) |
| P95 con 100 usuarios | < 2s | sin medicion (F.5 sin ejecutar) |
| Redis / cache memory | < 100MB | sin monitoring (Caffeine: memoria de la JVM) |

---

## Firma

**Builder:** _______________
**QA:** _______________
**Ready for Merge:** [ ] Yes [ ] No
