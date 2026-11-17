# Informe — Cobertura E2E y simulación del sistema

**Proyecto:** Control-s-Elector (backend Quarkus `ec.twintailcs.electoral` + frontend Angular/Ionic)
**Fecha:** 13-nov-2026 · **Re-verificado:** 13-nov-2026 16:55 (ver §9)
**Rama:** `brydyan/sc-510/t-fe-06-observer-mis-actas-sdd`
**Herramientas:** CodeGraph CLI (índice local `556 archivos / 7 988 nodos / 16 910 aristas`), lectura de recursos, ejecución real de las suites.

---

## 1. Resumen ejecutivo

**Sí existen E2E, y son tres capas distintas.** El problema no es la ausencia de pruebas: es que
ninguna capa que simula el sistema completo está hoy en verde, y la que más se acerca (el E2E de
navegador) está construida sobre un contrato de API que el backend no tiene.

| Veredicto | Detalle |
|---|---|
| 🟢 **Gate de contrato arreglado** | Commit `803bb7c` + `6f3ffdd` + `6a081f1` + `527f429` + `b0c5aa7` → **122 tests, 0 errores**. Verificado contra backend vivo. §9 |
| 🔴 **E2E de navegador no pasa** | Playwright: **66 tests → 59 fallos, 7 pasos** (con backend real en pie). Ningún spec despacha los dos overlays de consentimiento. |
| 🔴 **Invariante violado** | El perfil exige *«nada de mocks en la ruta de registro de acta»*. La ruta crítica de registro está **100 % mockeada** en Playwright: nunca toca el backend. |
| 🟡 **Mocks con contrato falso** | Los mocks codifican un envelope de paginación, un DTO y un vocabulario de estados que **el backend no emite**. Los tests verdes validan el mock, no el sistema. |
| 🟡 **E2E fuera de CI** | `ci.yml` corre format, lint, unit y build. **No hay job de Playwright.** El E2E puede estar rojo semanas sin que nadie lo note. |
| 🟢 **Backend en verde** | El módulo Quarkus pasa **133/133 tests**, spotless limpio y piso de JaCoCo cumplido. El backend no está roto: lo que está roto es el E2E. |
| 🟢 **Cobertura real disponible** | La suite de contrato cubre máquina de estados, RBAC 4×5, integridad electoral bajo concurrencia y forma de errores. k6 cubre carga contra API real. Ambas existen y son el andamiaje correcto. |

**La conclusión operativa:** el sistema **sí se puede simular hoy** — con la suite de contrato
(negro de caja, API real) y con k6. Lo que no existe es un E2E de navegador que cruce el Angular
real contra el Quarkus real, y esa es la pieza que falta y la que el invariante del perfil exige.

---

## 2. Inventario de lo que existe

### 2.1 Suite de contrato HTTP — `backend/contract-tests/` (la más valiosa)

| Suite | Cubre |
|---|---|
| `AuthContractIT` | login, `/me`, refresh con rotación, logout, register |
| `RbacContractIT` | matriz rol × endpoint (4 roles + anónimo) |
| `ObserverActasContractIT` | catálogo, registro, borrador, envío, corrección, foto, incidencias, 409 duplicado |
| `CoordinatorActasContractIT` | bandeja, disputa, corrección, aprobación, anulación, PDF, **máquina de estados completa hasta ANULADA** |
| `EvidenciasContractIT` | upload multipart, metadata, URL prefirmada, estado, observaciones, delete |
| `IntegridadElectoralContractIT` | **suma de votos descuadrada → 400** y **carrera real: dos registros simultáneos → exactamente un 201 y un 409** |
| `ConfiguracionContractIT` / `MenusContractIT` / `DashboardContractIT` / `ErroresContractIT` | catálogos, menús por permisos, dashboard, shape de 400/401/403/404/409 + OpenAPI |

- **Naturaleza:** black-box puro, habla solo HTTP. Corre contra un backend vivo (`-Dcontract.baseUrl`).
- **Dónde corre:** job `backend-contract` de `.github/workflows/ci.yml`, en cada push/PR que toca `backend/**`.
- **Estado medido hoy:** ❌ **73 errores de 122** (ver §4.1).

### 2.2 E2E de navegador — `frontend/app/e2e/` (Playwright)

| Archivo | Tests | Intercepción de red | Tipo |
|---|---|---|---|
| `j1-observer-happy-path.spec.ts` | 8 | 0 (mocks en `beforeEach`) | flujo feliz de registro |
| `j2-offline-resilience.spec.ts` | 2 | mocks | IndexedDB + cola offline |
| `j3-cuadre-estricto.spec.ts` | 4 | mocks | checksum, 409 idempotencia |
| `j4-disputa-correccion.spec.ts` | 4 | 5 rutas | disputa/corrección |
| `j5-monitoreo-en-vivo.spec.ts` | 4 | 24 rutas | dashboard MASTER |
| `j6-legal-terms.spec.ts` | 3 | 0 | aceptación de términos |
| `j7-auth-inv-02-user-invitations.spec.ts` | 3 | 0 | invitaciones |
| `j8-design-system.spec.ts` | 2 | 0 | design system |
| `observer-mis-actas.spec.ts` | 24 | 2 rutas | lista de actas del observador |
| `journeys/j1…j5` (5 specs) | 12 | 0 | journeys coordinator |
| **Total** | **66** | | |

- **Estado medido hoy:** ❌ **55 fallos / 11 pasos** en el proyecto `Desktop Chrome` (12,0 min).
- **Dos configs Playwright en conflicto:** `frontend/app/playwright.config.ts` (`testDir: './e2e'`,
  2 proyectos: Desktop Chrome + Pixel 7) y `frontend/app/e2e/playwright.config.ts`
  (`testDir: './e2e/journeys'` → resuelve a `e2e/e2e/journeys`, **ruta inexistente**). La segunda es
  código muerto con un `webServer` que además levanta `npm run start` en el directorio equivocado.
- **No hay `storageState` ni `globalSetup`**: no existe forma de que un test herede sesión.

### 2.3 Pruebas de carga — `frontend/tests/load/` (k6)

Escenarios `login`, `registrar-acta`, `listar-actas`, `mixed-realistic`, `stress-test`, `invitations`.
Golpean la API real: `POST /api/v1/auth/login`, `GET /api/v1/observer/jrvs`, `POST /api/v1/observer/actas`,
`GET /api/v1/coordinator/actas/{id}`, `GET /api/v1/dashboard/resumen`.
Corre en `load-test-baseline.yml` (20 VUs / 5 min) con su propio backend Docker + Postgres + seed.
**Valor:** es la única prueba que hoy ejercita el registro de acta contra la API real — pero afirma
**umbrales de latencia**, no correcciones de contrato.

### 2.4 Módulo Quarkus — `backend/electoral-api-quarkus/src/test/`

25 archivos: unitarios de auth (`InvitationTokenServiceTest`, `PasswordValidationServiceTest`,
`ActivationRateLimiterTest`…), `RlsIsolationIT`, `UserInvitationIT`, `LegalFlowIT`, `CorsPreflightIT`.

**Ausente:** ningún test del dominio del acta. Ver §4.2.

**Estado medido hoy:** ✅ **133 tests, 0 fallos, 0 errores. Spotless limpio. Piso de JaCoCo cumplido.
BUILD SUCCESS** (1 min 20 s). El módulo backend está en verde; el rojo está en la capa de contrato.

---

## 3. Matriz endpoint × rol (extrída con CodeGraph + lectura de los resources)

Autorización efectiva por `Autorizador.exigir(...)` en cada resource:

| Endpoint | Guard | MASTER | COORD | ADMIN | OBSERVER |
|---|---|:--:|:--:|:--:|:--:|
| `POST /observer/actas` | `acta:upload` | ✅ | ❌ | ❌ | ✅ |
| `POST /observer/actas/borrador` | `acta:upload` | ✅ | ❌ | ❌ | ✅ |
| `POST /observer/actas/{id}/enviar` | `acta:upload` | ✅ | ❌ | ❌ | ✅ |
| `PUT /observer/actas/{id}/corregir` | `acta:upload` + autoría | ✅ | ❌ | ❌ | ✅ (autor) |
| `POST /observer/actas/{id}/imagen` | `acta:upload` | ✅ | ❌ | ❌ | ✅ |
| `POST /observer/incidencias` | `acta:upload` | ✅ | ❌ | ❌ | ✅ |
| `GET /observer/actas/{id}` | `acta:upload` + autoría | ✅ | ❌ | ❌ | ✅ (autor) |
| `GET /observer/mis-actas` \| `correcciones-pendientes` \| `jrvs` \| `jrv/{id}/cargos` | sesión | ✅ | ✅ | ✅ | ✅ |
| `GET /coordinator/actas` \| `/{id}` \| `/historial` | rol | ✅ | ✅ | ❌ | ❌ |
| `PUT /coordinator/actas/{id}/disputar` \| `/corregir` \| `/aprobar` | rol | ✅ | ✅ | ❌ | ❌ |
| `DELETE /coordinator/actas/{id}` | `acta:anular` | ✅ | ❌ | ❌ | ❌ |
| `POST /coordinator/actas/{id}/imagen` | rol | ✅ | ✅ | ❌ | ❌ |
| `GET /coordinator/incidencias` | rol | ✅ | ✅ | ✅ | ❌ |
| `GET /coordinator/actas/{id}/expediente-pdf` | rol | ✅ | ✅ | ✅ | ❌ |
| `GET /dashboard/resumen` \| `/resultados` \| `/progreso` \| `/novedades` | rol | ✅ | ✅ | ✅ | ❌ |
| `POST /evidencias` | `evidencia:upload` | ✅ | ✅ | ✅ | ❌ |
| `GET /evidencias/{id}` \| `/download-url` | `evidencia:read`/`download` | ✅ | ✅ | ✅ | ❌ |
| `PATCH /evidencias/{id}/estado` \| `/observaciones` | `acta:aprobar`/`evidencia:update` | ✅ | ✅ | ✅ | ❌ |
| `DELETE /evidencias/{id}` | `evidencia:delete` | ✅ | ✅ | ✅ | ❌ |

Los «holes» documentados en el README de contrato (`KNOWN HOLE #121`: OBSERVER y ADMIN pasando
`disputar`/`corregir`/`aprobar`) **ya no existen** — `RbacContractIT` no tiene ningún assert con ese
prefijo. El guard de método está correctamente implementado.

---

## 4. Hallazgos

### 4.1 🔴 H1 — La suite de contrato está roja: 73 de 122 tests errorean

**Causa raíz identificada.** El commit `803bb7c` (hoy, 14:24) cambió el helper compartido:

```java
// antes
return r.jsonPath().getList("$");     // array simple
// después (commit 803bb7c)
return r.jsonPath().getList("content"); // envelope Spring
```

Pero **ningún endpoint del backend Quarkus devuelve `content`**:

- `GET /observer/jrvs`, `GET /observer/jrv/{id}/cargos`, `GET /configuracion/candidatos` → **array simple** (`[{...}]`)
- `GET /observer/mis-actas`, `GET /coordinator/actas` → **`{data, meta}`** (`PagedResponse`)

Con `getList("content")` sobre un array simple, Jayway devuelve una lista de `null` → NPE en
`jrv.get("id")`. Resultado: `73 errors`, todos con la misma firma:

```
NullPointerException: Cannot invoke "java.util.Map.get(Object)" because "jrv" is null
  at ContractTestSupport.comboLibre:223
```

**Impacto:** el job `backend-contract` de CI — el gate de contrato del backend — falla. Cualquier PR
que toque `backend/**` está rojo hoy.

**Corrección propuesta** (helper tolerante a las tres formas):

```java
protected static List<Map<String, Object>> listar(Response r) {
  assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
  String body = r.asString().trim();
  if (body.startsWith("[")) {
    return r.jsonPath().getList("$");
  }
  List<Map<String, Object>> data = r.jsonPath().getList("data");
  return data != null ? data : r.jsonPath().getList("content");
}
```

> **Nota de producto:** el frontend arrastra **la misma suposición equivocada** (§4.3). No es un
> error aislado del helper: es un envelope de paginación que nunca se estandarizó. La decisión de
> fondo — ¿`{data,meta}` del backend o `{content,…}` del front? — requiere un pronunciamiento
> humano; mientras tanto, lo correcto es que **ambos lados usen `{data, meta}`**, que ya es lo que
> el backend emite y lo que `coordinator.api.ts` consume bien.

### 4.2 🔴 H2 — Se perdieron las pruebas de integración de las invariantes del acta

El README de `contract-tests` declara explícitamente que la suite de contrato **no** cubre:

| Invariante | Test que el README dice que lo cubre | ¿Existe hoy? |
|---|---|---|
| Locking pesimista `SELECT FOR UPDATE` por `jrv_id + cargo_id` | `ActaConcurrencyIT` | ❌ no existe |
| Suma de votos `Σ(detalle.votos) + nulos + blancos == total_sufragantes` | `ActaInvarianteIT` | ❌ no existe |
| Aislamiento por `registrada_por_id` | `ActaAislamientoObserverIT` | ❌ no existe |
| Máquina de estados del acta | `ActaCicloVidaIT` | ❌ no existe |
| RLS de Postgres | `ActaRlsIntegrationIT` | ✅ `RlsIsolationIT` |
| Flujo de auth completo | `AuthFlowIT` | ⚠️ parcial (solo contrato HTTP) |
| PDF del expediente | `ExpedientePdfIT` | ⚠️ parcial (solo contrato HTTP) |

**Verificado:** esos nombres solo aparecen en `backend/contract-tests/README.md` y en documentos
archivados de `openspec/` y `odd/`. **No hay un solo archivo `.java` con esos nombres.** El módulo
Quarkus no tiene ningún test de dominio del acta (`grep` de `forUpdate|PESSIMISTIC|checksum|totalSufragantes`
en `src/test` → **cero resultados**).

Estos tests existían en el backend Spring y se perdieron en el corte a Quarkus (#139). La cobertura
actual de esas invariantes es **solo black-box por HTTP** (`IntegridadElectoralContractIT` sí prueba
la carrera real 201/409 y el descuadre de votos → 400, lo cual es valioso, pero no alcanza para
afirmar el locking a nivel de datos ni el aislamiento entre observers).

### 4.3 🔴 H3 — El frontend "Mis Actas" está desconectado de la API real

**Medido contra el backend vivo** (`GET /api/v1/observer/mis-actas` con token de observer):

```json
{"data":[{"id":1,"jrvId":1,"cargoId":1,"registradaPorId":4,"estado":"ENVIADA",
  "totalSufragantes":295,"votosBlancos":10,"votosNulos":5,
  "fechaRegistro":"2026-11-13T18:27:19Z","motivoAnulacion":null,
  "nombreCargo":"Alcalde/sa","numeroJrv":"JRV-SE-001","totalVotosCandidatos":null}],
 "meta":{...}}
```

Lo que el frontend espera (`frontend/app/src/app/features/observer/data/mis-actas.api.ts`):

```ts
interface ActaDTO {
  id: string; numeroActa: string; jrvNombre: string;
  fechaTransmision: string; estado: 'TRANSMITIDO'|'REVISION'|'BORRADOR'|'ENVIADA';
  accionLabel: string;
}
interface PageResult<T> { content: T[]; totalElements: number; totalPages: number;
  currentPage: number; size: number; }
```

**Cuatro desajustes independientes:**

| # | Front espera | Backend emite | Efecto |
|---|---|---|---|
| 1 | `content` | `data` | `this.actas.set(response.content)` → `undefined` |
| 2 | `totalElements` en la raíz | `meta.totalElements` | paginación rota |
| 3 | `numeroActa`, `jrvNombre`, `fechaTransmision`, `accionLabel` | `numeroJrv`, `nombreCargo`, `fechaRegistro`, `motivoAnulacion` | 4 columnas vacías |
| 4 | `currentPage` | `meta.page` | control de página roto |

Con `actas()` en `undefined`, la plantilla evalúa `actas().length === 0` → **excepción de render**.
La pantalla "Mis Actas" no puede funcionar contra el backend real.

Además, el filtro por estado usa valores que el backend **no conoce**:

```bash
$ curl ".../api/v1/observer/mis-actas?estado=REVISION"
HTTP 400  {"message":"invalid parameter format: estado"}
```

El enum real es `BORRADOR | ENVIADA | EN_DISPUTA | CORREGIDA | APROBADA | ANULADA`.

### 4.4 🔴 H4 — La ruta de detalle que navega la página no existe

`mis-actas.page.ts:126` navega a `/observer/mis-actas/{id}/detalles`.
`app.routes.ts` **no define esa ruta** (las de observer son: `mis-jrvs`, `jrv/:jrvId/cargos`,
`jrv/:jrvId/cargo/:cargoId/registrar`, `acta/:id/corregir`, `mis-actas`).

El E2E correspondiente (`observer-mis-actas.spec.ts:222`) mockea `/observer/mis-actas/{id}` — que
tampoco existe en el backend, cuya ruta real es `/observer/actas/{id}` — y afirma
`expect(page.url()).toContain('/observer/mis-actas/')`, que **es cierto incluso si la navegación
falla**. El test pasa por la razón equivocada.

### 4.5 🔴 H5 — La ruta crítica de registro de acta está 100 % mockeada

El invariante del perfil es explícito: *«Los E2E de flujo crítico corren contra la misma API que
producción: nada de mocks en la ruta de registro de acta.»*

`j1-observer-happy-path.spec.ts` (el "happy path" del registro) intercepta **todo** el tráfico:

```
GET  /api/v1/observer/jrvs                      → stub
GET  /api/v1/observer/jrv/1/cargos              → stub
GET  /api/v1/configuracion/candidatos?cargoId=10 → stub
POST /api/v1/observer/actas/borrador             → 201 { id: 999 }
POST /api/v1/observer/actas/999/imagen           → 200
POST /api/v1/observer/actas                      → 201 { id: 999, estado: ENVIADA }
```

**Lo que el E2E no puede detectar por construcción:**
- que la suma de votos no cuadre en el servidor (el checksum vive en el dominio),
- que el `Idempotency-Key` duplicado no devuelva 409,
- que el locking pesimista no bloquee la segunda acta del mismo par,
- que la RLS aísle a un observer de las actas de otro,
- que el `AuditLog` registre la transición,
- que la evidencia llegue a S3/RustFS y no a la nada,
- que el `X-Forwarded-For` y la trazabilidad se persistan.

Además, los mocks **no validan el body de la petición**: aunque el front mandara un payload roto,
el E2E seguiría verde.

### 4.6 🔴 H6 — 55 de 66 tests E2E fallan por los overlays de consentimiento

Causa raíz medida en `test-results/*/error-context.md`: la app monta **dos overlays bloqueantes**
en la primera visita y ningún spec los despacha:

```
- heading "Privacidad y Cookies" [level=3]
- button "Rechazar Todo" / "Personalizar" / "Aceptar Todo"
- dialog "Términos de Uso Actualizados":
  - "Para seguir usando el sistema necesitas leer y aceptar la versión v1.0 de los Términos…"
```

El síntoma (`TimeoutError: locator.click: Timeout 10000ms exceeded` →
`<div class="cookie-banner"> subtree intercepts pointer events`) se repite en 11 specs
(j1, j2, j3, j4, j5, j7, j8 y los 12 de `observer-mis-actas`).

**No es un problema del mock: es un requisito de fixture que no existe.** Sin un `storageState` o un
`beforeEach` que acepte cookies y términos, **ningún** E2E de este proyecto puede pasar.

### 4.7 🟡 H7 — Los 5 `journeys/*.spec.ts` son ejecutables pero no ejecutables

`frontend/app/e2e/journeys/*` no tienen ni una sola intercepción de red **ni** login:

- usan `@playwright/test` crudo (no el fixture `auth.fixture`), `browser.newPage()` sin autenticar;
- `ActasListaPage.goto()` espera `[role="table"]` → el guard redirige a `/login` → timeout en `beforeEach`;
- `AuthFixture.loginAs()` en `fixtures/pages.ts:136` es un **stub vacío** que solo hace `console.log`
  (con el comentario *«For now: assume we're already logged in or use API mock»*) — `j4` depende de él;
- `j5` simula un 409 con `route.abort('failed')` (error de red, no 409) y verifica
  `verifyErrorMessage('conflict')`, que busca la cadena «conflict» en cualquier `div`;
- `j4` verifica que «Anular» esté deshabilitado para COORDINATOR y habilitado para MASTER en la
  **misma página**, sin re-autenticar: imposible con el stub.

Los 12 tests de `journeys/` fallaron tal cual está el código.

### 4.8 🟡 H8 — Dos configs Playwright y un `webServer` que no arranca

| | `frontend/app/playwright.config.ts` | `frontend/app/e2e/playwright.config.ts` |
|---|---|---|
| `testDir` | `./e2e` (cubre los 66) | `./e2e/journeys` → resuelve a `e2e/e2e/journeys` (**inexistente**) |
| `webServer` | comentado | `command: 'npm run start'` desde `e2e/` (directorio sin `package.json`) |
| proyectos | Desktop Chrome + Pixel 7 | chromium + firefox |
| `baseURL` | `E2E_BASE_URL` env, default `:4200` | `:4200` fijo |

La segunda config es una trampa: si alguien la ejecuta, no encuentra tests; si la arregla, arranca
el servidor desde el directorio equivocado. **Debe borrarse o fusionarse.**

### 4.9 🟡 H9 — El E2E de navegador no está en CI

`.github/workflows/ci.yml` produce 6 checks requeridos: Backend Format, Backend Tests & Coverage,
Frontend ESLint, Frontend Format Check, Frontend Production Build, Frontend Unit Tests.
**No hay job de Playwright.** `load-test-baseline.yml` sí corre k6, pero es manual/programado y
afirma umbrales, no correcciones.

Consecuencia: la suite puede estar 100 % roja (como está hoy) sin señal alguna en los PRs.

### 4.10 🟡 H10 — Mocks que apuntan a endpoints inexistentes

| Mock | Ruta mockeada | ¿Existe en el backend? |
|---|---|---|
| `mockCoordinatorMisActas` | `GET /api/v1/observer/actas` | ❌ el listado es `GET /api/v1/coordinator/actas` |
| `mockCantonReportes` | `GET /api/v1/coordinator/reportes` | ❌ no existe; el dashboard es `/api/v1/dashboard/*` |
| `mockObserverActaDetalle` | `GET /api/v1/observer/mis-actas/{id}` | ❌ el detalle es `/api/v1/observer/actas/{id}` |

Un mock que no casa con ninguna request real no prueba nada: la request sale a internet (proxy de
dev) y el test pasa o falla por el motivo equivocado.

### 4.11 🟡 H11 — Vocabulario de estados falso en los mocks

`STUB_ACTAS_MIS_ACTAS` usa `estado: 'TRANSMITIDO'` y `'REVISION'`. **Ninguno existe** en el enum
`EstadoActa` del backend. Los tests filtran por esos valores (`J3: should filter actas by estado (REVISION)`,
`J3: should show only TRANSMITIDO when filter applied`) y pasan contra el mock, pero la API real
devuelve **400** para ambos (§4.3).

### 4.12 🟡 H12 — Documentación de contrato desalineada

- `contract-tests/README.md` dice que el MASTER sembrado es `admsismas@control.ttcs` con default
  `contract.master.email`. En el backend Quarkus, `DataSeeder` fue sustituido por la migración
  `026-admin-inicial.sql`, que crea **`admin@control-elector.local`** (contraseña `admin12345`).
  Con los defaults documentados, el login del MASTER devuelve **401**.
- El mismo README sigue remitiendo a los tests JPA del backend Spring que ya no existen (§4.2).
- `frontend/app/e2e/README.md` dice *«API endpoints mocked/stubbed in test setup»* y
  *«Status: Phase 2b (E2E) — Ready for execution»*: describe un estado que ya no es cierto.

---

## 5. E2E que **sí** se pueden realizar para simular el sistema

Ordenados por relación valor/esfuerzo. Los tres primeros niveles existen hoy; el 4 es el que falta.

### Nivel 0 — Simulación por API (HTTP puro) · ✅ existe · ❌ rojo hoy

**Qué simula:** el sistema completo por su contrato, sin navegador.
**Cómo se corre:**
```bash
# stack vivo
docker compose --profile dev --profile s3 up -d
cd backend/contract-tests && mvn -B verify -Dcontract.baseUrl=http://localhost:8083
```
**Qué cubre de verdad:** máquina de estados completa, matriz rol × endpoint, 409 por par duplicado,
carrera 201/409 con concurrencia real, descuadre de votos → 400, forma de errores, RLS vía
`RlsIsolationIT`.
**Bloqueo:** H1 (helper `listar()`). Arreglando H1, la suite vuelve a verde y es el gate principal.
**Valor:** ★★★★★ — es la simulación más fiel al sistema por unidad de esfuerzo.

### Nivel 1 — Carga y picos (k6) · ✅ existe · ✅ verde en CI

**Qué simula:**Behaviour de scrutinio — N observers registrando actas contra la API real, con
métricas de latencia y tasa de error.
**Cómo se corre:** `.github/workflows/load-test-baseline.yml` (20 VUs / 5 min) o
`frontend/tests/load/run.sh`.
**Cubre de verdad:** el `POST /api/v1/observer/actas` real, con locks, contra Postgres real.
**Brecha:** afirma umbrales, no semántica. **Debe complementarse** con un escenario k6 que *afirme*
el invariante (p. ej. `registrar-acta` debe recibir 409 en el segundo intento del mismo par).

### Nivel 2 — E2E de navegador contra API real (Playwright + stack) · ⚠️ parcial · ❌ rojo

**Qué simula:** el Angular real ejecutando el flujo real contra el Quarkus real, con Postgres,
RustFS y RLS reales.
**Qué falta para que exista de verdad:**
1. **Despachar los overlays** de cookies y términos (fixture global o `storageState`).
2. **Autenticación real** contra `POST /api/v1/auth/login` (borrar los stubs de `api-mocks.ts`).
3. **Datos reales**: registrar observer/coordinator vía la API de invitaciones; reservar pares
   (JRV, cargo) libres con el mismo mecanismo `comboLibre()` que usa la suite de contrato.
4. **Un solo config Playwright**, con `webServer` que levante backend + Postgres + RustFS.
5. **Job de CI** que lo ejecute (hoy no existe).

**Flujos que se pueden simular con este nivel** (y que hoy no se simulan en ningún sitio):

| # | Flujo | Invariante que verifica |
|---|---|---|
| E2E-01 | Observer: JRV → cargo → foto → votos → checksum → revisar → **transmitir** | suma de votos en servidor; persistencia real; `AuditLog`; evidencia en object storage |
| E2E-02 | Observer: **reintento con la misma `Idempotency-Key`** | 409 y **no duplicar el acta** (invariante explícito del perfil) |
| E2E-03 | Observer: **corte de red** → cola IndexedDB → reconexión → envío | idempotencia por clave de negocio; no duplicar el envío |
| E2E-04 | Observer: acta en disputa → **corregir** → reenviar | transición `EN_DISPUTA → CORREGIDA`; el observer solo puede corregir la suya |
| E2E-05 | Coordinator: bandeja → filtrar por estado → buscar → paginar | envelope de paginación real; forma `{data,meta}` |
| E2E-06 | Coordinator: **disputar → corregir → aprobar** | máquina de estados real, con historial auditado |
| E2E-07 | Master: anular (motivo obligatorio) | `acta:anular` exclusivo del MASTER; `ANULADA` terminal |
| E2E-08 | **Matriz RBAC en el navegador**: COORDINATOR no ve «Anular»; OBSERVER no entra a `/coordinator/*` | permisos reales, no `disabled` cosmético |
| E2E-09 | Master: **invitar** usuario → activación → primer login | flujo de invitación end-to-end con correo real |
| E2E-10 | **Aislamiento**: observer A no ve las actas de observer B | RLS + filtro por `registradaPorId` |
| E2E-11 | Dashboard: resumen, resultados, progreso, novedades | métricas del pipeline de actas (enviadas / en disputa / aprobadas) |
| E2E-12 | Expediente PDF de una acta impugnada | generación y descarga real |

### Nivel 3 — E2E de contrato UI↔API (el que falta y que más rápido da resultado) · ❌ no existe

**Idea:** un test Playwright **sin mocks de red** que solo afirma que **cada request que el front
emite es aceptada por la API real**. Es la defensa directa contra H3/H10/H11 (mocks con contrato falso):
si el front pide `estado=REVISION` o espera `content`, el test falla en la primera llamada.

**Alcance mínimo viable:** 6 requests (login, `jrvs`, `cargos`, `candidatos`, `mis-actas`, `actas`)
con aserciones sobre forma y enum. Cuesta horas y detecta hoy **4 defectos reales**.

### Nivel 4 — Resiliencia y offline · ⚠️ existe en mock

`j2-offline-resilience.spec.ts` simula el corte de señal, pero contra mocks. El escenario real
(cortar la red del navegador contra backend vivo, reconectar, verificar que la cola drena sin
duplicar) es el que valida el invariante de idempotencia **de verdad**. Playwright soporta
`context.setOffline(true)` con backend real.

### Nivel 5 — Contrato visual / accesibilidad · ✅ parcial

Vitest + axe (`.a11y.spec.ts`) cubren accesibilidad unitariamente; `j8-design-system` cubre foco y
toast. Falta el gate visual (Playwright screenshots) — no es bloqueante para el release inicial.

---

## 6. Plan priorizado

### P0 — Desbloquear (rojo → verde), sin tocar el producto

| # | Acción | Archivo | Esfuerzo |
|---|---|---|---|
| 1 | `listar()` tolerante a array / `{data,meta}` / `{content}` | `backend/contract-tests/.../ContractTestSupport.java` | 15 min |
| 2 | Decidir el envelope de paginación y **alinear front y back** (propuesta: `{data,meta}`) | `frontend/.../observer/data/mis-actas.api.ts` + página | 1 h |
| 3 | Corregir `ActaDTO` de Mis Actas a los campos reales del backend | `frontend/.../observer/data/mis-actas.api.ts` | 30 min |
| 4 | Cambiar el filtro de estado a los valores del enum (`ENVIADA`, `APROBADA`, …) | `mis-actas.page.ts` | 20 min |
| 5 | Crear la ruta `observer/mis-actas/:id/detalles` **o** navegar a `observer/actas/:id` | `frontend/app/src/app/app.routes.ts` | 30 min |
| 6 | Fixture Playwright que acepte cookies + términos y autentique contra la API real | `frontend/app/e2e/fixtures/` | 1–2 h |
| 7 | Borrar `frontend/app/e2e/playwright.config.ts` (config muerta) | — | 5 min |
| 8 | Actualizar `contract-tests/README.md` (email del MASTER, invariantes, estado real) | — | 30 min |

### P1 — Cerrar el invariante del perfil

| # | Acción | Esfuerzo |
|---|---|---|
| 9 | E2E de navegador contra API real para el flujo de registro (E2E-01, E2E-02) | 3–4 h |
| 10 | Job de CI `frontend-e2e` (levanta stack, corre Playwright, sube artefactos) | 2 h |
| 11 | Suite E2E de contrato UI↔API (Nivel 3) — el detector de mocks falsos | 3 h |
| 12 | Portar a Quarkus los invariantes perdidos: locking, aislamiento, suma de votos (§4.2) | 4–6 h |

### P2 — Completar la simulación

| # | Acción | Esfuerzo |
|---|---|---|
| 13 | E2E-03 a E2E-12 (offline real, RBAC en navegador, RLS, invitación, dashboard, PDF) | 2–3 días |
| 14 | k6 con aserciones semánticas (no solo umbrales) | 3 h |
| 15 | Screenshots de regresión visual | 1 día |

---

## 7. Evidencia de ejecución

Todo lo anterior se reprodujo en esta sesión, contra el backend real construido desde el repo.

```bash
# 1. Backend real (Postgres 16 + RustFS + imagen del repo)
docker build -t electoral-api:analysis backend/electoral-api-quarkus
docker run -d --name analysis-pg    -e POSTGRES_DB=electoral_db -e POSTGRES_USER=electoral_user \
  -e POSTGRES_PASSWORD=electoral_pass -p 5432:5432 postgres:16
docker run -d --name analysis-rustfs -e RUSTFS_ACCESS_KEY=rustfsadmin -e RUSTFS_SECRET_KEY=rustfsadmin123 \
  -e RUSTFS_ADDRESS="0.0.0.0:9000" -p 9000:9000 rustfs/rustfs:latest
docker run -d --name analysis-api --network host -e DB_HOST=localhost -e DB_PORT=5432 \
  -e DB_NAME=electoral_db -e DB_USER=electoral_user -e DB_PASSWORD=electoral_pass -e SERVER_PORT=8080 \
  -e QUARKUS_HIBERNATE_GENERATION=none -e QUARKUS_LIQUIDASE_CONTEXTS=seed \
  -e APP_JWT_SECRET=analysis-only-secret-at-least-32-chars-long \
  -e APP_JWT_REFRESH_PEPPER=analysis-only-pepper -e APP_SEED_ADMIN_PASSWORD=admin12345 \
  -e AWS_ACCESS_KEY_ID=rustfsadmin -e AWS_SECRET_ACCESS_KEY=rustfsadmin123 \
  -e AWS_S3_ENDPOINT=http://localhost:9000 -e AWS_S3_BUCKET=electoral-evidences \
  -e AWS_S3_PATH_STYLE=true electoral-api:analysis
# → 88 changesets de Liquibase aplicados; /actuator/health = UP

# 2. Suite de contrato (API real)
cd backend/contract-tests && mvn -B verify -Dcontract.baseUrl=http://localhost:8080
# → Tests run: 122, Failures: 0, Errors: 73   ❌

# 3. Módulo Quarkus (unit + IT) — el backend en verde
cd backend/electoral-api-quarkus && ./mvnw -B clean verify
# → Tests run: 133, Failures: 0, Errors: 0 · spotless clean · jacoco check OK · BUILD SUCCESS

# 4. E2E de navegador (dev server real en :4200)
cd frontend/app && pnpm start &
npx playwright test --project="Desktop Chrome" --reporter=list --timeout=30000
# → 55 failed, 11 passed (12.0m)   ❌
```

### Estado de los checks de CI medido hoy

| Check de CI | Job | Estado medido |
|---|---|---|
| Backend Format | `backend-format` (spotless) | 🟢 limpio |
| Backend Tests & Coverage | `backend-quarkus` | 🟢 **133/133**, jacoco OK |
| **Backend Contract Tests** | `backend-contract` | 🔴 **73 errores de 122** (H1) |
| Frontend ESLint / Format / Unit / Build | `frontend-*` | no ejecutados en esta sesión |
| **Frontend E2E** | — | 🔴 **no existe job**; en local 55/66 rojo |
| Load Test Baseline | `load-test-baseline.yml` | 🟢 verde (afirma umbrales) |

### Resultados que sostienen los hallazgos

| Evidencia | Resultado |
|---|---|
| `GET /api/v1/observer/mis-actas` (real) | `{"data":[{…,"estado":"ENVIADA","numeroJrv":"JRV-SE-001","fechaRegistro":…}],"meta":{…}}` |
| `GET /api/v1/observer/mis-actas?estado=REVISION` (real) | `HTTP 400 {"message":"invalid parameter format: estado"}` |
| `GET /api/v1/observer/jrvs` (real) | `[{…}]` — array simple, sin `content` ni `data` |
| `POST /api/v1/auth/login` con `admsismas@control.ttcs` | `HTTP 401 Credenciales invalidas` (el MASTER real es `admin@control-elector.local`) |
| Contrato `/observer/mis-actas/:id` mockeado | ruta real es `/observer/actas/{id}` |
| Ruta `observer/mis-actas/:id/detalles` | no existe en `app.routes.ts` |
| `assertThat(r.jsonPath().getList("content"))` sobre array simple | lista de `null` → NPE en `comboLibre:223` |
| Primer error de Playwright | `cookie-banner subtree intercepts pointer events` + `dialog "Términos de Uso Actualizados"` |
| `AuthFixture.loginAs()` (`e2e/fixtures/pages.ts:136`) | stub vacío, solo `console.log` |

### Entorno verificado

- CodeGraph local: `556 archivos · 7 988 nodos · 16 910 aristas · java 267 / typescript 243`.
- Playwright 1.65.0 con Chromium instalado (proyecto `Desktop Chrome` ejecutado; el proyecto
  `Mobile Chrome — Pixel 7` duplica la suite y no se ejecutó).
- Backend: Quarkus 3.40.1 sobre JVM, 88 changesets Liquibase, Postgres 16 (locale `es-EC`), RustFS.

---

## 8. Anexo — Cobertura de rutas del frontend

Rutas declaradas en `app.routes.ts` frente a las visitadas por los E2E:

| Ruta | ¿E2E la visita? | Observación |
|---|:--:|---|
| `/login` | ✅ | con mocks |
| `/auth/activate` | ❌ | flujo de activación sin E2E |
| `/auth/account-summary` | ❌ | — |
| `/terminos` · `/contact-dpo` · `/licenses` | ❌ | `/terminos` se usa de rebote |
| `/dashboard` · `/dashboard/resultados` · `/dashboard/progreso` | ❌ | `j5` los mockea |
| `/admin/resultados` · `/admin/progreso` | ❌ | — |
| `/observer/mis-jrvs` | ✅ | mocks |
| `/observer/jrv/:jrvId/cargos` | ✅ | mocks |
| `/observer/jrv/:jrvId/cargo/:cargoId/registrar` | ✅ | mocks (ruta crítica) |
| `/observer/acta/:id/corregir` | ❌ | **corrección en disputa sin E2E** |
| `/observer/mis-actas` | ✅ | mocks, contrato falso |
| `/observer/mis-actas/:id/detalles` | ❌ | **la ruta no existe** |
| `/coordinator/actas` | ✅ | `journeys/`, sin login → no ejecutable |
| `/coordinator/actas/:id` | ✅ | `journeys/`, sin login → no ejecutable |
| `/coordinator/reportes` | ✅ | mockea endpoint inexistente |
| `/admin/usuarios` | ✅ | `j7` con mocks |
| `/admin/sesiones` | ❌ | — |
| `/master/configuracion` · `/master/cargos` · `/master/partidos` · `/master/candidatos` | ❌ | — |
| `/modules/evidencias` · `/modules/evidencias-subir` | ❌ | — |

---

## 9. Re-verificación tras las correcciones (13-nov 16:55)

Se re-ejecutó todo contra backend real reconstruido (Postgres 16 + RustFS + imagen del repo,
88 changesets Liquibase) y dev server real en `:4200`.

### 9.1 Commits revisados

| Commit | Qué cambió |
|---|---|
| `6f3ffdd` | `listar()` acepta array directo **o** Map root |
| `6a081f1` | mensajes de error explícitos en el helper |
| `527f429` | clave correcta `data` (no `content`) para `PagedResponse` + fixes en `CoordinatorActasContractIT` y `ObserverActasContractIT` |
| `b0c5aa7` | `correccionesPendientes` usa el helper `listar()` |
| `d40a259` | reubicación de `docs/` → `documents/test/` |

### 9.2 Estado por hallazgo

| # | Hallazgo | Estado | Evidencia |
|---|---|---|---|
| **H1** | helper `listar()` incompatible con el backend | 🟢 **ARREGLADO** | `Tests run: 122, Failures: 0, Errors: 0` · BUILD SUCCESS (57 s) |
| **H2** | invariantes del acta perdidas (locking, aislamiento, checksum) | 🔴 abierto | `grep` en `src/test` → 0 resultados; los IT solo existen en README/`openspec` |
| **H3** | "Mis Actas" desconectado de la API real | 🔴 abierto | `mis-actas.api.ts` sigue esperando `{content,…}` + `numeroActa/jrvNombre/fechaTransmision`; la API emite `{data,meta}` + `numeroJrv/nombreCargo/fechaRegistro` |
| **H4** | ruta `observer/mis-actas/:id/detalles` inexistente | 🔴 abierto | sigue sin estar en `app.routes.ts` |
| **H5** | ruta crítica de registro 100 % mockeada | 🔴 abierto | `e2e/support/api-mocks.ts` sin cambios (10-nov) |
| **H6** | 55/66 fallos por overlays de cookies + términos | 🔴 abierto, **empeorado** | ahora **59/66 fallos** (ver §9.3) |
| **H7** | `journeys/*` sin login; `AuthFixture` stub | 🔴 abierto | `e2e/fixtures/pages.ts` sin cambios |
| **H8** | dos configs Playwright | 🔴 abierto | ambos archivos sin cambios |
| **H9** | E2E fuera de CI | 🔴 abierto | `ci.yml` sin job de Playwright |
| **H10** | mocks apuntan a endpoints inexistentes | 🔴 abierto | `api-mocks.ts` sin cambios |
| **H11** | vocabulario de estados falso en mocks | 🔴 abierto | `TRANSMITIDO`/`REVISION` siguen en `STUB_ACTAS_MIS_ACTAS` |
| **H12** | documentación de contrato desalineada | 🔴 abierto | `contract-tests/README.md` sin cambios (email del MASTER, invariantes) |

### 9.3 El E2E de navegador empeoró con el backend real en pie

| Corrida | Backend | Resultado |
|---|---|---|
| 1 (14:25) | **apagado** | 55 fallos / 11 pasos (12,0 min) |
| 2 (16:40) | **encendido** | **59 fallos / 7 pasos** (14,8 min) |

Los 7 que pasan son `j6-legal-terms` (3, el único spec que despacha el modal), `j8` toast (1),
`j5` lista vacía (1) y dos de `observer-mis-actas` que no dependen de la tabla.

**Conclusión de la re-verificación:** la suite **no es hermética**. Su resultado depende de que
haya o no un backend escuchando. Con backend real, los endpoints no mockeados responden de verdad y
el modal de términos se vuelve bloqueante. Esto es una razón más para no meterla en CI tal cual:
no es determinista.

Además confirma H6: **todos** los fallos siguen apuntando a `cookie-banner` / `Términos de Uso`
— ninguno tiene otra causa raíz.

---

## 10. Diseño propuesto: E2E en CI partido en shards por criticidad

### 10.1 Corrección al diagnóstico

La premisa «no está en CI porque demora» no es la causa raíz en este repo. Los hechos medidos:

- La suite **no es hermética**: 55/11 sin backend, 59/7 con backend. Un resultado que depende de
  si hay un proceso escuchando no puede ser un check requerido.
- La suite **no es informacional**: 100 % mockeada en la ruta crítica (H5). Un S1 verde hoy probaría
  que los mocks funcionan, no que el sistema funciona.
- La suite **está roja**: 59/66.

El tiempo es un problema real **después** de que la suite valga algo. Partirla en 4 resuelve el
problema de costo, no el de valor. El orden correcto es: **primero volverla real y determinista
(local), después partirla, después meterla a CI.**

### 10.2 Ejes de partición

No partir por cantidad de tests (`--shard=N/M` reparte por conteo, no por criticidad).
Partir por **tag**, de modo que cada shard sea un conjuntoHomogéneo de riesgo:

| Shard | Tag | Contenido | Bloquea PR | Presupuesto |
|---|---|---|:--:|---|
| **S1 — Guardián** | `@guardian` | Registro de acta contra API real; checksum en servidor; idempotencia 409; no-doble-envío tras corte de red | **Sí** | ≤ 4 min |
| **S2 — Ciclo de vida** | `@ciclo` | Disputa → corrección → aprobación → anulación; historial auditado; `acta:anular` exclusivo del MASTER | **Sí** | ≤ 4 min |
| **S3 — Permisos y aislamiento** | `@permisos` | Matriz RBAC en navegador; RLS entre observers; 401/403; aislamiento por `registradaPorId` | **Sí** | ≤ 3 min |
| **S4 — Rebanada** | `@rebanada` | Dashboard, reportes, invitaciones, legal, design system, responsive/a11y | No (nightly) | ≤ 4 min |

Criterio de corte: **un fallo en S1–S3 significa defecto electoral; un fallo en S4 significa defecto
de producto**. Eso es lo que define si un check bloquea el merge.

```ts
// frontend/app/playwright.config.ts
export default defineConfig({
  testDir: './e2e',
  retries: 0,                                   // ver §10.5
  timeout: 45_000,
  projects: [
    { name: 'S1-guardian',  grep: /@guardian/  },
    { name: 'S2-ciclo',     grep: /@ciclo/     },
    { name: 'S3-permisos', grep: /@permisos/  },
    { name: 'S4-rebanada',  grep: /@rebanada/  },
  ],
});
```

```ts
// e2e/e2e-01-registro.spec.ts
test('transmite el acta con la suma de votos cuadrada @guardian', async ({ page }) => { … });
```

```yaml
# .github/workflows/e2e.yml
strategy:
  fail-fast: false        # un shard rojo no debe cancelar los otros
  matrix:
    shard: [S1-guardian, S2-ciclo, S3-permisos]
```

### 10.3 El verdadero costo no son los tests: es el stack

Un E2E de navegador contra API real necesita Postgres + RustFS + imagen del backend + build del
frontend + navegador. **Setup ≈ 4–6 min por job**, y con 3 shards en paralelo se paga 3 veces.

Tres mitigaciones, en orden de impacto:

1. **Reutilizar el `Dockerfile` de frontend que ya existe.** `frontend/app/Dockerfile` compila
   Angular → nginx, y `nginx.conf` ya hace `proxy_pass http://electoral-api:80/api/`.
   Es decir: **CI no necesita `ng serve` en absoluto** — puede correr el frontend *production-like*
   detrás del mismo nginx que usan los usuarios. Más rápido **y** más fiel: es la forma natural de
   cerrar H3 y H5, porque el navegador habla con la misma ruta `/api` que producción.
2. **Pre-construir las imágenes en GHCR.** Backend y frontend se construyen una vez y se empujan;
   cada job hace `docker pull` (~60–90 s) en vez de `docker build` (~4 min).
3. **Base de datos por shard.** La suite de contrato reserva pares (JRV, cargo) con `comboLibre()`.
   Si el contrato y el E2E corren contra la misma base en paralelo, se pisan el índice único
   `uk_acta_activa` y aparecen 409 falsos → *flaky* aleatorio. Cada shard necesita su propia base
   (o su propio `compose.ci.yaml` con volumen propio).

### 10.4 Qué queda required y qué no

El ruleset de `develop` ya exige 6 checks. Agregar 4 más required hace que **todo** PR espere más y
que un flake cosmético bloquee el merge.

| Grupo | Checks | ¿Required? | Wall clock |
|---|---|:--:|---|
| Backend | Format, Tests & Coverage, Contract Tests (ya verde) | Sí (ya) | ~3 min |
| Frontend | ESLint, Format, Unit, Build | Sí (ya) | ~4 min |
| **E2E S1 + S2 + S3** | 3 checks nuevos | **Sí** | ~6–8 min (paralelo) |
| **E2E S4** | 1 check | **No** → `schedule` (nightly) + `workflow_dispatch` + push a `main` | fuera del camino crítico |
| Load test | k6 baseline | No (ya) | nightly |

Se **recomienda 3 required, no 4**: S1–S3 protegen los invariantes declarados del proyecto
(integridad electoral, RBAC, RLS). S4 protege UX y puede esperar al nightly.

### 10.5 Decisiones de robustez (importantes)

- **`retries: 0` en S1 y S2.** Un reintento de Playwright repite el registro del acta: el test de
  idempotencia, al reintentarse, ejecuta un segundo envío real. Con `retries: 2` (el default en
  CI) un flake se convierte en un acta duplicada en la base de prueba y, peor, enmascara el
  defecto que se quería detectar. Los *flakes* se gestionan con cuarentena explícita, no con
  reintentos silenciosos.
- **`timeout-minutes` por job** (p. ej. 15) + `maxFailures: 1`: un shard que se cuelga debe
  fallar rápido, no agotar los minutos del runner.
- **`timeout: 45_000` por test** y `expect.timeout: 10_000` (ya está en el config raíz).
- **`fail-fast: false`** en la matrix: que S4 caiga no debe cancelar S1.
- **Artefactos siempre**: `trace: 'on-first-retry'`, `video`, `screenshot` — y subirlos aunque el
  job pase, porque el siguiente que investigue va a necesitar el contexto.

### 10.6 Lo que la suite de contrato ya cubre (y por qué el E2E no debe duplicarlo)

La suite de contrato (122 tests, 57 s, **verde**) ya afirma en HTTP puro: la máquina de estados
completa, la matriz rol × endpoint, el 409 por par duplicado, la carrera 201/409 real, el descuadre
de votos → 400 y el shape de errores.

**El valor propio del E2E de navegador es la dimensión navegador**, que ningún otro nivel cubre:
render, enrutado, guards de rol en el cliente, interceptores de auth, IndexedDB y cola offline,
compresión de imagen a `.webp`, foco y accesibilidad.

De ahí se sigue el reparto: **S1–S3 deben probar lo que solo el navegador puede probar** (que el
guard de rol deja ver el botón, que la cola offline reenvía sin duplicar, que la foto comprimida
sube al object storage), y **no re-afirmar en el navegador lo que el contrato ya afirma**.

### 10.7 Orden de ejecución recomendado

| Paso | Acción | Esfuerzo |
|---|---|---|
| 1 | Fixture base: aceptar cookies + términos + login real contra la API (H6) | 1–2 h |
| 2 | Un solo config Playwright + borrar `e2e/playwright.config.ts` (H8) | 30 min |
| 3 | Reescribir `MisActasService` y la página contra el contrato real (H3) | 1 h |
| 4 | Crear la ruta de detalle o corregir la navegación (H4) | 30 min |
| 5 | Specs S1 reales: registro + idempotencia + checksum contra API real (H5) | 3–4 h |
| 6 | Script local `pnpm test:e2e:real` que levanta el stack completo | 1–2 h |
| 7 | **Recién entonces**: taggear por criticidad, 4 shards, workflow con matrix | 2 h |
| 8 | Imágenes en GHCR + `compose.ci.yaml` con base por shard | 2 h |
| 9 | Declarar S1–S3 required en el ruleset de `develop` | 30 min |

Los pasos 1–6 no tocan CI y dejan el E2E **verde y determinista en local**. Ese es el punto de
control: si el E2E es verde local contra el stack real, el shard entra a CI sin riesgo. Si no, el
CI se convierte en el lugar donde se descubre que el E2E está rojo, y eso es peor que no
tenerlo.


