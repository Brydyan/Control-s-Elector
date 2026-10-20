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
| T1 | Bootstrap del módulo `backend/contract-tests`: pom sin Spring, clase base, wiring de `contract.baseUrl`, helpers de auth por rol, guía de ejecución | S1,S2,S3 | inline | — |
| T2 | Contrato de auth: login, `GET /me`, refresh con rotación, logout, register | S2,S3 | inline | — |
| T3 | Contrato RBAC: matriz de los 4 roles sobre endpoints representativos de escritura y lectura | S2,S4 | delegated | — |
| T4 | Contrato de menús: `GET /api/v1/menus/me` (hoy sin cobertura de controller) | S2 | delegated | — |
| T5 | Contrato del flujo OBSERVER: registrar, borrador, enviar, corregir, `mis-actas`, `correcciones-pendientes` | S2 | delegated | — |
| T6 | Contrato del flujo COORDINATOR: listar, disputar, corregir, aprobar, anular, historial, incidencias | S2 | delegated | — |
| T7 | Contrato de evidencias: upload, get, download-url, cambio de estado, delete, observaciones | S2 | delegated | — |
| T8 | Contrato de configuración: partidos, cargos, candidatos, cantones | S2 | delegated | — |
| T9 | Contrato de dashboard: resumen, resultados, progreso, novedades | S2 | delegated | — |
| T10 | Contrato de errores y OpenAPI: shapes 400/401/403/404 y no-regresión del spec | S2 | delegated | — |
| T11 | Job de CI: build de imagen → boot con Postgres → health → suite contra el contenedor | S1,S5 | inline | — |
| T12 | Docs: cómo correr la suite contra Spring hoy y contra Quarkus mañana | S1,S3 | inline | — |

**Estrategia de entrega:** `auto-chain`. Los work-units T3–T10 son unidades independientes con superficies de edición disjuntas y se lanzan en paralelo una vez que T1+T2 fijan el patrón. T11 y T12 cierran la feature.

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
