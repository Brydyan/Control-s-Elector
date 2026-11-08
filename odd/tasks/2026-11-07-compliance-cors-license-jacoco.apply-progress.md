# Apply Progress — ODD: Compliance CORS + Licencias + JaCoCo (PWA)

**Implementador:** agente Mavis (modo ODD / Implementation Routing)
**Change:** `odd/tasks/2026-11-07-compliance-cors-license-jacoco.md`
**Rama:** `brydyan/sc-478/odd-cors-license-thirdparty-jacoco` (sin commitear, sin push)
**Fecha:** 2026-11-07

> Este archivo lo escribe el implementador, no el verificador. **No** decide si el trabajo está
> bien: eso lo hace otro, con contexto limpio. No hay `verify-report.md` ni `archive-report.md`
> acá, a propósito.

---

## A — Números medidos

### A.1 — Línea base (antes de tocar nada)

```
backend test   mvn -B -o test                                    exit 1
               10 suites / 47 tests / 0 failure / 2 error / 0 skip

  InvitationTokenServiceTest            10
  PasswordValidationServiceTest          9
  InvitacionesMigracionIT                1   ERROR (Testcontainers)
  EmailTemplatesTest                     7
  SmtpEmailProviderTest                  3
  InvitationEmailQueueImplTest           7
  ActivationRateLimiterTest              3
  UsuarioMapperTest                      4
  UuidVersion7StrategyTest               2
  RlsIsolationIT                         1   ERROR (Testcontainers)

  Los 2 errores: "Could not find a valid Docker environment".
  NO es un defecto de codigo: no hay daemon de Docker en esta maquina
  (/var/run/docker.sock no existe). En CI hay Docker y ambos corren.
```

La cobertura **no se media** en la linea base porque el modulo no instrumentaba cobertura
(el `pom.xml` no tenia JaCoCo). Se midio despues de agregar el plugin, sobre el codigo sin
tocar: 325/3294 lineas = **0.0986**.

> El `02-builder.md` afirma que "la suite de backend esta en rojo (78 errores)". Hoy es
> **2 errores**, ambos de Testcontainers. Esa afirmacion es obsoleta (preexiste del corte a
> Quarkus) y queda anotada en F.9.

### A.2 — Resultado al terminar

```
backend   mvn -B -o clean verify -Dmaven.test.failure.ignore=true   BUILD SUCCESS
          11 suites / 54 tests / 0 failure / 2 error / 0 skip
          (+1 suite, +7 tests: CorsPreflightIT)

  spotless:check      BUILD SUCCESS (violaciones: 0)
  jacoco:report       target/site/jacoco/{index.html,jacoco.xml} generado
  jacoco:check        BUILD SUCCESS (piso 0.08, real 0.1175)

  cobertura  LINE covered=387 missed=2907 total=3294 ratio=0.1175 (11.75%)
             214 clases en el reporte
             (la linea base era 325/3294 = 0.0986; +62 lineas por el arranque
              de la app que hace el @QuarkusTest de CORS)

frontend  pnpm test:ci        68 suites / 651 tests / 0 fallos   exit 0
          (linea base igual: 68/651/0 — medido con `git stash` de los 4 archivos
           de frontend tocados; el cambio es de build, no de codigo cubierto)
frontend  pnpm lint           0 errores                          exit 0
frontend  pnpm format:check   0 errores                          exit 0
frontend  pnpm build          exit 0 (818.87 kB initial)
          www/manifest.webmanifest presente   www/ngsw.json presente
```

---

## B — Tareas cerradas

| Task | Estado | Dónde |
|---|---|---|
| 1. CORS | **cerrada con desvío** (ver E.1–E.4) | `backend/.../src/main/resources/application.properties`, `.env.example`, `k8s/configmap.yaml` |
| 1b. Test de CORS | cerrada (nueva) | `backend/.../src/test/java/ec/twintailcs/electoral/shared/config/CorsPreflightIT.java` + `CorsTestProfile.java` |
| 2. LICENSE | **cerrada con desvío** (ver E.9) | `LICENSE` (raiz) |
| 3. THIRD_PARTY_LICENSES | **cerrada con desvío** (ver E.10) | `THIRD_PARTY_LICENSES.md` (raiz) |
| 4. JaCoCo | **cerrada con desvío** (ver E.5–E.6) | `backend/.../pom.xml` |
| 4b. Cobertura en CI | cerrada (nueva) | `.github/workflows/ci.yml` |
| 5. PWA manifest + SW | **cerrada con desvío** (ver E.11–E.12, F.1–F.4) | `frontend/app/angular.json`, `src/manifest.webmanifest`, `src/index.html`, `ngsw-config.json`, `src/assets/icon/*` (nuevos), `scripts/generate-pwa-icons.py` (nuevo) |

---

## C — Mutaciones ejecutadas

Protocolo: `cp` a `.bak` antes de mutar, `cp` de vuelta para restaurar, `diff` contra el `.bak`
al final para probar que quedo todo como estaba. **Nunca** `git checkout --`.

Oraculo: `CorsPreflightIT` (7 tests,levantan Quarkus de verdad y le preguntan al filtro CORS de
Vert.x).

| # | Mutacion | Test(s) que cayeron |
|---|---|---|
| M1 | `quarkus.http.cors.enabled=false` | `preflightDeOrigenPermitido`<br>`peticionRealDeOrigenPermitido`<br>`peticionRealProtegidaSinToken`<br>`listaDeOrigenes`<br>`losHeadersPermitidosNoAbrenElSpoofingDeIp` — **5 de 7** |
| M2 | Sacar `http://localhost:4300` de la lista de origenes | `listaDeOrigenes` |
| M3 | `access-control-allow-credentials=false` | `preflightDeOrigenPermitido` |
| M4 | Quitar `PATCH` de `quarkus.http.cors.methods` | `preflightDeOrigenPermitido` |
| M5 | Agregar `X-Forwarded-For` a los headers permitidos | `losHeadersPermitidosNoAbrenElSpoofingDeIp` |
| M6 | `quarkus.http.cors.origins=*` (comodin) | `preflightDeOrigenNoPermitido`<br>`peticionRealDeOrigenNoPermitido`<br>`listaDeOrigenes` — **3 de 7** |

**M3 no caia en la primera vuelta.** El perfil de test (`CorsTestProfile`) sobrescribia
`quarkus.http.cors.access-control-allow-credentials=true`, de modo que la mutacion de esa
propiedad en `application.properties` no producia ninguna diferencia observable: el perfil la
tapaba. Se **arreglo el test**, no el hallazgo: el perfil ya no toca ninguna propiedad de CORS
(asi el test verifica la configuracion real). Repetida, M3 cae en `preflightDeOrigenPermitido`.

**M6 es la que mas importa.** Con `*` y `access-control-allow-credentials=true`, Vert.x devuelve
el origen del requestor en vez de rechazarlo: la lista blanca deja de existir y **cualquier
pagina** puede llamar a la API con credenciales. Por eso el `*` esta prohibido por comentario en
la configuracion, y no solo por buena praxis.

Restauracion verificada: `diff` contra el `.bak` sin diferencias; suite en verde (7/7).

---

## D — Sin cerrar

Nada de esto se omitio: se declara.

### D.1 — Los 2 tests de Testcontainers no corren en esta maquina

`InvitacionesMigracionIT` y `RlsIsolationIT` necesitan un Postgres real via Testcontainers y no
hay daemon de Docker (`/var/run/docker.sock` no existe). Erran al arrancar el contenedor, no
por el codigo bajo prueba. En CI hay Docker y ambos corren: el job `Backend Tests & Coverage`
usa `ubuntu-latest`, donde el daemon existe.

**Consecuencia honesta:** el aislamiento de RLS y la migracion de invitaciones **no fueron
verificados por esta sesion**. Lo que si se verifico es que el gate de cobertura corre y que el
resto de la suite esta verde.

### D.2 — Subir el piso de cobertura

El piso quedo en 0.08 con un ratio real de 0.1175. El objetivo de 80% del `02-builder.md` no se
persigue con el plugin: se persigue con la suite de tests. La cobertura de 11.75% refleja que
el nucleo (`acta/`, `evidencia/`, `electoral/`) tiene **cero** tests unitarios. Subir el piso sin
subir la suite es falsear el tablero.

### D.3 — Codecov

El task original propose subir el reporte a Codecov. **No se agrego**: `codecov/codecov-action`
exige un token y una cuenta, y el proyecto no tiene ninguna; un token ausente rompe el job por
una razon que no es del codigo. En su lugar el reporte se publica como **artifact** del workflow
(`actions/upload-artifact@v4`), que da la misma visibilidad sin servicio externo ni credencial.
Si se quiere Codecov, es una decision de producto: crear la cuenta, guardar el token en el
Secret del repo y agregar el paso.

### D.4 — Pagina de licencias en el frontend

`openspec/changes/compliance/2026-11-05-t-compliance-03-licenses-ip` (T-COMPLIANCE-03) pide
una pantalla `/licenses` con un `licenses.json` generado. **No se hizo**: es otro change, con
sus propias mutaciones y sus tests. Lo que si se hizo es dejarle el dato listo: el comando que
genera el inventario (`pnpm licenses list --json`) esta documentado en
`THIRD_PARTY_LICENSES.md`, y el build de Angular ya emite `www/3rdpartylicenses.txt` con el
texto de licencia de cada paquete del bundle.

### D.5 — Security headers (CSP, HSTS, X-Frame-Options)

No implementados. Son la tarea T-INFRA-01.3 del SDD `2026-11-06-t-infrastructure-01-cors-security-headers`,
que es un change aparte. Este ODD no lo tocaba.

---

## E — Desvíos del task original (todos deliberados)

### E.1 — La lista de origenes sale de una variable de entorno, no esta fija en el archivo

El task pedia escribir los origenes a pelo en `application.properties`. Se hizo
`quarkus.http.cors.origins=${CORS_ALLOWED_ORIGINS:...}`.

Razones:

1. `k8s/configmap.yaml` **ya declaraba** `CORS_ALLOWED_ORIGINS` y el backend no lo leia. La
   variable existia; lo que faltaba era el cable. Ahora el cable existe y el ConfigMap manda.
2. Un origen fijo en el codigo obliga a un redeploy por cada entorno nuevo.
3. El default es solo de desarrollo (localhost). Si el ConfigMap falta, la API responde **sin**
   cabeceras CORS y el navegador bloquea: fallo visible, no silencioso.

### E.2 — `quarkus.http.cors.enabled`, no `quarkus.http.cors=true`

La propiedad que pedia el task **no existe**. `quarkus.http.cors` a secas no la lee nadie y
Quarkus no avisa: CORS queda apagado y el sintoma es "el navegador bloquea". Verificado contra
la interfaz `io.quarkus.vertx.http.runtime.cors.CORSConfig` del jar `quarkus-vertx-http-3.40.1`:

```
enabled() / origins() / methods() / headers() / exposedHeaders()
accessControlMaxAge() / accessControlAllowCredentials() / returnExactOrigins() / varyOrigin()
```

`enabled()` tiene `@WithDefault("false")`: **CORS esta apagado por defecto en Quarkus**.

### E.3 — `quarkus.http.cors.exposed-headers`, no `access-control-expose-headers`

La propiedad del task tampoco existe (el accessor es `exposedHeaders()`). Y ademas **no se
declara ninguna**: `Content-Length` y `Content-Type` ya entran por la safelist de CORS
(`Cache-Control`, `Content-Language`, `Content-Length`, `Content-Type`, `Expires`,
`Last-Modified`, `Pragma`), y el backend no devuelve ningun header de paginacion — se verifico
con grep: `X-Total-Count` y `Location` **no aparecen en ninguna parte del codigo**. Declarar
headers que nadie lee es superficie sin uso.

### E.4 — `X-Forwarded-For` no va en los headers permitidos

El task lo pedia. Es un header de hop-by-hop que el navegador **prohibe** setear desde JS:
permitirlo no le sirve a ningun cliente legitimo y abre la puerta a que un cliente origine ese
valor. Quarkus lo maneja aparte, con `quarkus.http.proxy.*`. M5 verifica que el test lo detecta.

### E.5 — JaCoCo 0.8.13, no 0.8.10

El modulo compila con `maven.compiler.release=25`. **JaCoCo 0.8.10 no instrumenta class files de
Java 25** (falla con `Unsupported class file major version 69`). 0.8.13 es la primera linea que
soporta Java 25, y ya estaba en `~/.m2`. Con 0.8.10 el build habria muerto en `prepare-agent`.

### E.6 — `report` y `check` en `verify`, no en `test`; regla `BUNDLE`, no `PACKAGE`

- **Fase:** el dato lo produce surefire en `test`. Atar el reporte a la misma fase deja el orden
  contra el lifecycle default de Maven. `verify` es ademas la fase que corre CI
  (`./mvnw -B clean verify`), asi que el reporte existe sin tocar el workflow.
- **Regla:** `PACKAGE` hace fallar el build por un unico paquete sin tests. `BUNDLE` con piso
  0.08 (real 0.1175) es un **piso de no-regresion**: frena una regresion real (perder los tests
  de auth lo baja a ~0.04) y deja margen de ~120 lineas para crecer sin romper el build. El
  objetivo de 80% se persigue con la suite, no con el gate.

### E.7 — El LICENSE es el Apache 2.0 oficial, sin tocar

El task traia una plantilla **truncada** ("[Full Apache 2.0 text - use standard version]") con un
"Licensor" inventado (CNE) reescribiendo la definicion oficial. Modificar el texto de una
licencia la deja sin valor legal. Se puso el texto oficial **verbatim**, descargado de
`apache.org`, con el apendice (la unica parte que se rellena) y el titular real:

```
$ diff <(curl -s https://www.apache.org/licenses/LICENSE-2.0.txt) LICENSE
190c190
<    Copyright [yyyy] [name of copyright owner]
---
>    Copyright 2026 Brydyan / twintailcs
```

Una sola linea de diferencia: la del titular. Titular decidido por el humano (Brydyan /
twintailcs, el owner del repo publico).

### E.8 — THIRD_PARTY_LICENSES generado del arbol real

El task traia una lista escrita a mano, con **licencias incorrectas**. Se genero desde el arbol
real (243 artefactos del backend, 966 paquetes del frontend) y se documentaron las correcciones
en la propia tabla. Las mas graves:

| El task decia | Realidad (medida) |
|---|---|
| PostgreSQL JDBC — LGPL | **BSD-2-Clause** |
| Nimbus JOSE JWT — MIT | **Apache-2.0** |
| JUnit 5 — MIT | **EPL-2.0** |
| AssertJ — MIT | **Apache-2.0** |
| Tailwind — Apache 2.0 | **MIT** |
| PrimeNG — Apache 2.0 | **MIT** |
| Angular Material | **No existe en el proyecto** (es PrimeNG + Tailwind) |
| — | **OpenPDF 2.2.5 (LGPL-2.1/MPL-2.0)**: la dependencia copyleft del backend, sin listar |

### E.9 — El manifest esperado por el task no era el del repo, y su schema de Angular estaba vencido

El task esperaba `short_name: "Control"`, `start_url: "/"`, `theme_color: "#1976d2"`. El repo
tiene `"Control Electoral"`, `"/dashboard"` y `"#2563eb"`: el manifest **real** esta mejor
(documenta `lang: es-EC`, descripcion, categories, icono maskable). No se toco.

El task tambien pedia `"serviceWorker": true` + `"ngswConfigPath": "ngsw-config.json"`: ese es
el schema de **Angular 16 y anteriores**. El repo usa Angular 21, donde el schema correcto es
`"serviceWorker": "ngsw-config.json"` — que es lo que ya estaba. Cambiarlo habria roto el
build.

### E.10 — Los iconos se generaron; el favicon de 64x64 no daba la talla

El manifest declaraba un PNG de **64x64** como `192x192` **y** como `512x512`, con el mismo
archivo y `purpose: "any maskable"`. Un archivo no puede ser dos tamanos, y 64px no cumple el
minimo de instalabilidad. Se generaron los tamanos reales con `scripts/generate-pwa-icons.py`,
que redibuja la marca con la geometria **medida** sobre el PNG original (anillo, disco interior
y punto satelite, con el corte de ~36 grados del anillo). Verificado: el favicon redibujado
difiere del original en 12% de los pixeles, y esa diferencia es **solo antialiasing** (el canal
azul difiere en 0.0).

---

## F — Hallazgos (fuera de scope o no tocados)

### F.1 — El `manifest.webmanifest` **nunca llegaba al build** (arreglado, es la tarea 5)

`angular.json` define `assets` de forma explicita, y al hacerlo **reemplaza** el default del CLI
(que incluia `src/manifest.webmanifest`). El archivo se referenciaba desde `index.html` pero no
existia en `www/`:

```
$ ls www/manifest.webmanifest
ls: no se puede acceder a 'www/manifest.webmanifest': No existe el fichero o el directorio
```

En produccion era un **404**: la PWA no tenia manifest, sin nombre, sin iconos, sin theme color.
Agregado el asset al `angular.json`; verificado que `www/manifest.webmanifest` existe, que el
`<link rel="manifest">` **no** lleva hash, y que `ngsw.json` lo cachea en el grupo `app-shell`.

### F.2 — `apple-touch-icon` apuntaba al favicon de 64x64 (arreglado)

iOS exige 180x180 y **no** soporta transparencia (dibuja sobre negro). Se genero
`apple-touch-icon.png` de 180x180 opaco.

### F.3 — `ngsw-config.json` listaba `/favicon.ico`, que no existe (arreglado)

El favicon real esta en `/assets/icon/favicon.png` y ya lo cubre el grupo `assets` (`/assets/**`).
La entrada muerta se elimino.

### F.4 — El cliente movil (Capacitor) apunta a la URL equivocada — **NO arreglado**

`frontend/app/src/environments/environment.prod.ts` define `apiUrl: '/api/v1'` (relativa), igual
que el de desarrollo. Dentro del WebView de Capacitor una URL relativa resuelve contra el
servidor local de la app, **no** contra la API: el movil no alcanza el backend.

No se toco, por decision explicita del humano: es un cambio de producto (definir la URL de la
API por ambiente para el movil), no un fix de compliance, y `AGENTS.md` pide plantear los cambios
de producto antes de ampliar el release. **Queda abierto.** Requiere su propio change, con la
decision de si el movil consume la cookie httpOnly o el camino alternativo del refresh por body
(`RefreshRequest.refreshToken`), que ya existe para clientes sin cookies.

### F.5 — `X-Forwarded-For` se toma del header en 15 endpoints — **NO tocado**

`CoordinatorResource` y `ObserverResource` leen `@HeaderParam("X-Forwarded-For")` en 15 metodos.
El navegador no puede setearlo, pero cualquier cliente HTTP si: el valor es **falsificable**.
Impacto real: el rastro de auditoria de esas operaciones registra una IP que puede no ser la del
actor. Arreglarlo exige confiar solo en el proxy (nginx / ingress) y definir la lista de proxies
de confianza, que es una decision de infraestructura.

### F.6 — El `theme_color` del manifest no existe en el sistema de diseno — **NO tocado**

El manifest declara `#2563eb`. Los tokens del proyecto usan `--color-brand-primary:
var(--primitive-blue-500)` = **`#1c4a8e`** en modo claro. `#2563eb` no esta en ningun token. Es
un tema visual, no un compliance: se anota, no se cambia.

### F.7 — El SDD de CORS se solapa con este ODD, y una de sus tareas sobra

`openspec/changes/compliance/2026-11-06-t-infrastructure-01-cors-security-headers`:

- **T-INFRA-01.1** (config CORS por `application-{dev,prod,staging}.properties`) queda
  **cumplida en efecto, no en la letra**: la configuracion es por variable de entorno, que es lo
  que el ConfigMap de k8s ya usaba. Habria que actualizar los criterios de aceptacion del spec
  para que Verify no falle un ODD que hizo lo correcto.
- **T-INFRA-01.2** (eximir `OPTIONS` en `JwtAuthFilter`) **es innecesaria**: el filtro CORS de
  Vert.x atiende el preflight **antes** de JAX-RS, asi que nunca llega al `JwtAuthFilter`.
  Verificado: el preflight de `OPTIONS /api/v1/auth/login` responde 200 con las cabeceras CORS
  sin token. `JwtAuthFilter` es `@PreMatching` y no exime `OPTIONS` — da igual, porque el
  contenedor nunca lo ve para el preflight.
- **T-INFRA-01.4** (flags de la cookie) ya esta **implementado** en el codigo actual:
  `HttpOnly; Secure; SameSite=Strict`.
- Los paths del spec apuntan al paquete viejo `ec.gob.cne.electoralsystem.api`; el codigo real
  es `ec.twintailcs.electoral`.

No edite ese spec: es de otro change y lo decide el Arquitecto.

### F.8 — La cookie de refresh es `SameSite=Strict`: no cruza origen

`AuthResource.buildRefreshCookie` emite `HttpOnly; Secure; SameSite=Strict`. Con `Strict`, el
navegador **no** manda la cookie en peticiones cross-site. O sea: **CORS con credenciales no
resuelve el refresh de un origen distinto** — la cookie no viaja igual. Para el movil esta el
camino alternativo por body (`RefreshRequest.refreshToken`), que es lo que lo resuelve. Queda
anotado porque es la confusion mas comun al leer la configuracion de CORS.

### F.9 — La documentacion de los agentes esta desactualizada

`documents/agents/02-builder.md` dice que "la suite de backend esta en rojo (78 errores)". Hoy
son **2 errores**, y los dos son de Testcontainers sin Docker. La afirmacion es del periodo
anterior al corte a Quarkus. Se deja como esta: no es de este change.

### F.10 — Modificaciones ajenas a este ODD en el working tree

`documents/agents/01-arquitecto-qa.md`, `02-builder.md` y `03-verify.md` aparecen modificados
(las secciones de cierre ODD → Verify). **No los toco este change**: se modificaron durante la
sesion, por fuera de este trabajo. Se los deja como estan.

---

## G — Como se reproduce

```bash
# Backend: tests + gate de cobertura + formato (el IT de Testcontainers
# necesita Docker; sin daemon, 2 errores de entorno)
cd backend/electoral-api-quarkus
mvn -B -o clean verify -Dmaven.test.failure.ignore=true

# Solo el test de CORS (no necesita Docker ni base de datos)
mvn -B -o test -Dtest=CorsPreflightIT -DfailIfNoSpecifiedTests=false

# El reporte de cobertura, para abrirlo
open backend/electoral-api-quarkus/target/site/jacoco/index.html

# Frontend
cd frontend/app
pnpm test:ci && pnpm lint && pnpm format:check && pnpm build

# Regenerar los iconos de la PWA (Pillow)
python3 scripts/generate-pwa-icons.py
```

### Comprobaciones manuales de CORS

```bash
# Preflight desde un origen permitido -> 200 con Access-Control-Allow-Origin
curl -i -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: http://localhost:4200" \
  -H "Access-Control-Request-Method: POST"

# Preflight desde un origen NO permitido -> sin Access-Control-Allow-Origin
curl -i -X OPTIONS http://localhost:8080/api/v1/auth/login \
  -H "Origin: https://no-autorizado.example" \
  -H "Access-Control-Request-Method: POST"
```

---

## H — Que se verifico y que no

**Verificado en esta sesion:**

- CORS: preflight de origen permitido y no permitido, peticion real, peticion real protegida sin
  token (401 con cabecera de origen), lista de origenes, metodos, credenciales y headers
  permitidos. 7 tests, todos verdes, **y las 6 mutaciones caen** (seccion C).
- JaCoCo 0.8.13 con Java 25: `prepare-agent` → tests → `report` → `check`, todo en `verify`, con
  `BUILD SUCCESS` y el reporte generado.
- El gate de cobertura: con piso 0.10 (placeholder) el build **fallo** con
  `lines covered ratio is 0.09, but expected minimum is 0.10` — o sea, el gate muerde.
- LICENSE: diff contra el texto oficial de apache.org, una sola linea de diferencia.
- PWA: build verde, `www/manifest.webmanifest` presente y cacheado por el SW, iconos en el
  bundle con los tamanos correctos, geometria de la marca verificada pixel a pixel contra el
  original.
- Frontend: 651 tests, lint y formato limpios.

**No verificado en esta sesion (declarado, no omitido):**

- Los 2 tests de Testcontainers (D.1): sin daemon de Docker. En CI corren.
- El comportamiento de CORS contra un navegador real: se verifico el contrato HTTP, no el
  resultado en un motor. La cookie `SameSite=Strict` no cruza origen en cualquier caso (F.8).
- La instalabilidad de la PWA en un movil: se verifico que el manifest y los iconos llegan al
  bundle, no la instalacion en si.
- La cobertura en CI: el ratio real de CI sera **mayor** que 0.1175 (los 2 IT de Testcontainers
  suman cobertura), asi que el piso de 0.08 no deberia moverse. No verificado: el job de CI no se
  ejecuto.

---

**Estado de la implementacion tras este ODD:** las 5 tareas del task original estan cerradas (4
con desvio deliberado y documentado). El build del backend pasa con el gate de cobertura, el
frontend pasa sus gates, y hay 7 tests nuevos que demonstrates el comportamiento de CORS por
mutacion. Este archivo **no** declara el change listo para produccion: eso lo decide el
verificador y el Arquitecto.
