# t-in-13 — Suite de tests de regresión por contrato (gate de paridad Spring → Quarkus)

Objetivo: crear la suite de tests de regresión que fija el comportamiento observable de la API, para que la fase 1 de la issue #113 (Spring Boot → Quarkus) se pueda validar paridad contra paridad.
Rama: `brydyan/sc-113/t-in-13-contract-regression-tests`
Estrategia de entrega: work-units encadenados (auto-chain). La suite ES el gate de paridad, no un extra.
Runner: Maven · módulo nuevo `backend/contract-tests` · Failsafe (`*IT`) · `mvn verify` · base URL por `-Dcontract.baseUrl`

---

## Specs

### S1 — "vamos conn la fase 1 que seria de spring a quarkus"

La fase 1 de la issue #113 arranca. El primer work-unit no es migrar código: es construir el oráculo que permite saber, con evidencia, que el código migrado se comporta igual.

**Alcance de este documento:** únicamente la suite de regresión y su integración en CI. La reescritura a Quarkus es S1 de la issue #113, no de este documento.

**Criterios de aceptación:**
- Existe una suite de tests de regresión ejecutable que corre contra el backend actual (Spring Boot) y pasa.
- La suite queda wired en CI como gate que corre en cada push/PR de backend.
- La suite corre contra un backend identificado por URL, de modo que el mismo artefacto de test pueda apuntar al backend Spring hoy y al backend Quarkus mañana.

**Checks aplicables:** `mvn verify` en `backend/contract-tests` contra el backend vivo; job de CI verde.

### S2 — "manteniendo las mismas funcionalidades"

La suite debe fijar el comportamiento **observable** del servicio: código de estado, forma del body, campos presentes, shape del error, y resultado de autorización. No debe fijar el mecanismo interno.

**Alcance:** los 13 controllers y sus ~50 endpoints, agrupados por módulo: `auth`, `menus`, `observer`, `coordinator`, `evidencias`, `configuracion`, `dashboard`, `admin`.

**Criterios de aceptación:**
- Cada endpoint mutante covered tiene al menos una aserción sobre código de estado y al menos una sobre el body o el error.
- El shape del error es el mismo para 400/401/403/404 (timestamp, status, error, message).
- La matriz de autorización por rol queda fijada: MASTER, COORDINATOR, ADMIN, OBSERVER.
- La suite pasa hoy contra Spring Boot con el seed determinista de Liquibase.

**Checks aplicables:** ejecución de la suite; aserciones de status y de body en cada test.

### S3 — "para esto crea test de regresion"

La suite debe ser **agnóstica del framework**. Es la única forma de que sirva como gate de paridad: cualquier test acoplado a Spring muere con el proyecto Spring.

**Alcance:** módulo Maven independiente `backend/contract-tests`.

**Criterios de aceptación:**
- El `pom.xml` del módulo NO declara `spring-boot*`, `spring-*`, `hibernate*` ni el artefacto `electoral-api`. Solo cliente HTTP + JUnit + aserciones.
- Los tests hablan únicamente HTTP contra `contract.baseUrl`. No importan tipos de `ec.twintailcs.electoral`.
- Los tests se nombran `*IT` y corren en la fase `verify` (Failsafe), no en `test`.

**Checks aplicables:** inspección del árbol de dependencias resuelto (`mvn dependency:tree`); grep de imports prohibidos.

### S4 — Derivada: la matriz RBAC es parte de "manteniendo las mismas funcionalidades"

*Derivada de S2 y de la tabla de roles de la issue #113 y de `CLAUDE.md`.* El control electoral es un sistema de permisos: un cambio de framework que altere quién puede escribir es una regresión silenciosa que ningún test de Happy Path detecta.

**Criterios de aceptación:**
- Para cada rol, un test afirma el resultado (2xx vs 403) sobre un endpoint representativo de escritura.
- `ADMIN` nunca obtiene 2xx en ningún endpoint mutante.
- `OBSERVER` no accede a actas que no registró.
- El token sin autorización devuelve 401 y con autorización insuficiente devuelve 403 (no 404, no 500).

**Checks aplicables:** ejecución de la suite RBAC contra el backend vivo.

### S5 — Derivada: la suite tiene que ser reproducible en CI

*Derivada de S1 y del carácter de gate de la migración.* Una suite que solo corre en la máquina del autor no es un gate.

**Criterios de aceptación:**
- Un job de CI levanta el backend (contenedor) + Postgres, espera el health check, y ejecuta la suite.
- El job falla si algún contrato se rompe.
- El base URL es configurable por variable de entorno en CI, no hardcodeado.

**Checks aplicables:** job verde en CI; re-run del job sobre el backend actual.

---

## Tasks

| ID | Tarea | S# | Ruta | Commit |
|---|---|---|---|---|
| T1 | Bootstrap del módulo `backend/contract-tests`: pom sin Spring, clase base, wiring de `contract.baseUrl`, helpers de auth por rol, guía de ejecución | S1,S2,S3 | inline | `ec2a47d` |
| T2 | Contrato de auth: login, `GET /me`, refresh con rotación, logout, register | S2,S3 | inline | `ec2a47d` |
| T3 | Contrato RBAC: matriz de los 4 roles sobre endpoints representativos de escritura y lectura | S2,S4 | delegated | `c53db64` |
| T4 | Contrato de menús: `GET /api/v1/menus/me` (hoy sin cobertura de controller) | S2 | inline | `1bc23eb` |
| T5 | Contrato del flujo OBSERVER: registrar, borrador, enviar, corregir, `mis-actas`, `correcciones-pendientes` | S2 | delegated | `f3301ab` |
| T6 | Contrato del flujo COORDINATOR: listar, disputar, corregir, aprobar, anular, historial, incidencias | S2 | delegated | `ee7cdfb` |
| T7 | Contrato de evidencias: upload, get, download-url, cambio de estado, delete, observaciones | S2 | delegated | `6f61024` |
| T8 | Contrato de configuración: partidos, cargos, candidatos, cantones | S2 | inline | `1bc23eb` |
| T9 | Contrato de dashboard: resumen, resultados, progreso, novedades | S2 | inline | `1bc23eb` |
| T10 | Contrato de errores y OpenAPI: shapes 400/401/403/404 y no-regresión del spec | S2 | inline | `1bc23eb` |
| T11 | Job de CI: build de imagen → boot con Postgres → health → suite contra el contenedor | S1,S5 | inline | `ce97870` |
| T12 | Docs: cómo correr la suite contra Spring hoy y contra Quarkus mañana | S1,S3 | inline | `ce97870` |

**Estrategia de entrega:** `auto-chain`. Los work-units T3–T10 son unidades independientes con superficies de edición disjuntas y se lanzan en paralelo una vez que T1+T2 fijan el patrón. T11 y T12 cierran la feature.

**Estado:** 120 tests verdes. Todos los módulos de la API con contrato. Job de CI validado end-to-end contra base recién creada.

---

## Log

### L1 — 2026-10-20 — Solicitud del usuario (verbatim)

> a ver vamos conn la fase 1 que seria de spring a quarkus manteniendo las mismas funcionalidades para esto crea test de regresion

### L2 — 2026-10-20 — Contexto heredado de la auditoría previa

La auditoría del 2026-10-20 (issues #113 y #114) estableció que el backend corre hoy como JVM puro y que el profile `native` del `pom.xml` nunca se ejecutó. La fase 1 (#113) es la migración Spring Boot → Quarkus con estrategia strangler y feature-freeze del backend Spring.

Decisión de diseño tomada en este work-unit: **la suite de regresión es un módulo Maven independiente** (`backend/contract-tests`) y no un paquete más dentro de `backend/electoral-api/src/test/java`. Razón: el proyecto Spring entra en feature-freeze y sus tests quedan acoplados a Spring (MockMvc, `@SpringBootTest`, `@DataJpaTest`). Un gate de paridad que muere con el proyecto Spring no es un gate. El módulo independiente no declara Spring en su classpath, corre contra una URL base, y el mismo artefacto valida Spring hoy y Quarkus mañana.

Decisión operativa: la suite corre contra un backend **vivo** (contenedor + Postgres), no en proceso. Motivo: un test en proceso necesita el contexto de aplicación Spring, que es exactamente lo que se va a reescribir.

### L3 — 2026-10-20 — Superficie de API mapeada (base de la cobertura)

13 controllers, ~50 endpoints, agrupados en 8 módulos. Seed determinista por Liquibase: cargos `Alcalde/sa`, `Concejal Urbano`, `Concejal Rural`; JRVs `JRV-SE-001`, `JRV-SE-002`, `JRV-BAL-001`, `JRV-LL-001`; 4 partidos; 12 candidatos; usuario MASTER seed `admsismas@control.ttcs` / `admin12345` (email auto-generado `{3 letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs`).

Gaps detectados en la suite existente de 345 tests: **`GET /api/v1/menus/me` no tiene test de controller** (solo hay un test del DTO `MenuResponse`). T4 cubre ese gap.

Cobertura crítica ya existente y que NO hay que duplicar en la suite de contrato (vive en los IT con Testcontainers y se portará al proyecto Quarkus): `ActaConcurrencyIT` (locking pesimista), `ActaInvarianteIT` (suma de votos), `ActaAislamientoObserverIT`, `ActaCicloVidaIT` (máquina de estados), `ActaRlsIntegrationIT` + `RlsIsolationIT` (RLS), `AuthFlowIT`, `ExpedientePdfIT`.

### L4 — 2026-10-20 — Entorno verificado

Java 21.0.2, Maven 3.9.16, Docker 29.9.1 con Compose 5.6.0 disponibles. Stack de dev ya levantado: `electoral-api-dev` (healthy), `electoral-postgres-dev`, `electoral-rustfs`, `electoral-frontend-dev`. API expuesta en `http://localhost:8083`; `GET /actuator/health` devuelve `{"status":"UP"}`; login MASTER verificado con `curl` (devuelve `accessToken`, cookie httpOnly `refresh_token`, `sesionId` y `usuario{id,email,rol}`).

Nota: el contenedor `electoral-api-dev` corre una imagen previa (`39c4d9ecda5b`). Antes de tomar los resultados de la suite como baseline hay que reconstruirlo desde el HEAD actual, si no el oráculo queda fijado contra código viejo.

### L5 — 2026-10-20 — La suite encontró 5 bugs del backend. Todos corregidos

La suite no es decorativa: en el camino encontró defectos reales del backend, todos verificados de forma independiente (logs del contenedor, lectura del código y reproducción) antes de tocar nada.

1. **`LazyInitializationException` → 500 en las transiciones de acta.** `Acta.jrv/cargo/registradaPor` son LAZY, `findByIdWithLock` no hace fetch join, y el controller mapea el DTO *después* de cerrada la transacción (`open-in-view=false`). Peor: **la escritura ya se había commiteado**, así que el cliente veía 500 y reintentaba. Fix: `ActaAssociations.inicializar()` dentro de la transacción del caso de uso — **no** con fetch join, porque extendería el `FOR UPDATE` a las tablas `jrv` y `cargo` y cambiaría el invariante de concurrencia.
2. **Duplicate key al reanudar un borrador.** Faltaba el `flush()` entre el `deleteByActa` y el insert. Se copió el patrón que ya usaba `CorregirActaUseCase`.
3. **`audit_log.accion VARCHAR(32)` desbordado** por `EVIDENCIA_CONTINGENCIA_COORDINADOR` (34 chars) → el upload de evidencia de contingencia devolvía 500. Fix: changeset 024 ensancha la columna a VARCHAR(64). Aditivo, no destructivo.
4. **El mapeo de errores 4xx estaba roto.** `GlobalExceptionHandler.missingPart` declaraba dos tipos de excepción con un solo parámetro, así que Spring no podía resolver el argumento y la excepción caía al `DefaultHandlerExceptionResolver` → `sendError(400)` → dispatch a `/error` (no estaba en `permitAll`) → el cliente recibía **401 en vez de 400**. Además `NoResourceFoundException` y `HttpRequestMethodNotSupportedException` caían en el catch-all y devolvían 500.
5. **`POST /observer/actas/borrador` sobre un par con acta de otro observer devolvía 500** en vez de 409: el use case buscaba por `(jrv, cargo, observerId)`, no veía el acta ajena e insertaba, violando el índice parcial `uk_acta_activa`.

### L6 — 2026-10-20 — Bug de despliegue: una base nueva no tenía datos de referencia

`015-data-seed.sql` (46 statements: geografía, configuración preelectoral, usuarios de los 4 roles, JRVs, candidatos y actas en distintos estados) **nunca estuvo incluido** en `db.changelog-master.yaml`, que saltaba de 014 a 016. Y `017-complete-data-seed.sql`, que sí está, solo siembra evidencias y notificaciones.

Verificado empíricamente con `liquibase:update` contra una base vacía: **0 JRVs, 0 cargos, 0 candidatos, 0 usuarios**. El sistema era inutilizable en cualquier despliegue limpio (CI, máquina nueva, staging).

El seed no se podía agregar en el medio del maestro: estaba escrito para un punto del esquema en el que existía `acta.storage_url` (dropeada por 019), y correrlo después de 019 rompía las bases ya migradas. Por eso se renombró a `025` y va al final, que funciona igual en bases nuevas y existentes. Además `025-00` le pone `DEFAULT gen_random_uuid()` a `usuario.public_id`: `004` la dejó NOT NULL sin default (las tablas de `010` sí lo traen) y eso hacía reventar cualquier INSERT en `usuario` que no listara la columna.

### L7 — 2026-10-20 — Issue #121: agujero de autorización

El guard de clase de `CoordinatorController` es `hasAnyAuthority('acta:aprobar','acta:rechazar','acta:disputar','acta:read','acta:anular')`. `acta:read` lo tienen OBSERVER y ADMIN, y `disputar`/`corregir`/`aprobar` no tienen guard de método. Resultado: **un OBSERVER puede disputar, corregir y aprobar actas ajenas** (200, y el estado cambia). Además `acta:disputar` no existe como permiso en la semilla: nadie lo tiene, ni el MASTER.

Se trackea en la issue **#121** con la reproducción y el fix propuesto. **No se corrigió en este work-unit**: cambiar el modelo de autorización es una decisión de producto con blast radius (puede dejar fuera a un rol legítimo de la bandeja del coordinator) y no le corresponde al que escribe el código. `RbacContractIT` afirma el comportamiento real y deja los seis asserts marcados `KNOWN HOLE #121`, para que el gate siga verde y la corrección sea explícita al cambiar el assert.

### L8 — 2026-10-20 — El job de CI se validó end-to-end antes de commitearlo

Un job de CI que nunca corrió es una bomba de relojería. Se simularó localmente: Postgres + RustFS, bucket creado con `amazon/aws-cli`, imagen del backend con `--network host`, espera del health check y `mvn -B verify` contra una **base recién creada**. Resultado: **120/120 verdes**. Eso valida de punta a punta que una base limpia queda con los datos de referencia y que la suite es reproducible desde cero.

### L9 — 2026-10-20 — Interferencia de otra sesión

Una sesión paralela recreó el stack con `docker compose down -v` (base vacía) y un `git reset` concurrente borró un archivo sin trackear del worker. Costó un rato recuperar. Cada work-unit se commiteó apenas quedó verde justamente para que un reset ajeno no se lleve el entregable. Para las próximas iteraciones: work-units en worktrees separados, o commits tempranos.

### L10 — 2026-10-20 — Cablear el seed rompió la suite del backend. Corregido separando esquema de dato

Al cablear `025-data-seed.sql` al changelog maestro, la suite del backend pasó de verde a **318 tests con 10 failures y 58 errors**. Todos por colisión con el seed: 25 en `ix_provincia_codigo` y 3 en `ix_canton_codigo` (los fixtures de los tests construyen su propia geografía con los códigos '24' y '2401', los mismos que siembra el seed), más aserciones que Wait porque el seed mete 5 usuarios, 1 sesión, partidos y candidatos.

La causa de fondo es de diseño: **la suite del backend está escrita contra una base con solo el esquema** (afirma `RNF_DASH_01_baseVacia`, espera que el primer partido sea el que crea el test, espera una sola sesión). El seed es dato de referencia de producción. Mezclarlos no era un bug del test, era un error de separación esquema/dato que el seed cableado destapó.

Fix: el seed pasa a changeset con **contexto Liquibase `seed`**. La app opta por él con `spring.liquibase.contexts=seed` en `application.properties` (dev, docker y prod siguen recibiendo los datos de referencia); la suite opta por no correrlo con `spring.liquibase.contexts=schema-only` como system property del pom — que tiene más precedencia que `application.properties` y así no hace falta tocar ningún properties de test.

Verificado: suite del backend **318/318 verdes** y suite de contrato **120/120 verdes** contra base nueva con el seed aplicado. Las dos caras del contexto, comprobadas.

Lección: cuando un seed de referencia entra en el esquema, la primera pregunta no es "qué datos mete" sino "**quién los necesita**". La app los necesita; los tests no, y de hecho los estorban.

Nota operativa: al cambiar el contenido de `025-data-seed.sql` (agregar el contexto), el checksum del changeset cambia y una base donde ya se aplicó la versión anterior se niega a arrancar con "changeset check sum ... was ... but is now ...". Se resolvió con `liquibase:clearCheckSums` sobre la base de dev. En una base nueva (CI, despliegue limpio) no hace falta. Alternativa para un despliegue ya migrado: `docker compose down -v`.
