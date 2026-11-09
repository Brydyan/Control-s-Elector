# Apply Progress — T-LEGAL-01: Política de Privacidad + Términos de Uso

**Implementador:** agente Mavis (modo SDD-apply / Implementation Routing)
**Change:** `openspec/changes/compliance/2026-11-06-t-legal-01-privacy-terms/`
**Rama:** `brydyan/sc-482/t-legal-01-privacy-policy-terms-of-use-sdd` (sin commitear, sin push)
**Fecha:** 2026-11-08

> Este archivo lo escribe el implementador, no el verificador. **No** decide si el trabajo está
> bien: eso lo hace otro, con contexto limpio. No hay `verify-report.md` ni `archive-report.md`
> acá, a propósito.

---

## A — Números medidos

### A.1 — Línea base (antes de tocar nada)

Medida en un `git worktree` del mismo commit (`c6c61e8`), no de memoria:

```
backend   mvn -B -o clean verify -Dmaven.test.failure.ignore=true
          11 suites / 61 tests / 0 failure / 0 error / 0 skip
  cobertura  INSTRUCTION covered=1657 missed=13143 ratio=0.1120
             LINE       covered=387  missed=2907  ratio=0.1175
             (213 clases en el reporte)

frontend  pnpm test:ci          69 files / 664 tests / 0 failure
          pnpm lint             All files pass linting
          pnpm format:check     All matched files use Prettier code style
          pnpm build            Application bundle generation complete
e2e       npx playwright test   44 passed (22 tests x 2 proyectos)

DOCKER: durante esta sesion el daemon de Docker se levanto (23:28). La linea base del
backend corrio COMPLETA (los IT de Testcontainersincluded): 61/61 en verde.
```

### A.2 — Resultado al terminar

```
backend   mvn -B -o clean verify        BUILD SUCCESS
          14 suites / 87 tests / 0 failure / 0 error / 0 skip
  (+3 suites, +26 tests)
    LegalFlowIT                 11   (nuevo)
    LegalPublicPagesIT           6   (nuevo)
    DpoRateLimiterTest           4   (nuevo)
    RlsIsolationIT               7   (+5: politicas del bloque legal)

  spotless:check      BUILD SUCCESS (violaciones: 0)
  jacoco:report       target/site/jacoco/ generado (230 clases)
  jacoco:check        BUILD SUCCESS (piso 0.08, real 0.1382)

  cobertura  INSTRUCTION covered=2225 missed=13874 ratio=0.1382 (13.82%)  [antes 0.1120]
             LINE       covered=506  missed=3104  ratio=0.1402 (14.02%)  [antes 0.1175]
             (+568 lineas cubiertas, +17 clases en el reporte)

frontend  pnpm test:ci          73 files / 689 tests / 0 failure   [+4 files, +25 tests]
          pnpm lint             All files pass linting
          pnpm format:check     All matched files use Prettier code style
          pnpm build            Application bundle generation complete (822.30 kB)
e2e       npx playwright test   50 passed (25 tests x 2 proyectos)  [+6]
```

### A.3 — Lo que NO se midió, y por qué

- **Cobertura por clase del bloque legal:** el reporte de JaCoCo atribuye **0%** a
  `LegalService` y `UserLegalResource`, que el `LegalFlowIT` ejercita de punta a punta (el test
  asserta las filas que escriben). La causa es que el usuario del datasource en los entornos de
  test es superusuario y se salta RLS, y el mecanismo de instrumentacion de JaCoCo no engancha esas
  dos clases en el classloader de Quarkus. **Es una limitacion de la herramienta, no un hueco de
  tests**: el mismo 0% aparece en clases preexistentes que solo se alcanzan por REST
  (`ActaService`, `ObserverResource`, `DashboardService`, `UsuarioResource`). El numero medido
  (13.82%) es un **piso**, no la cobertura real del bloque legal. Lo que si prueba que los tests
  no son decorativos son las mutaciones de la seccion C: matan tests concretos.
- **El E2E no corre en CI:** `ci.yml` no tiene job de Playwright. Los 50 tests E2E se corrieron
  local contra el dev server (`pnpm start` + mocks de `page.route`).

---

## B — Que se cerro, tarea por tarea

| Task | Cerrada | Donde quedo |
|---|---|---|
| T-LEGAL-01.1 DB | SI | `026-legal-compliance.sql` (5 changesets) + registro en `db.changelog-master.yaml` |
| T-LEGAL-01.2 LegalResource | SI | `legal/web/LegalResource.java` + `legal/web/LegalVersionResource.java` |
| T-LEGAL-01.3 UserLegalService | SI | `legal/application/LegalService.java` (+ `DpoRateLimiter.java`) |
| T-LEGAL-01.4 endpoints | SI | `legal/web/UserLegalResource.java`, `legal/web/DpoResource.java`, `legal/web/LegalExceptionMapper.java` |
| T-LEGAL-01.5 privacy ES/EN | SI | `src/main/resources/legal/privacy-{es,en}.html` |
| T-LEGAL-01.6 terms ES/EN | SI | `src/main/resources/legal/terms-{es,en}.html` |
| T-LEGAL-01.7 TermsAcceptanceModal | SI | `features/legal/components/terms-acceptance-modal/` + `pages/terms-acceptance/` |
| T-LEGAL-01.8 AuthGuard | SI | `core/guards/auth.guard.ts` (`authGuard` + `sessionGuard`) |
| T-LEGAL-01.9 E2E Playwright | SI | `e2e/j6-legal-terms.spec.ts` (3 escenarios x 2 proyectos) |
| T-LEGAL-01.10 test export + rate limit | SI | `LegalFlowIT` (11 tests) + `DpoRateLimiterTest` (4 tests) |

**Ninguna task queda sin cerrar.** El contenido legal de los HTML queda **pendiente de revision y
firma por el responsable del tratamiento y el DPO**: los documentos lo dicen de forma explicita en
el pie, y la proposal lo exige antes de publicar.

---

## C — Mutaciones ejecutadas (13: 12 mataron tests, 1 sobrevive y se declara)

Cada mutacion se aplico sobre el codigo limpio, se corrio el test afectado y se restauro el
codigo. Nombre exacto del test que cayo:

| # | Mutacion | Test(s) que cayo |
|---|---|---|
| M1 | `LegalService.exigirVentanaExport` deja de lanzar el 429 | `LegalFlowIT.laSegundaExportacionDentroDe24hResponde429`<br>`LegalFlowIT.elLimiteDeExportacionEsPorUsuario` |
| M2 | `usuario.setAcceptedTermsVersion(...)` deja de ejecutarse | `LegalFlowIT.aceptarTerminosRegistraLaVersionEnElUsuarioYEnLaAuditoria`<br>`LegalFlowIT.laSegundaAceptacionSeRegistraComoReaceptacion` |
| M3 | `LegalResource.normalizarIdioma` deja de validar contra la lista cerrada | `LegalPublicPagesIT.unIdiomaFueraDeLaListaCaeAlPorDefecto` |
| M4 | el HTML deja de sustituir `{{VERSION}}` por la version vigente | `LegalPublicPagesIT.elDocumentoMuestraLaVersionVigente` |
| M5 | `DpoRateLimiter.registrar` deja de rechazar el exceso | `DpoRateLimiterTest.laSegundaSolicitudDentroDeLaVentanaSeRechaza`<br>`DpoRateLimiterTest.sinIpLaCuentaCaenEnUnaClaveCompartida`<br>`LegalFlowIT.laSegundaSolicitudDeLaMismaIpResponde429` |
| M6 | `LegalService` deja de activar el contexto de RLS | **SOBREVIVE** (declarado, ver abajo) |
| M8 | `JwtAuthFilter.PUBLICAS` deja de incluir `/privacy` y `/terms` | `LegalPublicPagesIT.privacidadEsPublica`<br>`LegalPublicPagesIT.terminosEsPublico`<br>`LegalPublicPagesIT.elParametroLangManda`<br>`LegalPublicPagesIT.unIdiomaFueraDeLaListaCaeAlPorDefecto`<br>`LegalPublicPagesIT.elDocumentoMuestraLaVersionVigente` |
| M9 | `TermsAcceptanceService.pendienteDeAceptar` siempre devuelve `false` | `terms-acceptance.service.spec.ts > un usuario que nunca acepto tiene pendiente la aceptacion`<br>`terms-acceptance.service.spec.ts > una version nueva vuelve a pedir la aceptacion` |
| M10 | el modal deja de emitir `accepted` | `terms-acceptance-modal.component.spec.ts > al aceptar se avisa al padre y se apaga el estado de envio` |
| M11 | el formulario del DPO deja de validar | `contact-dpo.page.spec.ts > un formulario vacio no se manda y muestra los errores`<br>`contact-dpo.page.spec.ts > un email invalido no se manda` |
| M12 | `describirErrorLegal` deja de leer el `Retry-After` | `legal.service.spec.ts > un 429 con Retry-After dice cuanto falta para reintentar` |
| M13 | al aceptar, el store no se actualiza | `terms-acceptance.service.spec.ts > aceptar guarda la version en el store y en la sesion` |
| M14 | el guard no redirige a `/terminos` | E2E `j6-legal-terms.spec.ts > un usuario sin aceptar ve el modal y al aceptar entra al sistema`<br>E2E `j6-legal-terms.spec.ts > cerrar el modal sin aceptar cierra la sesion y vuelve al login` |

### C.1 — M6 sobrevive, y por que se declara (no se oculta)

La mutacion que quita `rls.activar(usuario.getId())` **no tumba ningun test HTTP**. Motivo
medido: el usuario del datasource en los entornos de test es superusuario (`\du` ->
`electoral_user | Superusuario ... | Ignora RLS`), asi que Postgres se salta las politicas y el
INSERT de la auditoria pasa igual.

Intente cerrar el hueco haciendo que la app conecte como un rol `NOSUPERUSER NOBYPASSRLS`
(`PostgresLegalTestResource` lo intento): **falla**, y el motivo es instructive — los seeds de
referencia (`025-data-seed.sql`) insertan en `acta` y en `notificacion`, que tienen RLS forzada, y
un rol sin privilegios no puede sembrarlas. Se midio el error exacto:
`new row violates row-level security policy for table "acta"`.

Lo que si se dejo probando es la **capa de politicas**, en `RlsIsolationIT` (que si usa un rol
`NOBYPASSRLS` de verdad):

- `sinContextoDeRlsLaEscrituraSeRechaza` — sin `app.current_user_id` el INSERT en
  `terms_acceptance_audit` se rechaza con SQLState **42501**. Esa es la prueba de que
  `RlsContexto.activar(...)` no es decorativo: sin el, el alta de aceptacion revienta en
  produccion (donde el usuario de la app no es superusuario).
- `unUsuarioNoPuedeEscribirEnNombreDeOtro` — escribir una aceptacion a nombre de otro: 42501.
- `cadaUsuarioSoloVeSuPropiaAuditoriaDeAceptacion` — cada usuario ve solo la suya; el MASTER ve
  todas.
- `elLogDeExportacionesEsDelPropioUsuario` — idem para `user_export_log`.
- `laSolicitudAlDpoSeEscribeSinSesionPeroSoloLaLeeElMaster` — el formulario del DPO acepta la
  escritura sin sesion (politica `WITH CHECK (true)`) y la lectura queda para el MASTER.

---

## D — Desviaciones del contrato (tasks.md / specs) y por que

El `tasks.md` y los specs estan escritos contra el **backend Spring** (Flyway, `@Authenticated`,
`SecurityContext`, paquete `ec.gob.cne.electoralsystem.api`, anotacion `@RateLimit` que no existe
en este repo). Se implemento **adaptando el contrato al stack real**, no al reves. Cada
desviacion y su motivo:

| Donde | Devolucion | Motivo |
|---|---|---|
| 01.1 ruta del changelog | `database/changelog/026-legal-compliance.sql` en vez de `db/changelog/V2026.11.01__...sql` | Liquibase es el unico dueno del esquema (AGENTS.md). Numero de changelog, no `V…` de Flyway. |
| 01.1 columnas del DPO | `name`, `email`, `subject`, `message` | El spec (dpo-contact, escenario 1) fija ese formato en el cable y el SQL del task usa esos nombres. |
| 01.1 tabla del DPO | sin FK a `usuario` | El formulario es publico: lo puede usar alguien que nunca entro. |
| 01.2 `current-version` | clase propia (`LegalVersionResource`) | JAX-RS resuelve por camino raiz mas largo: `DpoResource` esta en `/api/v1/legal` y se llevaba el request del metodo de `LegalResource` (404 medido). |
| 01.2 respuesta de version | `{version, terms_version, privacy_version}` | El task pide `{"version":"1.0"}`; se manda esa **y** las dos especificas, por si terminos y politica se versionan por separado. |
| 01.3/01.4 `@RateLimit` | limitadores propios (`DpoRateLimiter` en memoria; export durable en `user_export_log`) | La anotacion no existe. El export tiene que sobrevivir a un reinicio, asi que no puede ser un contador en memoria. |
| 01.3 version aceptada | la fija el **servidor** (`legal.terms-version`), nunca el cliente | Si el cliente eligiera la version, podria aceptar "1.0" con el "1.1" ya publicado. |
| 01.4 `accept-terms` | `POST` sin cuerpo | El cuerpo no lleva datos: la version es del servidor. |
| 01.5/01.6 ubicacion del HTML | `backend/.../src/main/resources/legal/` en vez de `frontend/app/src/assets/legal/` | El design (D2) ofrece las dos; los sirve el backend, asi que el nginx enruta `/privacy` y `/terms` hacia el y el documento queda en git, revisado y firmado en un PR. |
| 01.5 version en el documento | el servidor inyecta `{{VERSION}}` | Si el HTML dijera "1.0" hardcodeado y el servidor aceptara "1.1", el usuario aceptaria un documento que dice ser otro. El `LegalPublicPagesIT` lo asserta. |
| 01.7 pipe `translate` | no existe | El proyecto no tiene i18n. Los textos van en espanol, con version en ingles de los documentos legales. |
| 01.7 ruta del modal | `/terminos` (pantalla que hospeda el dialogo) | El spec decia "navegar a `/terms-acceptance-modal`", pero un modal no es una ruta: la ruta hospeda el modal y es la que el guard redirige. |
| 01.8 comparacion de version | por **igualdad**, no por "mayor" | No hay orden confiable de versiones (1.10 > 1.9 no es cierto en decimal plano). |
| 01.9 E2E | `e2e/j6-legal-terms.spec.ts`, 3 escenarios | El nombre del archivo del task (`j-legal-01-terms-acceptance.spec.ts`) no segue la convencion del repo (J1..J5). |
| spec: formulario del DPO | pagina Angular `/contact-dpo` (no HTML estatico) | Una sola implementacion del formulario: la de la SPA se puede probar con unit tests y E2E; duplicarla en HTML seria drift. El backend expone el `POST /api/v1/legal/contact-dpo`. |
| spec: forma del error | `{timestamp, status, error, message}` | Es el contrato del proyecto (`ApiErrorResponse`), que la suite de regresion afirma. El spec pedia el texto dentro de `error`; aca va en `message` y `error` queda el reason phrase. |
| spec: mensaje del DPO | `Mensaje recibido. DPO responderá en 5 días hábiles.` | Literal del spec. |

---

## E — Hallazgos y arreglos que no estaban previstos

1. **Faltaba el contexto de RLS en el flujo legal (defecto real, no cosmético).** Las tres tablas
   nuevas tienen RLS `FORCE`; sin `set_config('app.current_user_id', …, true)` el INSERT de la
   aceptacion revienta y el `SELECT` del log de exportaciones no ve nada — o sea, **el limite de 24
   horas no limitaba**. Corregido con `RlsContexto.activar(...)` en `LegalService` (el mismo
   mecanismo que usa `ActaService`).
2. **`GET /api/v1/legal/current-version` devolvia 404** por el conflicto de caminos raiz de JAX-RS
   con `DpoResource`. Medido con el IT, corregido con una clase propia.
3. **El proxy de dev se tragaba el chunk lazy de la ruta nueva.** Con la regla `'/terms'` (prefijo)
   en `proxy.conf.json` y `vite.config.ts`, el request de `terms-acceptance.page-<hash>.js` se
   mandaba al backend y devolvia **502**: la pantalla de aceptacion no cargaba. Corregido con
   glob (`/terms{,/*}`) y regex anclada (`^/terms(\?.*)?$`).
4. **`app-button` no tenia input `label`:** tres botones del app (subir foto de contingencia,
   descargar expediente, desbloquear PIN) se renderizaban **sin texto**. Se agrego el input y se
   paso `[label]` a `p-button`. Efecto colateral del modal de aceptacion, que lo usa.
5. **El `X-Forwarded-For` es falsificable** (ya documentado en ODDs previas): el limitador del DPO
   lo usa tal cual, como el `ActivationRateLimiter`. No se cambio: es la convencion del proyecto y
   el arreglo (proxy confiable) es de infra, no de este change.

---

## F — Lo que queda pendiente (fuera del alcance del implementador)

1. **Revision y firma del contenido legal** por el responsable del tratamiento y el DPO. Los
   documentos lo dicen en el pie. Define tambien: identidad del responsable, identidad y contacto
   del DPO, y los plazos de retencion definitivos (hoy: actas ~7 anos por obligacion electoral,
   sesiones 90 dias, auditoria mientras el acta que ampara el consentimiento).
2. **Los endpoints de exportacion no tienen boton en la UI.** El backend y el endpoint existen y
   estan probados; el spec solo pedia el endpoint y el link en la politica. Un boton "Descargar
   mis datos" seria alcance nuevo de producto: requiere decision explicita.
3. **Job de Playwright en CI.** El E2E corre local pero `ci.yml` no lo ejecuta.
4. **`/contact-dpo` en produccion** lo sirve la SPA (nginx: fallback de Angular). El backend ya no
   expone esa ruta: se elimino el HTML duplicado.

---

## G — Archivos tocados

**Backend (nuevos)**
```
src/main/java/ec/twintailcs/electoral/legal/domain/{TermsAcceptanceAudit,DpoContactRequest,UserExportLog,LegalException}.java
src/main/java/ec/twintailcs/electoral/legal/application/{LegalService,DpoRateLimiter}.java
src/main/java/ec/twintailcs/electoral/legal/web/{LegalResource,LegalVersionResource,UserLegalResource,DpoResource,LegalExceptionMapper}.java
src/main/resources/legal/{privacy-es,privacy-en,terms-es,terms-en}.html
src/main/resources/database/changelog/026-legal-compliance.sql
src/test/java/ec/twintailcs/electoral/legal/application/DpoRateLimiterTest.java
src/test/java/ec/twintailcs/electoral/legal/web/{LegalPublicPagesIT,LegalFlowIT,LegalPublicTestProfile,LegalFlowTestProfile}.java
src/test/java/ec/twintailcs/electoral/legal/support/PostgresLegalTestResource.java
```

**Backend (modificados)**
```
auth/domain/Usuario.java                      (+4 columnas de aceptacion + aceptoTerminos)
auth/security/JwtAuthFilter.java              (+5 rutas publicas)
auth/web/dto/AuthResponse.java                 (+acceptedTermsVersion)
auth/web/dto/mapper/UsuarioMapper.java
acta/domain/Acta.java                         (+getPublicId)
resources/application.properties               (legal.terms-version, recursos nativos)
resources/database/db.changelog-master.yaml
test/.../shared/security/RlsIsolationIT.java  (+5 casos de RLS del bloque legal)
```

**Frontend (nuevos)**
```
src/app/features/legal/data/{legal.dto,legal.api}.ts
src/app/features/legal/services/{legal.service,terms-acceptance.service}.ts
src/app/features/legal/components/terms-acceptance-modal/terms-acceptance-modal.component.ts
src/app/features/legal/pages/terms-acceptance/terms-acceptance.page.ts
src/app/features/legal/pages/contact-dpo/contact-dpo.page.{ts,html,scss}
e2e/j6-legal-terms.spec.ts
e2e/support/legal-mocks.ts
```

**Frontend (modificados)**
```
src/app/core/guards/auth.guard.ts              (authGuard + sessionGuard con el gate de terminos)
src/app/app.routes.ts                          (rutas /terminos y /contact-dpo)
src/app/features/auth/{domain/models/user.model,data/auth.dto,data/auth.api}.ts
src/app/features/auth/services/auth.store.ts  (setAcceptedTermsVersion)
src/app/features/auth/pages/login/login.page.{html,scss}   (aviso legal)
src/app/shared/ui/button/button.component.{ts,html}       (input label)
nginx.conf, proxy.conf.json, vite.config.ts   (enrutado de los documentos legales)
```
