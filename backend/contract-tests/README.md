# electoral-contract-tests

Suite de tests de **regresión por contrato** del backend `electoral-api`.

Es el gate de paridad de la fase 1 de la issue [#113](https://github.com/Brydyan/Control-s-Elector/issues/113) (migración Spring Boot → Quarkus).

## Por qué es un módulo aparte

Este módulo **no declara Spring, ni Hibernate, ni el artefacto `electoral-api`**. Solo un cliente HTTP, JUnit y AssertJ.

Esa ausencia es el diseño, no una coincidencia:

- El backend Spring entra en **feature-freeze** durante la migración. Sus tests (MockMvc, `@SpringBootTest`, `@DataJpaTest`) quedan acoplados a Spring y mueren con el proyecto Spring.
- Un gate de paridad que depende del framework que se está reemplazando **no es un gate**.
- La misma suite corre contra el backend Spring hoy y contra el backend Quarkus mañana. Solo cambia la URL.

Verificable:

```bash
mvn dependency:tree   # no debe aparecer spring-boot*, spring-*, hibernate* ni electoral-api
```

## Cómo corre

La suite necesita un **backend vivo**. No levanta nada: se le pasa la URL.

```bash
# contra el stack de desarrollo local (compose publica 8083)
mvn -B verify

# contra otra URL
mvn -B verify -Dcontract.baseUrl=http://localhost:8080
```

Resolución de la URL, en orden:

| Fuente | Ejemplo |
|---|---|
| `-Dcontract.baseUrl` | `mvn verify -Dcontract.baseUrl=https://api.staging` |
| variable `CONTRACT_BASE_URL` | `CONTRACT_BASE_URL=https://api.staging mvn verify` |
| default | `http://localhost:8083` |

Credenciales del usuario MASTER seedeado (necesario para registrar a los demás roles):

| Fuente | Default |
|---|---|
| `-Dcontract.master.email` / `CONTRACT_MASTER_EMAIL` | `admsismas@control.ttcs` |
| `-Dcontract.master.password` / `CONTRACT_MASTER_PASSWORD` | `admin12345` |

El email del MASTER sale del `DataSeeder`: 3 letras del nombre + 3 del apellido + 3 del rol, normalizado. Con los defaults (`Admin` / `Sistema` / `MASTER`) → `admsismas@control.ttcs`.

## Estructura

```
src/test/java/ec/twintailcs/electoral/contract/
├── ContractTestSupport.java   # base compartida: config, auth por rol, helpers, contrato de error
├── AuthContractIT.java        # login, /me, refresh con rotación, logout, register
└── <Modulo>ContractIT.java    # un archivo por módulo de la API
```

Convenciones:

- Sufijo `*IT` → corre en la fase `verify` vía Failsafe (no en `test`).
- Las clases extienden `ContractTestSupport` y usan sus helpers: `as(token)`, `anon()`, `json(body)`, `tokenFor(rol)`, `registerUser(rol)`, `unique(base)`, `assertErrorShape(response, status)`.
- Los datos que el test crea llevan `unique(...)` → la suite es **re-ejecutable sobre la misma base** sin limpiar nada.
- Los tests hablan **solo HTTP**. Ningún import de `ec.twintailcs.electoral.*` ni de Spring.

## Qué fija

El **comportamiento observable**: código de estado, forma del body, campos presentes, shape del error y resultado de autorización. No el mecanismo interno.

- Los endpoints mutantes de `auth`, `menus`, `observer`, `coordinator`, `evidencias`, `configuracion`, `dashboard` y `admin`.
- La matriz de autorización de los 4 roles (MASTER / COORDINATOR / ADMIN / OBSERVER).
- El shape estándar de error: `{timestamp, status, error, message}` (+ `fields` en errores de validación).

## Qué NO cubre

La suite de contrato es **black-box**. Los invariantes que necesitan estado interno ya tienen cobertura en la suite JPA de `backend/electoral-api` (que se portará al proyecto Quarkus):

| Invariante | Test actual |
|---|---|
| Locking pesimista `SELECT FOR UPDATE` por `jrv_id + cargo_id` | `ActaConcurrencyIT` |
| Suma de votos `SUM(detalle.votos) + nulos + blancos == total_sufragantes` | `ActaInvarianteIT` |
| Aislamiento de actas por `registrada_por_id` | `ActaAislamientoObserverIT` |
| Máquina de estados del acta | `ActaCicloVidaIT` |
| RLS de Postgres | `ActaRlsIntegrationIT`, `RlsIsolationIT` |
| Flujo de auth completo | `AuthFlowIT` |
| PDF del expediente de impugnación | `ExpedientePdfIT` |

## Cuando exista el backend Quarkus

La suite no cambia. Solo cambia la URL:

```bash
# Spring Boot (hoy)
mvn -B verify -Dcontract.baseUrl=http://localhost:8083

# Quarkus (fase 1 de #113)
mvn -B verify -Dcontract.baseUrl=http://localhost:8080
```

Si el backend Quarkus devuelve algo distinto, **el fallo es del backend**, no del test. Eso es exactamente lo que un gate de paridad tiene que hacer.
