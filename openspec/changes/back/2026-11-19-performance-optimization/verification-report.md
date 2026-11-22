# Verificación SDD — Performance Optimization (T-PERF-OPT-01)

**SDD:** `openspec/changes/back/2026-11-19-performance-optimization/`
**Rama:** `brydyan/sc-519/feat-performance-optimization` → PR #207 (base `develop`)
**Fecha:** 2026-11-22
**Veredicto: ✅ CONFORM** — implementa lo que el SDD pedía, con la evidencia medida. Los dos huecos
que la verificación anterior dejó abiertos (E2E del flujo Observer y criterio del pool) están
cerrados; lo que queda es medición contra Neon, que es post-deploy por definición y no se simula.
Queda un incumplimiento menor de la convención de commits que arrastra el repo, no este SDD.

---

## 1. Cumplimiento del spec

### 1.1 Propuesta — alcance

| Área del SDD | Estado | Evidencia |
|---|---|---|
| A. Índices en `acta` | ✅ | `048-add-acta-indices.sql`, 5 índices + 1 compuesto, registrados en `db.changelog-master.yaml` |
| A. Índices en `acta_detalle` | ⚠️ parcial | `049-add-acta-detalle-indices.sql` con **1** índice, no 2: el de `acta_id` es redundante con el `UNIQUE(acta_id, candidato_id)` que ya existía |
| A. Índice en `usuario.rol_id` | ✅ ya existía | `ix_usuario_rol_id` verificado en `pg_indexes` |
| B.1 EAGER → LAZY | ✅ | `Usuario.rol` LAZY + `@BatchSize(25)` sobre `Rol` |
| B.2 Batch endpoint | ✅ | `GET /api/v1/observer/cargos-with-candidatos?jrvId=`, DTOs, IT, y **consumidor frontend** (`registrar-acta`, `modificar-acta`) |
| B. Native queries optimizadas | ✅ | El repo ya usaba queries indexadas; el EXPLAIN confirma el plan |
| C. Caché frontend | ✅ | `shareReplay(1)` en `observer.api.ts` y `dashboard.api.ts` (4 cachés en dashboard) |
| D.1 gzip | ✅ | `enable-compression=true`, `compression-level=6` |
| D.2 Pool 20 → 50 | ✅ | `quarkus.datasource.jdbc.{min-size=10,max-size=50,acquisition-timeout=30s,idle-removal-interval=5m,max-lifetime=30m}` |
| E. Tests backend | ✅ | 298 tests backend, 1153 frontend + 5 a11y, 152 contract |
| E. EXPLAIN ANALYZE | ✅ | 4 queries verificadas con 50 000 filas sintéticas |
| E. Load test | ⚠️ parcial | 100 usuarios × 50 requests contra Postgres local. **No** contra Neon, que es la topología de producción |
| E. E2E flujo Observer | ✅ ejecutado | Playwright contra la API real, 3 proyectos de navegador — ver §4.1 |
| F. Documentación | ✅ | `PERFORMANCE_OPTIMIZATION.md`, `INDEXING_GUIDE.md`, `CACHING_STRATEGY.md` |

**Desviación de D4 sin consequences**: el SDD decía "mantener los 3 endpoints viejos para
compatibilidad". Se maintained — `/observer/jrv/{id}/cargos` sigue existiendo y el IT del batch
compara contra él precisamente para demostrar equivalencia.

### 1.2 Decisiones de diseño (D1–D5)

| Decisión | Elegida | Implementada | Veredicto |
|---|---|---|---|
| D1 Índices antes que query optimization | B | B | ✅ |
| D2 Lazy selectivo, no global | B | B | ✅ — solo `Usuario.rol` cambió; el resto de relaciones intactas |
| D3 Caché service-level, no interceptor | B | B | ✅ |
| D4 Endpoint batch nuevo, mantener los viejos | B | B | ✅ |
| D5 Pool 50, no 100 | A | A | ✅ |

Las cinco decisiones se aplicaron tal como se decidieron.

### 1.3 GIVEN/WHEN/THEN

El SDD no usa formato GIVEN/WHEN/THEN; usa checkboxes de tareas. Las casillas se auditaron una
por una: **51 marcadas** (cada una con su evidencia en `apply-progress.md` y en `tasks.md`), **7 sin
marcar** y todas justificadas:

- 7 metas de monitoreo post-despliegue (no verificables antes de desplegar)

El E2E del flujo Observer, que estaba sin marcar, se ejecutó en esta sesión (§4.1): 53 tests en
verde contra la API real, en los 3 proyectos de navegador.

---

## 2. Cobertura de mutaciones

El SDD no define mutaciones explícitas, así que traduje sus invariantes a estados en los que el código
podría romperse y verifiqué que **un test falla** en cada uno.

| Invariante | Mutación aplicada | ¿Falla un test? | Test |
|---|---|---|---|
| `Usuario.rol` es LAZY | `FetchType.LAZY` → `EAGER` | ✅ **Sí** | `UsuarioLazyBatchIT.laAsociacionARolEsLazy` |
| El rol se carga en lote, no N+1 | `@BatchSize(25)` → `@BatchSize(1)` | ✅ **Sí** (5 sentencias vs 3) | `UsuarioLazyBatchIT.resolverLosRolesNoEscalaConElNumeroDeUsuarios` |
| Los índices de acta existen | `DROP INDEX idx_acta_detalle_candidato_id` | ✅ **Sí** | `IndicesActaIT.elIndiceDeActaDetalleExiste` |
| El endpoint batch devuelve lo mismo que el endpoint suelto | respuesta con cargos distintos | ✅ **Sí** | `ObserverCargosConCandidatosIT.devuelveElMismoCatalogoQueElEndpointSuelto` |
| El IT del batch no depende del seed | `jrv_cargo` vacía (base limpia) | ✅ **Sí** (fallaba 4/4 antes del arreglo) | `ObserverCargosConCandidatosIT` |
| La caché frontend no duplica peticiones | quitar `shareReplay(1)` | ✅ **Sí** | `observer.api.spec.ts`, `dashboard.api.spec.ts` |
| El pool sube a 50 | quitar `quarkus.datasource.jdbc.max-size` | ⚠️ **No hay test** | — el valor solo se puede observar en runtime, ver §5 |

**Cobertura: 6 de 7 invariantes con test que falla ante la mutación.** El que falta (tamaño del
pool) es una propiedad de configuración, no de comportamiento: se verificó en runtime
(§4, D.2).

### 2.1 Dos hallazgos reales que la verificación destapó

**H-1 — El IT del catálogo dependía de datos que otros IT borran.** `ObserverCargosConCandidatosIT`
buscaba "la primera JRV del seed que tenga al menos un cargo habilitado". `UserInvitationIT`,
`PasswordChangeServiceTest` y otros ejecutan `delete from jrv` / `delete from jrv_cargo` en su
limpieza. Consecuencia: en una base limpia el IT fallaba 4/4 con `NoResult`, y en una base
compartida podía pasar **usando la JRV que otro test acababa de insertar** — un falso verde que
dependía del orden de ejecución. Corregido: el IT arma su propio dataset (JRV, cargo, partido,
candidato) y lo borra al terminar.

**H-2 — El test de conteo de queries que el SDD pedía no distinguía nada.** La primera versión
comparaba `getQueryExecutionCount()` entre páginas de 5 y 20 usuarios. Ese contador **solo ve
JPQL**: con EAGER y con LAZY da el mismo número, así que el test pasaba con y sin el cambio. Se
reescribió con `getPrepareStatementCount()` (SQL emitido) y con un dataset de usuarios de **roles
distintos**, que es lo que hace visible el N+1.

---

## 3. Calidad de los tests

| Criterio | Evaluación |
|---|---|
| Pirámide completa | ✅ 298 unit + IT backend, 1153 frontend, 152 contract, 5 a11y |
| IT contra BD real, no mocks | ✅ `PostgresTestResource` (Testcontainers o `-Dtest.postgres.url`), sin mocks de persistencia |
| Casos de error | ✅ 401 sin token, 400 sin `jrvId`, JRV inexistente, 403 por rol, 429 por rate limit |
| Flakiness | ⚠️ **riesgo real, mitigado**: la suite contra base *compartida* dio 2 fallos + 25 errores (FK `incidencia_jrv_jrv_id_fkey` al limpiar `jrv` — contaminación entre suites). Contra base **limpia**: 298/298 verde. El CI usa base limpia. |
| Timeouts hardcodeados | ✅ ninguno en los tests nuevos |
| Aislamiento | ✅ los tests nuevos crean y borran su propio dataset |
| E2E de flujo crítico | ✅ el flujo Observer corre contra la API real, 3 proyectos de navegador (§4.1) |
| Cobertura | ✅ umbral de JaCoCo superado ("All coverage checks have been met") |

**Debilidad real**: el IT del batch pasó en verde sobre una base con datos residuales de otro
suite. Un test verde sobre datos ajenos no es evidencia. Corregido en esta sesión, pero el patrón
—tests que dependen del seed— puede seguir presente en otros IT del repo. No es de este SDD.

---

## 4. Gate por gate (lo que el SDD pedía verificar)

### A.1/A.2 — Índices
✅ **CONFIRMADO**. 8 índices `idx_acta*` visibles en `pg_indexes` (5 de `048`, 1 de `049`, 2
preexistentes de `044`), tras reconstruir el jar con los commits de perf y aplicar las migraciones
(Liquibase `Run: 2`, 108 changesets).

### A.3 — EXPLAIN ANALYZE
✅ **CONFIRMADO** con 50 000 filas sintéticas (dentro de una transacción con `ROLLBACK`):

| Query | Plan | Índice |
|---|---|---|
| `WHERE registrada_por = ?` | Index Scan | `idx_acta_registrada_por` |
| `WHERE estado = 'ENVIADA'` (7 % de las filas) | Index Scan | `idx_acta_estado` |
| Filtro compuesto jrv+cargo+estado | Bitmap Index Scan | `idx_acta_jrv_cargo_estado` |
| `ORDER BY fecha_registro DESC` | Index Scan | `idx_acta_fecha_registro_desc` |
| `acta_detalle` JOIN `acta` | Seq Scan | — (287 filas de detalle: el planner acierta a esa escala) |

### B.1 — Lazy sin N+1
✅ **CONFIRMADO** con test de conteo de queries + dos mutaciones que lo hacen fallar. Además, la
suite completa (298 tests) pasa sin `LazyInitializationException`: todos los consumidores de
`getRolNombre()` leen dentro de transacción.

### B.2 — Batch endpoint
✅ **CONFIRMADO**: 4/4 verde contra Postgres local, con dataset propio.

### D.1 — gzip
✅ **CONFIRMADO** contra backend vivo: `content-encoding: gzip` en `/api/v1/menus/me`,
`/api/v1/admin/usuarios` y `/api/v1/dashboard/resultados`.

### D.2 — Pool
✅ **CONFIRMADO con medición** (el gate estaba bloqueado: sin métricas del pool no era observable).

- Habilitadas **dos** claves: `quarkus.datasource.metrics.enabled` (global) y
  `quarkus.datasource.jdbc.metrics.enabled` (este datasource). Con una sola no aparece nada. Ambas
  son de **tiempo de build**: cambiar una exige reconstruir la imagen.
- Load test: 100 usuarios × 50 requests = **5 000 requests**, 0 errores HTTP, 0 eventos "unable to
  acquire connection", p50 25.4 ms, **p95 36.4 ms**, max 48.2 ms, 2 383 req/s.
- Pool: `agroal_max_used_count` = 50 (techo alcanzado en el pico), `agroal_creation_count_total` =
  50; en régimen permanente `agroal_active_count` 1–9 y `agroal_available_count` 41–49 → **uso medio
  5-18 %, pico 100 %**.
- `agroal_blocking_time_max_milliseconds` = 49 ms: espera máxima por conexión, sin timeouts.

> ⚠️ **El "pool al 60-80 %" que pedía el SDD no se alcanza con esta carga — y en producción ese
> criterio no es observable.** La carga es de una sola tabla y el pool no se satura. Pero el punto
> de fondo es otro: Neon pone un **PgBouncer (`pool_mode=transaction`) delante de cada compute**, que
> multiplexa las conexiones del cliente sobre pocas conexiones reales. El "80 % de ocupación" es un
> número de PgBouncer, no del backend. **Por decisión del humano, el criterio de D.2 se redefinió**
> a lo que sí se puede medir contra la topología real: 0 conexiones esperando, 0 errores de
> adquisición, pool cliente chico respecto de los slots del compute, y 0 errores de conexión tras
> el cold start de Neon. Los dos primeros quedan CONFIRMADOS en local; los dos últimos son
> medición post-deploy. Ver §4, "D.2 — Pool".

### E.3 — Load test
⚠️ **PARCIAL**: se usó un script propio (100×50) en lugar del escenario exacto de JMeter (50×10) del
SDD — **más exigente**, no menos. Contra Neon: no ejecutado.

### 4.1 E.3 — E2E del flujo Observer contra la API real
✅ **CONFIRMADO**. Ejecutado local con el stack completo en pie.

**Montaje** (el que faltaba en el intento anterior):

| Pieza | Cómo se levantó | Por qué así |
|---|---|---|
| Postgres 16 | `docker compose --profile dev up -d postgres` | Puerto 5432 liberado (el `ci-pg` previo se paró y renombró) |
| Redis 7.4 | `docker compose --profile dev up -d redis` | El rate limiting usa contadores compartidos en Redis |
| RustFS | `docker compose --profile dev --profile s3 up -d rustfs` | Storage de las evidencias |
| Rol `electoral_app` | `docker compose --profile dev run --rm db-app-role` | **Sin superusuario y sin BYPASSRLS**: con el dueño, Postgres ignora el RLS y ninguna prueba puede afirmar que filtra |
| Migraciones | jar local, `APP_MIGRATE_ONLY=true`, contextos `seed,bootstrap-admin` | Liquibase corre como owner (`electoral_user`); el backend corre con `electoral_app` |
| Backend | `java -jar target/quarkus-app/quarkus-run.jar` en :8080 | El binario nativo de GraalVM no entra en la RAM de la máquina; la JVM sí |
| Frontend | `pnpm start` en :4200 | Proxy `/api` → :8080 |

**Resultado** (`E2E_REQUIRE_BACKEND=1 pnpm test:e2e --grep "@guardian"`):

| Proyecto | Resultado |
|---|---|
| Desktop Chrome | ✅ 19/19 |
| Mobile Chrome — Pixel 7 | ✅ 17/17 (2 skipped por viewport) |
| Mobile Safari — iPhone 12 | ✅ 17/17 (2 skipped por viewport) |
| **Total** | **✅ 53 passed, 0 failed, 4 skipped** |

**Qué cubre el flujo (J1, `@guardian @no-intercept`)** — todos contra la API real, sin mocks:

- El observer entra y ve sus JRVs asignadas
- Selecciona una JRV y navega a la lista de cargos
- Llega al wizard de registro en el paso IMAGEN
- Adjunta la foto (compresión a `.webp` en el cliente, subida a RustFS)
- Llena los votos y **el checksum cuadra en el servidor**
- **Registra el acta y ve el estado de éxito**
- El checksum inválido **bloquea** el avance a REVISIÓN
- Los nombres de los candidatos se muestran en el paso VOTOS
- **Idempotencia: 409 Conflict en el duplicado**

El caso de idempotencia es el que importa aquí: es la ruta cuyo contrato de red
cambió con el batch endpoint (`cargos-with-candidatos`), y el 409 confirma que el
bloqueo por clave de negocio (`jrv_id` + `cargo_id`) sigue intacto contra la API
real.

### 4.2 Hallazgo del E2E: el rate limit de `observer-jrvs` lo rebasa la suite

⚠️ **H-3 — el rate limit de producción (60/min por usuario) no aguanta la suite E2E completa.**

La primera corrida dio **18 fallos**, todos de la misma familia:

```
TypeError: jrvs is not iterable
TypeError: cargos is not iterable
```

No era un defecto de la API: era el **HTTP 429** del policy `observer-jrvs`
(`limit=60`, `window=1m`, `key=user`). La suite corre 3 proyectos de navegador
contra el **mismo** usuario del seed, y cada test llama varias veces a
`/observer/jrvs` y `/observer/jrv/{id}/cargos`: la cuota se agota en el segundo
proyecto. Confirmado de forma independiente con 65 llamadas seguidas a
`/api/v1/observer/jrvs`: los primeros 60 responden 200 y los siguientes 5
responden 429.

**El defecto real era el diagnóstico, no el rate limit.** El test deserializaba la
respuesta de error como si fuera el array esperado, y el fallo aparecía como
"jrvs is not iterable" — un síntoma que no dice nada del motivo. Corregido: el
spec ahora usa un helper `pedirJson()` que valida el status y, ante un 429, dice
exactamente qué policy se rebasó y cómo evitarlo.

**Con el límite relajado** (`APP_RATELIMIT_OBSERVER_JRVS_LIMIT=100000`, que es
una variable de entorno ya soportada, no un cambio de código), la suite pasa
completa en los 3 proyectos.

> El rate limit **no se toca**: 60/min por usuario es una decisión de producto
> razonable contra abuso. Lo que se corrige es que el E2E —que es una
> herramienta— necesita poder correr sin pelearse con la política de producción.

### 4.3 Hallazgo del E2E: WebKit exige un contexto seguro (https)

⚠️ **H-4 — la cookie `refresh_token` se emite con `Secure`, y WebKit (Safari) no
manda cookies `Secure` por `http://`.**

El proyecto *Mobile Safari — iPhone 12* fallaba entero (13 de 14) mientras
Chromium pasaba. Diagnóstico, por capas:

| Capa | Evidencia | Lectura |
|---|---|---|
| ¿Llega la cookie al navegador? | `ctx.cookies()` devuelve `refresh_token` con `secure=true` en **ambos** navegadores | La cookie sí se guarda |
| ¿La manda el navegador? | `fetch('/api/v1/auth/refresh')` desde la página: Chromium **200**, WebKit **401** | **WebKit no la envía por http://** |
| ¿Es un problema del navegador? | `curl -k` a `http://localhost:8080` funciona; el 401 es del backend ante una cookie ausente | No es del backend |
| ¿Afecta a producción? | En producción el backend está detrás de https (Railway) | **No**: es una limitación del entorno local, no del producto |

Chromium trata `http://localhost` como *contexto potencialmente confiable* y sí
envía cookies `Secure` ahí. WebKit no. La diferencia es del navegador, no del
código: la cookie `Secure` es lo correcto para producción.

**Cómo se corrió igual, sin tocar el código del producto:** un proxy TLS local
(certificado autofirmado) delante de Vite y del backend, para que el navegador
vea `https://localhost:8443` — la misma condición de producción. Con eso, WebKit
pasa los 17 tests.

Dos ajustes de entorno que hizo falta (nada de esto es código del producto):

- El backend necesita `CORS_ALLOWED_ORIGINS` con `https://localhost:8443`: con el
  proxy, el `Origin` que ve la API cambia y la lista fail-closed lo rechaza.
- El proxy debe rutear **solo** `/api` al backend. `/terms` y `/privacy` son
  rutas del frontend, y cualquier prefijo más amplio se traga los chunks lazy de
  Vite (`terms-acceptance.page-*.js` devolvía 401 y rompía la app).

> El proxy TLS y el global setup alterno fueron **temporales y no se versionaron**:
> son andamiaje de esta máquina, no del repo.

---

## 5. Calidad de los commits

| Criterio | Resultado |
|---|---|
| Formato convencional `type(scope): message` | ✅ 14/14 commits del SDD |
| Sin co-author IA | ⚠️ **2 commits con `Co-Authored-By: Claude Haiku 4.5`**, pero **ninguno es de este SDD** (son de un arreglo previo de email, `7eb304a`/`7149f73`). Verificado commit por commit. |
| Un commit por tarea | ✅ 14 commits, uno por fase/tarea |
| Mensajes que referencian la spec | ✅ varios citan la fase y el T-PERF-OPT-01 |
| Historia reescrita | ❌ no (el `1bf7dcb` se conserva, con el N.9 pendiente intacto) |
| Marcas de IA en el código | ❌ ninguna |

**Deuda que este SDD hereda** (no la introdujo): el repo tiene commits con co-author IA. AGENTS.md
lo prohíbe. No se reescribió historia (decisión explícita), así que queda como deuda del repo, no de
este cambio.

---

## 6. Alineación con AGENTS.md

| Regla | Cumplimiento |
|---|---|
| Capas resource → application → domain → persistence | ✅ el endpoint batch vive en `ObserverResource`, delega a `ActaService`, consulta en `ActaRepository` |
| Autorización con `Autorizador.exigir("<permiso>","<ROL>")`, no `@RolesAllowed` | ✅ el SDD pedía `@RolesAllowed({"OBSERVER"})`; se implementó `autorizador.exigir("acta:read")` + `@RateLimited("observer-jrvs")`. **Desviación deliberada y documentada.** |
| RLS obligatorio | ✅ `RlsIsolationIT` en verde (7 tests); el endpoint batch no salta RLS |
| Liquibase es el único dueño del schema | ✅ los índices entran por migraciones `048`/`049` registradas en `db.changelog-master.yaml`; nadie ejecuta DDL a mano |
| Mapeo ORM explícito, sin conversiones implícitas | ✅ `@ManyToOne(fetch = LAZY)` + `@BatchSize` explícitos |
| Caché explícita y medible | ✅ `shareReplay(1)`; el SDD no añadió caché de servidor (la decisión D2 de Valkey es de otro SDD) |
| Sin secretos versionados | ✅ ninguna variable sensible en los cambios |
| Sin BYTEA para evidencias | ✅ no aplica |
| Cada transición del acta necesita cobertura de integración | ✅ el flujo de registro se cubre con el IT del catálogo batch |
| E2E contra la misma API de producción, sin mocks | ✅ el E2E del flujo Observer corre contra la API real (rol de aplicación, RLS activo), sin mocks en la ruta de registro de acta |
| Push a Railway / CF Pages / Neon / R2, `k8s/` legado | ✅ no se toca infraestructura de despliegue |

---

## 7. Qué falta y por qué

| # | Falta | Impacto | Por qué |
|---|---|---|---|
| 1 | **Medición del pool contra Neon** (D.2-c y D.2-d) | Medio | El criterio de D.2 quedó redefinido a lo observable en esta topología (0 conexiones esperando, 0 errores de adquisición, pool cliente chico, 0 errores tras el cold start). Los dos primeros están medidos en local; los dos últimos **exigen el compute real** y son medición post-deploy. No es simulable: PgBouncer y la suspensión del compute solo existen en Neon. |
| 2 | **Recalibrar el pool** (`max-size=50`, `min-size=10`, `idle-removal-interval=5m`) | Medio | Con PgBouncer delante, `max-size=50` no compra concurrencia y `min-size=10` + `idle-removal-interval=5m` chocan con la suspensión de Neon (~5 min). Por decisión del humano **el pool se deja como está y el riesgo queda documentado** (ver `tasks.md`, "Riesgo abierto: pool sin calibrar"), con los valores recomendados y el procedimiento para decidir con dato. |
| 3 | **Métricas del pool: re-verificar tras el deploy** | Bajo | Las claves son de tiempo de build. Hay que confirmar que el rebuild de la imagen de Railway las lleva. |
| 4 | **E2E contra la API desplegada** | Bajo | El E2E se ejecutó y pasó contra la API real local (§4.1). Repetirlo contra el despliegue de Railway cierra el círculo con la latencia real. |
| 5 | **Deuda de co-author IA en el repo** | Bajo | Heredada, no introducida aquí. AGENTS.md la prohíbe; se corrige en un barrido aparte, sin reescribir historia. |

---

## 8. Recomendaciones de rework

No hay rework que bloquee el merge. Antes de producción:

1. **Medir el pool contra Neon** con los criterios D.2-c y D.2-d, y ajustar `max-size` con dato, no
   con la proyección del SDD. La tabla de valores recomendados y el procedimiento de decisión están
   en `tasks.md` ("Riesgo abierto: pool sin calibrar").
2. **Repetir el E2E del flujo Observer contra la API desplegada** en Railway, para cerrar el círculo
   con la latencia real y el pool calibrado.
3. **Revisar el `acta_detalle` en producción con datos reales**: el seq scan observado a escala de
   laboratorio no dice qué pasará con 50 000 actas y sus detalles.
4. **Revisar la suite E2E contra el rate limit de producción**: la suite de 3 proyectos rebasa los
   60/min de `observer-jrvs`. Para CI con backend real, hay que correr el backend con
   `APP_RATELIMIT_OBSERVER_JRVS_LIMIT` relajado (ya soportado por variable de entorno).
5. **El E2E sobre WebKit necesita https local**: Safari no manda cookies `Secure` por `http://`.
   En CI con el backend detrás de https no hay problema; contra un backend local por http, el
   proyecto *Mobile Safari* no puede autenticarse. Es una limitación del entorno, no del producto.

Después del merge:

5. Barrido de los commits con co-author IA en todo el repo (sin reescribir historia: `git filter-repo`
   sobre ramas lineages, o nota de política).

---

## 9. Veredicto

# ✅ CONFORM

**Listo para merge.** Todo lo que el SDD pedía implementar está implementado, con la arquitectura
del proyecto respetada, la suite en verde (298 backend + 1153 frontend + 152 contract), CI real en
verde y los gates verificados con evidencia medida. Los dos huecos que la verificación anterior
dejó abiertos (⚠️ PARTIAL) están cerrados:

| Hueco anterior | Cómo se cerró |
|---|---|
| **E2E del flujo Observer no ejecutado** | Ejecutado contra la API real, 3 proyectos de navegador, 0 fallos. El flujo completo —incluido el 409 de idempotencia— pasa contra el backend con el rol de aplicación (RLS activo). (§4.1) |
| **Criterio D.2 medido contra Postgres local y no contra Neon** | El criterio "60-80 % de ocupación" **no es observable** en esta topología: con PgBouncer delante, ese porcentaje es de PgBouncer. Por decisión del humano se **redefinió** a lo que sí se puede medir (0 conexiones esperando, 0 errores de adquisición, pool cliente chico, 0 errores tras el cold start). Los dos primeros quedan CONFIRMADOS; los dos últimos son medición post-deploy, que es donde corresponde. |

Lo que queda pendiente **no es un defecto de implementación**: es medición contra la base real, que
no se puede simular, y está documentado con su criterio, su procedimiento y sus valores
recomendados.

### Hallazgos de esta verificación (4)

| # | Hallazgo | Estado |
|---|---|---|
| H-1 | El IT del catálogo dependía de datos que otros IT borran (falso verde posible) | ✅ corregido — el IT arma su propio dataset |
| H-2 | El test de conteo de queries no distinguía EAGER de LAZY (`getQueryExecutionCount()` solo ve JPQL) | ✅ corregido — `getPrepareStatementCount()` y dataset de roles distintos |
| H-3 | El E2E fallaba con "jrvs is not iterable": era el 429 del rate limit `observer-jrvs`, y el test no distinguía un error de un array | ✅ corregido — helper `pedirJson()` que valida el status y explica el 429 |
| H-4 | WebKit no manda cookies `Secure` por http://, así que el proyecto iPhone 12 no podía autenticarse contra el backend local por http | ✅ resuelto en el entorno — proxy TLS local; en producción el backend ya está detrás de https |

### Nota sobre el pool (sin cambiar configuración)

El pool **se deja como está** (`min-size=10`, `max-size=50`), por decisión del humano: recalibrar a
ciegas, sin medir contra Neon, sería adivinar. El riesgo queda documentado con valores
recomendados concretos y el procedimiento para decidir con dato:

- `max-size=50` es innecesario con PgBouncer delante (el techo real lo impone
  `default_pool_size` de PgBouncer).
- `min-size=10` + `idle-removal-interval=5m` chocan con la suspensión de Neon a los ~5 min: el
  síntoma esperado son errores transitorios de conexión tras el cold start, no saturación.
- Con 0.25 CU, `max_connections=112`: un `min-size=10` retiene slots del compute permanentemente.

La compatibilidad del backend con PgBouncer está **verificada**: el RLS usa
`set_config('app.current_user_id', ..., true)` (= `SET LOCAL`, soportado en transaction mode) y el
código no usa advisory locks, `LISTEN`/`NOTIFY`, tablas temporales ni `SET` de sesión.
