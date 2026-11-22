# Apply Progress — Caching Strategy: Frontend + Backend Optimization

**SDD:** `T-CACHE-STRAT-01` (`openspec/changes/back/2026-11-19-caching-strategy/`)
**Implementado por:** Builder (`/02-builder-sdd`) + rework de verificación (`/03-verify-sdd`)
**Última actualización:** 2026-11-21

## Estado: COMPLETA — implementacion completa y medida (backend, frontend, tests, metricas, E2E, k6 ejecutado, docs). D2 decidida (Caffeine en memoria). Quedan pendientes: el job de E2E en CI, la migracion a Neon (T-IN-13) y el N.9 de otro SDD

---

## Resumen de lo entregado

| Bloque | Estado | Evidencia |
|---|---|---|
| A.1 Cache de partidos (observer) | ✅ | `observer.api.ts`: `partidosCache$` + `shareReplay(1)` + `invalidateCache()` |
| A.2 Test de cache de partidos | ✅ | `observer.api.spec.ts` — la mutacion (quitar `shareReplay`) cae el test |
| B.1 Cache del dashboard (4 metodos) | ✅ | `dashboard.api.ts`: `resumen$`, `resultados$`, `progreso$`, `novedades$` |
| B.2 Test de cache del dashboard | ✅ | `dashboard.api.spec.ts` — 4 metodos, mutacion verificada |
| C.1 Configuracion de cache | ✅ | Caffeine en memoria, TTL 2 min parametrizado con `CACHE_TTL` (D2 decidida: ver "Decision D2: tomada") |
| D.1/D.2 Lectura cacheada | ✅ | 4 caches: `cargos`, `partidos`, `candidatos`, `geografia` (TTL 2m). Acceso explicito con `CacheDeLectura` (no `@CacheResult`: sin el, el hit ratio no se puede medir) |
| D.3 Invalidacion en escritura | ✅ | `@CacheInvalidateAll` en los 21 casos de uso de escritura |
| D.4 Test de invalidacion | ✅ | `CacheInvalidacionIT` — mutacion verificada (sin invalidar, el test cae) |
| E.1 Endpoint de vaciado | ✅ | `AdminCacheResource` — exige MASTER, deja rastro en `audit_log` |
| E.2 Test del vaciado | ✅ | `AdminCacheResourceTest` — 7 tests: matriz de roles, estado post-call, auditoria |
| F.1/F.2 Tests unitarios | ✅ | frontend (`observer.api.spec.ts`, `dashboard.api.spec.ts`) y backend (`CacheServicioIT`, `CacheContenidoIT`, 4 caches) |
| F.3/F.4 E2E | ✅ | `e2e/cache-datos-estaticos.spec.ts` — 2 tests, mutacion verificada |
| F.5 Load test | ✅ | `frontend/tests/load/scenarios/cache-hit-ratio.js` — **ejecutado**: 100 VUs, p95 0.92 ms, hit ratio 100 %, 4 consultas de configuración a la base (ver "Resultado del load test") |
| F.6 Monitoring | ✅ | micrometer + prometheus: `cache_hits_total` / `cache_misses_total` (`CacheMetricasIT`), etiquetados por `replica` (`RAILWAY_REPLICA_ID`) + metricas estandar de Caffeine (tamaño, evictions) |
| G Documentacion | ✅ | `CACHING_STRATEGY.md`, `CACHE_INVALIDATION.md`, `CACHE_SETUP_GUIDE.md` (duplicado de `docs/` borrado) |
| H TTL y deteccion de split | ✅ | `CacheTTLIT` (el TTL se respeta de verdad) + `replica.id` en las metricas (`CacheMetricasIT`) |

---

## Commits

| Commit | Contenido |
|---|---|
| `1bf7dcb` | Cache frontend (observer + dashboard) + config Redis inicial (sustituida despues por Caffeine: ver "Decision D2: tomada") |
| `5f5b25b` | `@CacheResult` + config Caffeine |
| `98f8337` | Endpoint `/api/v1/admin/cache/clear` |
| `c790336` | `CACHING_STRATEGY.md` |
| `7dc7e78` | Autorizacion MASTER + auditoria + vaciado real del endpoint |
| `25026c7` | `@CacheInvalidateAll` en las 21 escrituras + test de invalidacion |
| `fe444aa` | Frontend: sin `done()` deprecado (CI rojo) + `invalidateCache()` en observer |
| `b48f635` | Estado real registrado en este documento |
| `a94712c` | Documentacion de la estrategia de cache |
| `7906806` | `CacheServicioIT`: contador de consultas — la 2a lectura no toca la base (mutacion verificada) |
| `a7d7e0b` | Metricas de hit ratio (micrometer + prometheus) y lectura explicita de la cache |
| `f1ba1b7` | E2E Playwright: formulario del observer (F.3) y tabs del dashboard (F.4) |
| `55d1a7d` | Script k6 de hit ratio con 100 VUs (F.5) |
| `b6835f5` | Referencias `T-FE-15` → `T-CACHE-STRAT-01` en el codigo de este SDD |
| `4ba1ed6` | Invalidacion del frontend cableada + `CacheContenidoIT` + correcciones de doc |
| (verif. v4) | `Cache.as()` lanza `IllegalStateException`: con la cache apagada el backend devolvia 500 |
| `a5a38a9` | Perder la cache es una degradacion, no un 500 (`CacheApagadaIT`, 5 tests) |
| `c36b800` | Load test ejecutado contra el backend real + E2E contra la API real |
| `b005300` | E2E robusto con backend frio + cifras de load test reproducibles |
| `bce9054` | `design.md`/`proposal.md` dejan de afirmar una decision no tomada |
| `ed65253` | D2 cerrada: TTL de 2 min parametrizado + metricas etiquetadas por replica |
| `fbf5a67` | `design.md`/`proposal.md` con D2 como decision tomada + doc de la cache alineada |
| `aa2595d` | Topologia de produccion alineada a Railway + Cloudflare Pages + Neon + R2 |
| `7c6cd81` | Los parsers de metricas (k6 y E2E) toleran la etiqueta `replica` |
| `c6af093` | Mediciones del load test actualizadas con el TTL de 2 min |
| `595f0d9` | `tasks.md` con la seccion de Redis sustituida por la opcion A |
| `27eda74` | `apply-progress.md` con los commits reales de la decision D2 |
| `04d81ed` | `.env.example` a Neon/R2/Railway + purga de restos del diseno viejo |

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
   el listado hasta que expiraba el TTL. Ahora cada escritura invalida su cache, y
   `CacheInvalidacionIT` lo comprueba leyendo, escribiendo y volviendo a leer.

Ademas: el CI del frontend estaba en rojo por el `done()` deprecado de vitest 5 (6 errores no
manejados). Resuelto con promesas y `firstValueFrom`; la suite completa sale con codigo 0.

---

## Desviaciones del diseno original (declaradas)

`design.md` y `proposal.md` ya reflejan la implementacion; esta tabla deja constancia de
como se apartaron del diseno original.

| Desviacion | Diseno | Implementacion | Por que |
|---|---|---|---|
| **D2** — backend de cache | Redis compartido (`quarkus.cache.type=redis`, TTL 10 min) | **Caffeine en memoria**, TTL 2 min (`CACHE_TTL`) | Decision humana: el backend corre en Railway con **1 replica**, asi que el problema que Redis resolvia (copias distintas entre replicas) no existe; en Neon una cache en proceso evita un round-trip al PostgreSQL serverless. Redis queda como fase 2. |
| **C.2** — numero de cachés | 6 cachés (`provincias`, `cantones`, `parroquias` a TTL 1h, aparte de cargos/partidos/candidatos) | **4 cachés**: `cargos`, `partidos`, `candidatos` y `geografia` (TTL 2m) | La geografia se sirve como **un arbol** provincia → canton → parroquia → JRV en una sola consulta (`ListarGeografiaUseCase`); cachear tres listas por separado obligaria a recomponer el arbol en cada lectura y a tres TTL que pueden desincronizarse. |
| **D.1/D.2** — mecanismo de lectura | `@CacheResult` | Acceso explicito con `CacheDeLectura` | `@CacheResult` no deja ver si el acierto vino de la cache o de la base: el hit ratio (la metrica que dice si la cache esta sirviendo) quedaria a ciegas. |
| **E.1** — rol del vaciado | `@RolesAllowed({"ADMIN"})` en `AdminResource` | `Autorizador.exigir("cache:clear", "MASTER")` en `AdminCacheResource` | `@RolesAllowed` no se usa en ninguna parte del backend; ADMIN no escribe en este dominio y vaciar la cache es una escritura sobre el estado operativo. El resource se separo del de administracion general para que la matriz de roles sea explicita. |

---

## Decision D2: tomada (decision humana)

**Caffeine en memoria, TTL de 2 minutos.** El despliegue real es backend en **Railway con 1
replica**, base en **Neon** (PostgreSQL serverless) y frontend en Cloudflare Pages.

Por que no Redis:

- El problema que Redis resolvia —varias replicas con copias distintas— **no existe**: Railway
  corre 1 replica. Redis anadiria un servicio, un coste y un modo de falla a cambio de nada.
- En Neon, una cache en proceso es **mejor** que una remota: cada acierto evita un round-trip
  al PostgreSQL serverless y amortigua su arranque en frio. Redis solo cambiaria un salto de
  red (Neon) por otro (Redis).
- La mitad del frontend (Cloudflare Pages, `shareReplay(1)` en el navegador) es immune a este
  problema: la cache del navegador es por sesion.

Como queda acotado el riesgo si algun dia hubiera mas de una replica:

- TTL de **2 min** (parametrizado con `CACHE_TTL`): el peor desfase entre replicas es el TTL.
- Las metricas de cache van etiquetadas con `replica` (`RAILWAY_REPLICA_ID`): Railway **agrega**
  las metricas de todas sus replicas, asi que sin la etiqueta no se veria si una quedo
  desfasada respecto de las otras. Con la etiqueta, se ve.
- El vaciado manual (`/api/v1/admin/cache/clear`) es **best-effort por replica**: vacia la del
  proceso que atiende la peticion.

Redis queda como **fase 2**, condicionado a que el backend corra en mas de una replica.
Justificacion completa en `documents/CACHE_SETUP_GUIDE.md` (seccion 3).

---

## Pendientes

- [x] **D2 (decision humana)**: Caffeine en memoria con TTL de 2 min. `design.md` y `proposal.md`
      ya la declaran como tomada, con la justificacion y las condiciones de revisitarla.
- [x] Acompanada de lo que la hace segura: TTL parametrizable (`CACHE_TTL`), metricas de cache
      etiquetadas por replica (`RAILWAY_REPLICA_ID`) y guardrail de replicas documentado.
- [x] **F.5 (ejecucion)**: corrida con 100 VUs contra el backend real. p95 0.92 ms, hit ratio
      100 %, 4 consultas de configuracion a la base frente a 374.240 sin cache (−99.999 %).
      Reportes en `documents/load-test/`.
- [x] **Coste del TTL de 2 min medido**: en una corrida de 4 min 30 s (con dos vencimientos del
      TTL) fueron 33 consultas para 1.103.520 lecturas — 29 consultas extra frente al llenado
      inicial. Es el precio de acotar el desfase a 2 min en vez de 10.
- [x] **Detectores de split**: los contadores de cache van etiquetados por replica
      (`RAILWAY_REPLICA_ID`). Mutacion verificada: sin la etiqueta el test cae.
- [x] Alertas de memoria de la cache: con Caffeine el limite es el heap del contenedor de
      Railway, no la memoria de Redis. Las metricas estandar de Caffeine quedan habilitadas
      (`metrics-enabled=true`): `cache_size` y `cache_evictions_total` por cache dan el tamano
      para alertar.
- [x] Corregir referencias `T-FE-15` → `T-CACHE-STRAT-01` en el codigo de este SDD (endpoint,
      tests de cache, `observer.api.ts`, `dashboard.api.ts`, `application.properties`). Las
      referencias de `dashboard.model.ts` y de las paginas de resultados/progreso se dejan: esas
      vienen del SDD archivado `T-FE-15` de analitica, que si es al que apuntan.
- [ ] Sacar `odd/tasks/invitacion-duplicada-race-condition.md` del commit de cache (`1bf7dcb`): no
      es de este SDD. No se puede deshacer sin reescribir historia (excluido por decision); el
      archivo esta en su carpeta correcta (`odd/tasks/`) y queda en el commit equivocado.
- [ ] **E2E de cache en CI**: los specs `e2e/cache-api-real.spec.ts` y
      `e2e/cache-datos-estaticos.spec.ts` corren hoy en local contra la API real, pero no hay job
      de Playwright en `.github/workflows/` que los ejecute en cada push.
- [ ] **T-IN-13 (Neon)**: la migracion de la base a Neon esta documentada pero no ejecutada
      (`openspec/changes/infra/2026-11-08-t-in-13-neon-database/apply-progress.md`).
- [ ] **WebKit** en la suite E2E: no es ejecutable en esta maquina; solo se corrio Chromium
      (Desktop Chrome y Mobile Chrome — Pixel 7).

---

## Resultado de las suites

```
frontend  npx vitest run --exclude '**/*.a11y.spec.ts'  → 106 files / 1092 tests, exit 0
backend   ./mvnw -o test                                → 250 tests, BUILD SUCCESS
k6        k6 run scenarios/cache-hit-ratio.js           → 100 VUs, p95 0.92 ms, hit ratio 100 %
backend   ./mvnw -o spotless:check                      → sin violaciones
frontend  pnpm format:check && pnpm lint                → sin observaciones
e2e       npx playwright test e2e/cache-api-real.spec.ts e2e/cache-datos-estaticos.spec.ts
          → 10 tests (Desktop Chrome + Mobile Chrome), exit 0
```

---

## Metricas de aceptacion (targets del SDD)

| Metrica | Target | Medido | Estado |
|---|---|---|---|
| Cache hit ratio | > 90% | **100.00 %** (k6, 100 VUs, TTL 2 min) | ✅ |
| P95 con 100 usuarios | < 2s | **0.92 ms** | ✅ |
| Consultas de configuracion a la base | −90 % | **−99.999 %** (374.240 → 4; 10,0 → 0,0000872 por lectura) | ✅ |
| Latencia formulario observer | < 1s | no medida (el E2E cuenta peticiones, no tiempos) | ⚠️ |
| Latencia tabs dashboard | < 0.5s | no medida (idem) | ⚠️ |
| Memoria de la cache | < 100MB | **medida**: 4 entradas (una por cache), ~2,3 KB de payload JSON; el limite real es el heap de la JVM (`cache_size` y `cache_evictions_total` en `/q/metrics`) | ✅ |

> Las dos latencias de interfaz (observer y dashboard) siguen **sin medir**: el E2E cuenta
> peticiones, no tiempos. El p95 de 0.92 ms es el de la API, no el de la pantalla. Medirlas
> implica times-to-interactive en el E2E, que es trabajo de otro SDD.

---

## Firma

**Builder:** _______________
**QA:** _______________
**Ready for Merge:** [ ] Yes [ ] No
