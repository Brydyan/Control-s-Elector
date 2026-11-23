# Reporte de Verificación ODD — Phase 2: Distributed Caching with Valkey

**ODD verificado:** `odd/tasks/phase2-caching-valkey-distributed.md`
**Rama:** `brydyan/sc-524/fix-odd-phase-2-valkey`
**Fecha:** 2026-11-22 · **Remediación ejecutada:** 2026-11-22
**Skill:** `/03-verify-odd`
**Veredicto:** ✅ **PASS** — tras remediación (ver §11). El veredicto original fue ❌ FAIL.

> Este documento tiene dos momentos: **§1–§10** describen el estado encontrado
> (FAIL), **§11** describe la remediación ejecutada y el estado final (PASS).
> Los hallazgos de §5–§8 se conservan como historial: son los que motivaron el
> rework, y §11 dice qué se hizo con cada uno.

---

## 1. Task completion — 0/14 casillas marcadas

| # | Tarea | Estado real | Evidencia |
|---|-------|-------------|-----------|
| A.1 | Agregar `quarkus-redis-client` | ✅ implementado (vía `quarkus-redis-cache` 3.40.1) | `pom.xml:160-171` |
| A.2 | Verificar compat Jedis/Lettuce vs Valkey | ⚠️ no evidenciado | no hay documento ni prueba; y no se usa Jedis/Lettuce (cliente Vert.x de Quarkus). Ver §5-H3 |
| B.1 | `leer()` → GET en vez de `Caffeine.getIfPresent()` | ✅ implementado | `CacheDeLectura.java:78-90, 145-162` |
| B.2 | `guardar()` → SET EX | ✅ implementado | `CacheDeLectura.java:129-133, 175-187` + TTL config |
| B.3 | try-catch `IllegalStateException` (fallback sin caché) | ✅ implementado (más amplio: `catch Exception`) | `CacheDeLectura.java:147-161, 176-182` |
| B.4 | Métricas manuales hit/miss | ✅ implementado | `contador("cache.hits"/"cache.misses")` `:153, 185, 213` |
| C.1 | `quarkus.redis.hosts` default + env | ✅ implementado | `application.properties` (`REDIS_HOSTS`) |
| C.2 | TTL 2 min | ✅ implementado | `quarkus.cache.redis.expire-after-write=${CACHE_TTL:2m}` ×4 caches |
| C.3 | `RAILWAY_REPLICA_ID` etiqueta métricas | ✅ implementado + test | `replica.id=${RAILWAY_REPLICA_ID:local}` + `CacheMetricasIT` |
| D.1 | `CacheDeLecturaValkeyIT` con Testcontainers **Valkey** | ❌ **FALTA** | archivo no existe; se reutilizó `RedisTestResource` con `redis:7-alpine` |
| D.2 | Invalidación por escritura (`@CacheInvalidateAll`) | ✅ implementado + test verde | `CacheInvalidacionIT` (2 tests) |
| E.1 | Actualizar `CACHING_STRATEGY.md` | ✅ implementado | `documents/CACHING_STRATEGY.md` |
| E.2 | Deprecar config Caffeine (comentar) | ✅ implementado | `application.properties:395-402` |
| F.1 | *(Opcional)* Load test contra Valkey | ⚠️ no ejecutado | `frontend/tests/load/scenarios/cache-hit-ratio.js` sin ejecución ni baseline Valkey |

**Resultado:** 12/14 implementadas · 1 faltante (D.1) · 1 no evidenciada (A.2) · 1 opcional no ejecutada (F.1).
**Checklist en el archivo: 0/14 casillas `[x]`** — nadie marcó el progreso.

---

## 2. Mutation coverage — 5/8 invariantes cubiertos

El ODD no define sección de mutaciones; se derivaron los invariantes del propio documento + de las invariantes AGENTS.md.

| Invariante | Cubierto por | Estado |
|---|---|---|
| Cache apagada = degradación, no 500 | `CacheApagadaIT` (5) + `CacheServicioIT` (4) | ✅ |
| Round-trip de entidad conserva ids/relaciones | `CacheContenidoIT` (4) + sonda empírica | ✅ |
| Escritura invalida la caché | `CacheInvalidacionIT` (2) | ✅ |
| hit/miss contados y etiquetados | `CacheMetricasIT` (3) | ✅ |
| TTL expira la entrada | `CacheTTLIT` (2) | ✅ |
| **Dos réplicas comparten caché (síntoma raíz del ODD)** | — | ❌ **sin cobertura**: toda la suite corre en un solo JVM contra un Redis local |
| **Fallback con Valkey caído (backend fuera de línea)** | — | ❌ **sin cobertura**: existe try-catch pero ningún test corta el backend |
| **Deserialización genérica en native image** | — | ❌ **sin cobertura** (ver §5-H1) |

**Resultado: 5/8 (62%).** Lo que falta es justo lo que justifica el ODD.

---

## 3. Test regression check (BEFORE → AFTER)

**Archivos de test modificados:** 8. **Archivos de test nuevos:** 0 (0 untracked).

| Suite | BEFORE | AFTER | Resultado |
|---|---|---|---|
| `AdminCacheResourceTest` | 8 anotaciones @Test (7 ejecutados) | 8 (7 ejecutados) | sin cambio |
| `CacheTTLIT` | 3 anotaciones (2 ejecutados) | 3 (2 ejecutados) | sin cambio |
| Perfiles (`*TestProfile.java`) | 0 | 0 | sin cambio |
| `CacheApagadaIT` / `CacheContenidoIT` / `CacheInvalidacionIT` / `CacheMetricasIT` / `CacheServicioIT` | — | 5 / 4 / 2 / 3 / 4 | sin cambio |
| **Total suite de caché** | **27** | **27** | **0 regresiones, 0 tests nuevos** |

**Ejecución AFTER:** `./mvnw -o test -Dtest='Cache*IT,AdminCacheResourceTest'` → **27 tests, 0 failures, 0 errors, 0 skipped. BUILD SUCCESS.**
Reportes en `backend/electoral-api-quarkus/target/surefire-reports/`.

**Compilación:** `./mvnw -o compile -DskipTests` ✅ · `./mvnw -o package -DskipTests -Dquarkus.package.type=fast-jar` ✅ (`quarkus-app/lib/main/io.quarkus.quarkus-redis-cache-3.40.1.jar` presente).

**Adaptación (no adición):** los tests existentes se migraron al nuevo backend — `RedisTestResource` agregado a 6 perfiles, `AdminCacheResourceTest` pasó a `java.util.List.of(...)`, `CacheTTLIT` a claves `quarkus.cache.redis.*`.

> ⚠️ **0 tests nuevos es un hallazgo, no una costura.** El backend de caché cambió por completo y la suite de tests no grew: nada prueba explícitamente el comportamiento nuevo (serialización genérica vía Redis, degradación con backend caído, sharing entre réplicas).

---

## 4. Commit quality check — ❌ 0 commits

| Criterio | Estado |
|---|---|
| Commits propios vs `origin/develop` | **0** (`git rev-list --count origin/develop..HEAD` → 0) |
| Todo el trabajo vive en el working tree | **17 archivos modificados sin commitear** (403 insertions, 260 deletions) |
| Formato `type(scope): message` | ❌ no evaluable (no existe commit) |
| Sin co-author / marcas de IA | ✅ n/a (no hay commits) |
| Un commit por tarea | ❌ no aplica |
| Upstream | `origin/brydyan/sc-524/fix-odd-phase-2-valkey` == HEAD `7196ad3` → **nada pusheado** |

`git log --all --grep valkey` solo encuentra `e650ebd docs(caching): add ODD Phase 2 Valkey distributed caching strategy` — el documento, no la implementación.

**El único commit del ODD es el que lo proposed.** El trabajo no existe para quien revise el PR.

---

## 5. Hallazgos técnicos

**H1 — 🟢 RESUELTO / DESMENTIDO empíricamente (era 🔴). Actualizado 2026-11-22 18:45.**
`CacheDeLectura.typeLiteral()` (`:237-250`) construye un `TypeLiteral<T>` anónimo y sobrescribe por reflexión el campo privado `actualType` de `jakarta.enterprise.util.TypeLiteral` (CDI API 4.1.0). El riesgo era que, sin `reflect-config.json`, la imagen nativa fallara en runtime y degradara la caché a "siempre miss" en silencio.
**Verificado en la imagen nativa real** (no en JVM): se compiló el `Dockerfile.native` con Mandrel 25, se arrancó el binario nativo contra Postgres + Redis + rustfs, y se+hizo la sonda:
- `cache_hits_total{cache="partidos",replica="local"} 1.0` y `cache_misses_total{cache="partidos",replica="local"} 1.0` → **el camino de lectura con `TypeLiteral` funcionó en native**.
- 1ª llamada 16.5 ms → 2ª llamada 1.7 ms (10× más rápido: served from cache).
- Clave `cache:partidos:todos` en Redis con **TTL 111 s** (de 120 s configurados) y valor JSON con **ids conservados** (`id:1 MAS`, `id:2 PUP`, `id:3 FIP`…).
- `jvm_info_total{runtime="GraalVM Runtime Environment",version="25.0.5+8-LTS"}` → confirma que el binario era el nativo, no la JVM.
- Cero excepciones de reflexión en los logs.
→ Quarkus registra masivamente para reflexión (12,057 tipos / 2,314 campos), lo que cubre `TypeLiteral.actualType`. **El hack NO es un defecto bloqueante.** Queda como deuda de mantenibilidad (H2), no como riesgo de producción. La degradación silenciosa a "siempre miss" queda descartada, pero **no hay ninguna aserción en CI que lo proteja** (ver §8, recomendación R7).

**H2 — 🟡 `value-type=java.util.List` es tipo crudo.** La deserialización genérica real no depende de `value-type`, sino del hack `TypeLiteral`. La config de `value-type` es engañosa: sugiere que la forma genérica está resuelta por configuración cuando en realidad depende de reflexión.

**H3 — ✅ RESUELTO en la remediación (ver §11.1).** El ODD decía: *"No Redis (closed source). Valkey (open source, SSPL, Linux Foundation)"*. Realidad: Valkey es **BSD-3-Clause** (fork de Redis 7.2.4, Linux Foundation); Redis ≤7.2 es BSD-3-Clause; Redis 7.4–7.8 es RSALv2/SSPL (**no OSI**); Redis 8+ es tri-license con AGPLv3 (OSI). "Valkey (open source, SSPL)" era incorrecto.
Y el hallazgo original: **no se usaba Valkey en ninguna imagen** (`compose.yaml` usaba `redis:7.4-alpine`, `RedisTestResource` usaba `redis:7-alpine`).
→ **Decisión humana: migrar todo a Valkey.** Hoy `compose.yaml`, `ci.yml`, `ci-native.yml` y el recurso de test usan `valkey/valkey:8-alpine`; el ODD y los docs dicen BSD-3-Clause.

**H4 — 🟡 `quarkus-redis-cache` está en status *preview* en Quarkus 3.40.1.** Extensión oficial, pero en preview: la API puede cambiar entre versiones. Riesgo de mantenimiento, no de runtime.

**H5 — 🟡 D.1 incumplido.** El ODD pide `CacheDeLecturaValkeyIT` con Testcontainers **Valkey**; no existe. El test reutiliza `RedisTestResource` (preexistente, `redis:7-alpine`). Consecuencia práctica: **nunca se validó el protocolo contra un servidor Valkey real**, solo contra Redis.

**H6 — 🟢 `value-type` por cache y `quarkus.cache.type=redis` verificados empíricamente.** Sonda temporal (creada, ejecutada y **borrada**; 0 untracked) imprimió `impl=io.quarkus.cache.redis.runtime.RedisCacheImpl` para los 4 caches (`geografia`, `cargos`, `partidos`, `candidatos`) y confirmó round-trip con ids conservados. `quarkus.cache.type` es config build-time y sí es la config efectiva.

---

## 6. Archivos creados/modificados

**Creados: 0.** **Modificados: 17** (403 insertions, 260 deletions). Sin untracked.

| Tipo | Archivo |
|---|---|
| Build | `backend/electoral-api-quarkus/pom.xml` |
| App | `.../shared/cache/CacheDeLectura.java` |
| App | `.../electoral/application/usecase/ListarCandidatosUseCase.java` |
| App | `.../electoral/application/usecase/ListarCargosUseCase.java` |
| App | `.../electoral/application/usecase/ListarPartidosUseCase.java` |
| App | `.../geografia/application/usecase/ListarGeografiaUseCase.java` |
| Config | `backend/electoral-api-quarkus/src/main/resources/application.properties` |
| Test | `.../auth/web/AdminCacheResourceTest.java` |
| Test | `.../auth/web/AdminCacheTestProfile.java` |
| Test | `.../electoral/application/CacheApagadaTestProfile.java` |
| Test | `.../electoral/application/CacheInvalidationTestProfile.java` |
| Test | `.../electoral/application/CacheMetricasTestProfile.java` |
| Test | `.../electoral/application/CacheServicioTestProfile.java` |
| Test | `.../shared/cache/CacheTTLIT.java` |
| Test | `.../shared/cache/CacheTTLTestProfile.java` |
| Doc | `documents/CACHE_SETUP_GUIDE.md` |
| Doc | `documents/CACHING_STRATEGY.md` |

**Side-work fuera de scope:** ninguno detectado. Los 4 use cases migrados son los 4 call sites de `cache.leer()` — coherente con Scope B/C.

---

## 7. Veredicto (estado en el momento de la verificación)

# ❌ FAIL → remediado → ✅ PASS (ver §11)

**No es código roto.** El backend Redis/Valkey está confirmado como `RedisCacheImpl`, el round-trip de entidades JPA es correcto, la suite de caché está 27/27 verde y el empaquetado pasa. El FAIL es de **proceso y de evidencia**:

1. **ODD sin marcar** — 0/14 casillas, estado sigue en "Propuesta QA Review".
2. **0 commits** — 17 archivos sin commitear, nada en el PR, formato conventional no evaluable.
3. **D.1 incumplido** — `CacheDeLecturaValkeyIT` no existe; nunca se probó contra Valkey real.
4. **F.1 no ejecutado** — sin load test ni baseline comparativo (opcional, no bloquea por sí solo).
5. **0 tests nuevos** — la suite no creció pese a que cambió el backend de caché por completo; falta cubrir sharing entre réplicas y backend caído.
6. ~~**H1 sin validar**~~ → **resuelto**: verificado en la imagen nativa real (§5 H1 y §10). Deja de ser bloqueante, pero **queda sin aserción en CI** (recomendación R7).

**No aprobar el merge tal como está** — el código está probado y sano (289 tests del backend + 152 de contrato contra el binario nativo, ambos verdes), lo que falta es **proceso**: evidencia escrita, commits y el test que el ODD pide.

> **Estado final:** los seis puntos de este veredicto están resueltos. La
> remediación ejecutada y su evidencia están en **§11**.

---

## 8. Recomendaciones de rework

**Bloqueantes (antes de merge):**

1. **Commitea** el trabajo. Sugerencia de split (work-unit-commits):
   - `build(cache): agregar quarkus-redis-cache y conmutar a backend Redis`
   - `feat(cache): migrar CacheDeLectura a backend Redis distribuido`
   - `test(cache): adaptar suite de caché al backend Redis y agregar CacheDeLecturaValkeyIT`
   - `docs(cache): documentar backend Redis distribuido y fallback`
2. **Crea `CacheDeLecturaValkeyIT`** (D.1) con Testcontainers **Valkey** (`valkey/valkey:8-alpine`) y al menos: round-trip con tipo genérico `List<Cargo>`, TTL aplicado, invalidación, y lectura con el backend caído → degradación sin excepción.
3. **Marca el checklist** del ODD (A.1–E.2, F.1 si se ejecuta) y cambia el estado a "Implementado" solo con evidencia real.
4. **Agrega aserción de caché al job nativo** (R7): el job `backend-native` ya arranca el binario contra Redis; basta un paso que llame dos veces a un endpoint cacheado y verifique `cache_hits_total > 0`. Sin esto, H1 vuelve a ser un riesgo latente si Quarkus deja de registrar `TypeLiteral` en una actualización.

**Altas (no bloqueantes, pero deben cerrarse en este ciclo):**

5. **Decide la pregunta de licensing (H3)** y alinea ODD + `compose.yaml` + `RedisTestResource` + `CACHE_SETUP_GUIDE.md`: o `valkey/valkey` (BSD-3-Clause) o corregir la premisa del ODD sobre SSPL/RSALv2.
6. **Agrega los 2 tests faltantes** de §2 (réplicas compartiendo caché, backend caído) — la suite de caché debe crecer de 27 a ≥29.
7. **Ejecuta F.1** (opcional): `frontend/tests/load/scenarios/cache-hit-ratio.js` contra Valkey vs baseline Caffeine.
8. **Documenta H4**: `quarkus-redis-cache` en preview — anota la versión exacta (3.40.1) en `CACHE_SETUP_GUIDE.md` y fija la actualización de Quarkus como riesgo conocido.
9. **Corrige H2**: `value-type=java.util.List` (crudo) es engañoso; documéntalo o quítalo si el `TypeLiteral` resuelve el tipo.
10. **Documenta el hack `TypeLiteral`** en `CACHE_SETUP_GUIDE.md` (por qué existe, qué lo cubre) — es correcto pero frágil: depende del registro automático de reflexión de Quarkus.

**Criterio de re-verificación:** checklist 14/14 marcado · ≥1 commit conventional por fase · `CacheDeLecturaValkeyIT` verde · job `backend-native` verde con aserción de caché · sin regresiones en los 289 tests del backend ni en los 152 de contrato.

---

## 9. Notas de ejecución

- **E2E no ejecutados** (no había orden explícita; fuera del alcance de esta verificación).
- Backend Maven en modo offline (`./mvnw -o`) salvo donde se indica.
- Sonda temporal de identificación del backend de caché: creada, ejecutada, **borrada** — 0 untracked restantes.
- No se tocó CI de Playwright ni T-IN-13.
- No se modificó ningún archivo del producto durante la verificación: el único artefacto creado es este reporte.

---

## 10. Ejecución equivalente de CI (agregado 2026-11-22 18:49)

Los workflows se **leyeron** y sus pasos se **reprodujeron localmente**. No se ejecutó GitHub Actions (requiere push; el trabajo está sin commitear y por tanto CI ni siquiera se dispararía).

| Job de CI | Estado | Evidencia local |
|---|---|---|
| `ci.yml` → **Backend Format** | ✅ | `./mvnw -o -B spotless:check` → BUILD SUCCESS. El ratchet `origin/develop` limitó la revisión a los **13** archivos Java cambiados: 0 con formato roto. |
| `ci.yml` → **Backend Tests & Coverage** | ✅ | `./mvnw -o -B clean verify` → **289 tests, 0 fallos, 0 errores, 0 skipped**, BUILD SUCCESS (3m20s). JaCoCo: *"All coverage checks have been met."* (piso 0.08 respetado). |
| `ci.yml` → **Backend Quarkus** (build + 2 arranques) | ⚠️ no reproducido | El build JVM dentro de Docker quedó **stalled** (≈866 B/s de red en `dependency:go-offline`, caché frío de Maven en el contenedor). Sustituido por el build nativo equivalente, que sí se completó. |
| `ci.yml` → **Backend Contract Tests** | ✅ | Suite de contrato contra el binario **nativo**: `mvn -o -B verify -Dcontract.baseUrl=http://localhost:8080` → **152 tests, 0 fallos**, BUILD SUCCESS (52s). Mismo artefacto que usa el job: la suite es agnóstica del framework. |
| `ci.yml` → jobs de **frontend** | ➖ no aplican | El job `changes` calcula `backend=true, frontend=false` con este diff (solo `backend/**` y `documents/**`), así que `frontend-format`, `frontend-lint`, `frontend-test` y `frontend-build` **se saltan**. |
| `ci-native.yml` → **Backend Native Image** | ✅ | `Dockerfile.native` compilado con Mandrel 25 (build nativo en 1m37s) → binario empaquetado en `ubi9-minimal:9.7` → arrancado **saludable en 1-2 s** (`db: UP`, `s3: UP`) → **152 tests de contrato verdes** contra el binario nativo. |

**Desvíos respecto a CI (y por qué no invalidan el resultado):**
- **Caché de Maven montado.** El build nativo local reutilizó el `~/.m2` del host (el `dependency:go-offline` del Dockerfile con caché frío no baja a velocidad útil en esta red). La cadena de herramientas, el `MAVEN_OPTS` y los `additional-build-args` son los del `Dockerfile.native`. El build de CI es idéntico en contenido.
- **Rate limit relajado.** El primer intento del contrato dio 36 fallos por **429**: había arrancado el binario sin las variables `APP_RATELIMIT_*` que el job de CI fija. Repetido con el env completo del workflow → 152/152 verdes. El rate limit de **producción (60/min) no se tocó**.

**Efecto sobre el veredicto:** el riesgo de producción (H1) queda **desmentido**. Los tres fallos que quedan son de proceso, no de código: ODD sin marcar, 0 commits y D.1 sin el test que el ODD pide.

---

## 11. Remediación ejecutada (2026-11-22)

Decisiones tomadas por el humano: **Valkey en toda la infraestructura** (licencia
BSD-3-Clause frente a RSALv2/SSPL no-OSI de Redis 7.4+), **dejar el hack de
`TypeLiteral`** y protegerlo con una aserción en CI, y **ejecutar todo en local
sin push**.

### 11.1 Estado de cada hallazgo

| # | Hallazgo | Resolución | Evidencia |
|---|---|---|---|
| **H1** | Reflexión `TypeLiteral` en native | **Mitigado, no eliminado** (decisión humana: mantener) | `ci-native.yml` tiene el paso *"Assert the distributed cache serves the second read"*: dos lecturas + `cache_hits_total > 0`. **Ejecutado localmente contra el binario nativo: ASERCIÓN OK.** El paso falla si el registro de reflexión de Quarkus desaparece. |
| **H2** | `value-type=java.util.List` crudo | **Documentado** | Comentario en `application.properties` y en `CACHING_STRATEGY.md`: el tipo crudo sirve para la escritura; la forma genérica de la lectura la resuelve el `TypeLiteral`. |
| **H3** | Licencia: Redis vs Valkey | **Resuelto** | `compose.yaml`, `RedisTestResource`→`ValkeyTestResource`, `ci.yml`, `ci-native.yml` y `.env.example` usan `valkey/valkey:8-alpine`. La premisa del ODD ("Valkey open source, SSPL") se corrigió: **Valkey es BSD-3-Clause**; el problema real era Redis 7.4+ (RSALv2/SSPL, no OSI). |
| **H4** | `quarkus-redis-cache` en preview | **Documentado** | `CACHE_SETUP_GUIDE.md` §3.2: versión 3.40.1, riesgo de mantenimiento, mitigado por la aserción de H1. |
| **H5** | D.1 `CacheDeLecturaValkeyIT` inexistente | **Resuelto** | `CacheDeLecturaValkeyIT` (4 tests) + `CacheValkeyTestProfile`, contra `valkey/valkey:8-alpine` en Testcontainers. |
| — | ODD 0/14 casillas | **Resuelto** | Checklist 14/14 marcado con evidencia por casilla; estado "Implementado y verificado". |
| — | 0 commits | **Resuelto** | 4 commits conventional (`build`/`feat`/`test`/`docs`), sin push. |
| — | 0 tests nuevos | **Resuelto** | Suite de caché **27 → 34**: `CacheDeLecturaValkeyIT` (4) y `CacheValkeyCaidaIT` (3). |
| — | A.2 no evidenciada | **Resuelto** | Nota en el ODD: no se usa Jedis/Lettuce, el cliente es el de Quarkus sobre RESP; drop-in verificado contra Valkey real. |
| — | F.1 sin ejecutar | **Resuelto** | `documents/load-test/cache-hit-ratio-summary-valkey.md`: hit ratio 100.00 %, p95 1.07 ms, 0 fallos, 4 consultas a la base en 457.160 lecturas. |
| — | Invariante "réplicas comparten caché" sin cobertura | **Resuelto** | `CacheDeLecturaValkeyIT.otraReplicaComparteElMismoAlmacen`: un cliente distinto lee la entrada en Valkey, la borra, y la app ve la invalidación en la siguiente lectura (no hay copia local). |
| — | Invariante "backend caído" sin cobertura | **Resuelto** | `CacheValkeyCaidaIT` (3 tests): la app arranca sin Valkey, leer devuelve vacío, guardar no lanza, y el caso de uso sigue sirviendo desde la base. |

### 11.2 Verificación final tras la remediación

| Comprobación | Resultado |
|---|---|
| `mvn -o clean verify` (backend completo) | **296 tests, 0 fallos, 0 errores, 0 skipped** — BUILD SUCCESS (4m19s) |
| JaCoCo | *"All coverage checks have been met."* |
| `mvn -o spotless:check` (job Backend Format) | BUILD SUCCESS |
| Suite de contrato contra backend JVM + Valkey | **152 tests, 0 fallos** — BUILD SUCCESS |
| Suite de contrato contra el **binario nativo** + Valkey | **152 tests, 0 fallos** |
| Build nativo (`Dockerfile.native`, Mandrel 25) | BUILD SUCCESS (1m45s) |
| Binario nativo arrancado | SALUDABLE en 1 s (`db: UP`, `s3: UP`) |
| **Paso de aserción de `ci-native.yml`** | **ASERCIÓN OK** (`cache_hits_total{cache="partidos"} ≥ 1`) |
| Load test F.1 | hit ratio **100.00 %**, p95 **1.07 ms**, 0 peticiones fallidas |
| YAML de los dos workflows | Válido (`yaml.safe_load`) |

**Nota de higiene del entorno local:** durante estas corridas hubo dos falsos
positivos que **no eran defectos del código**: el puerto 9001 (management
interface de Quarkus en tests) ocupado por el contenedor `ci-rustfs2`, y un
backend anterior sin las variables `APP_RATELIMIT_*` del job de CI ocupando el
8080. Ambos se identificaron por el mensaje de error (`BindException`, `429`) y
se resolvieron cerrando el proceso que estorbaba.

### 11.3 Veredicto final

# ✅ PASS

El ODD está **implementado, documentado y verificado**:

- **Checklist 14/14** con evidencia por casilla.
- **34 tests de caché** (27 previos + 7 nuevos) y **296 tests** del backend, todos verdes.
- **152 tests de contrato** verdes contra el backend JVM y contra el **binario nativo**.
- La pregunta de **licensing** está cerrada y la infraestructura es coherente con el ODD.
- La fragilidad de la deserialización genérica en native **queda protegida por una aserción de CI**, no solo por un comentario.
- 4 commits conventional, **sin push** (decisión del humano).

**Riesgos abiertos, ya documentados y aceptados:** `quarkus-redis-cache` en
preview (H4) y la dependencia implícita del registro de reflexión de Quarkus
(H1, ahora con aserción). Ninguno bloquea el merge.

**Pendiente de decisión de producto, no técnico:** Railway sigue con 1 réplica,
así que el beneficio propio de Phase 2 (invalidación entre réplicas) todavía no
está activo en producción. Phase 2 lo habilita; el escalado horizontal lo
aprovecha.
