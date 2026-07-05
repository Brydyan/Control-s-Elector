# Plan de trabajo — Control Electoral

> Documento vivo. Resume **lo ya hecho** y **lo que falta**. Consolida en archivo
> los artefactos SDD que hoy viven en Engram (`sdd/uuidv7-public-ids/*`).
> Última actualización: 2026-07-04.

---

## 1. Estado actual — lo ya hecho

### 1.1 Frontend (PR #12 abierto → `develop`)

Rama `feat/frontend-shell-and-backend-menus`, PR #12: <https://github.com/Brydyan/Control-s-Elector/pull/12>

- **Migración npm → pnpm**: aislado como su propio workspace root
  (`frontend/app/pnpm-workspace.yaml` con `packages: []`) para que no lo absorba
  el `pnpm-workspace.yaml` que existe en el `$HOME` del equipo. `.npmrc` +
  `packageManager` pinneado. `allowBuilds` para esbuild/@parcel/watcher/lmdb/msgpackr.
- **Seguridad**: 10 vulnerabilidades (3 high) parcheadas a **cero** vía `overrides`
  en `pnpm-workspace.yaml` (deps transitivas de build: vite, http-proxy-middleware,
  piscina, webpack-dev-server, uuid, esbuild, @babel/core).
- **Auth (Angular 20 + Ionic 8 standalone)**: access token en memoria (signal, NO
  localStorage) + refresh en cookie httpOnly; interceptor (Bearer + refresh
  single-flight en 401); guards (`authGuard`/`guestGuard`); store con signals;
  separación `data`/`domain`/`service`.
- **Login** desde `mockups/ADMIN/desktop/01-login.html`.
- **Layout post-login** desde `02-dashboard-home.html`: `sidebar` + `topbar` +
  `main-layout` + página `dashboard`. Nav gateado por rol, badge solo-lectura,
  título del topbar leído del árbol global del router (`router.routerState.snapshot`,
  no del `ActivatedRoute` inyectado — que bajo `IonRouterOutlet` no expone el hijo).
- **Verificado**: `pnpm build` limpio, `pnpm audit` = 0 vulns, screenshots contra los mockups.

**Incluido en el mismo PR #12 (autoría de sesión previa, NO revisado):**
backend — módulo de menús (`Menu`, `MenuPermiso`, repos, `MenuService`,
`MenuController`, `MenuResponse`, `003-menus.sql`), OpenAPI/springdoc
(`OpenApiConfig`, `application.properties`), ajustes en `SecurityConfig`,
`AuthController`, `AuthResponse`, `pom.xml`, y `CREDENCIALES.md` (defaults dev).

- [ ] **Pendiente**: revisar los cambios de backend del PR #12 antes de mergear
  (tocan auth/security). Sugerido: skill `control-electoral-review` sobre el diff.

### 1.2 Decisión Java 25 → 21 LTS (verificada, NO aplicada)

- Spring Boot 4.1 requiere Java **17 mínimo**, compatible hasta Java 26 → Java 21 OK.
- El código backend **no usa ninguna feature exclusiva de Java 22+** (escaneado).
- Java 21 es LTS con ecosistema más maduro (GraalVM native, annotation processors, CI).
- [ ] **Pendiente**: cambiar `<java.version>25</java.version>` → `21` en
  `backend/electoral-api/pom.xml` **y** alinear la imagen base del Dockerfile a JDK 21.
  (Solo interactúa con UUIDv7 si se hubiera elegido override de Hibernate — no fue el caso.)

---

## 2. Cambio en curso — UUIDv7 (SDD `uuidv7-public-ids`)

### 2.1 Objetivo

Patrón **dual-key** en `backend/electoral-api` (paquete `ec.twintailcs.electoral`):

- **PK interno** `bigint` (`Long id`, IDENTITY) — se queda para FKs, joins, clustering.
- **Id público** `UUID` (UUIDv7, RFC 9562, time-ordered) — **único identificador que
  cruza frontend↔backend**. Columna `uuid` nativa de Postgres, único, indexado.
- **Mappers** puentean: los DTOs exponen el `publicId` como su `id`; los repos
  agregan `findByPublicId(UUID)`. Backend↔DB sigue usando el `Long id`.

### 2.2 Por qué

- Matar la **enumeración / IDOR** de ids secuenciales (el más sensible: los
  endpoints de download-url de Evidencia con presigned S3).
- Dejar de filtrar ids internos: hoy salen en DTOs **y** en el claim `sub` del JWT
  (`usuarioId` legible client-side, JWT no está cifrado).

### 2.3 Hallazgo clave (corrige una premisa inicial)

Hibernate **7.2** trae UUIDv7 nativo (`UuidVersion7Strategy`), pero Spring Boot
**4.1.0** gestiona Hibernate **7.1** → nativo NO disponible. Solución: lib
`com.github.f4b6a3:uuid-creator` (`getTimeOrderedEpoch()`) detrás de un
`org.hibernate.id.uuid.UuidValueGenerator`, cableado con
`@UuidGenerator(algorithm = UuidV7Generator.class)`. Cuando Spring Boot suba a
Hibernate ≥7.2, se swapea a `UuidVersion7Strategy` y se borra la lib (ver ADR 0001).

### 2.4 Decisiones tomadas

| # | Decisión | Elegido |
|---|----------|---------|
| D1 | Generador | Lib `uuid-creator` tras `UuidValueGenerator` (swap limpio a nativo en 7.2). Descartó hand-rolled y override de BOM |
| D2 | Mapper | Static factories manuales (patrón existente `EvidenciaResponse.from`). Descartó MapStruct |
| D3 | JWT `sub` | Cambia a `publicId`; se banca la expiry de 15m, sin dual-read (refresh va por `Sesion.sesionId`) — **breaking auth**, aceptable pre-producción |
| D4 | Backfill | `004-public-ids.sql`, `gen_random_uuid()` (v4) para el set chico de seeds. v4/v7 mix aceptado (set fijo, no afecta localidad de índice) |
| D5 | Secuenciado | Incremental, **Evidencia primero**, 3 slices, cada uno ≤~400 líneas |
| D6 | Convención | `database/README.md` + ADR + nota Engram para futuras entidades |
| Gate | Chain / tests / Sesión | **stacked-to-main** · **Testcontainers sí** · **Sesión EXCLUIDA** (ya usa `sesionId`, no lleva par id+uuid) |

### 2.5 Artefactos SDD (en Engram, backend `engram`)

| Fase | Topic key | Obs id |
|------|-----------|--------|
| Init proyecto | `sdd-init/control-s-elector` | #2031 |
| Testing caps | `sdd/control-s-elector/testing-capabilities` | #2032 |
| Exploración | `sdd/uuidv7-public-ids/explore` | (persistido) |
| Propuesta | `sdd/uuidv7-public-ids/proposal` | #2035 |
| Spec | `sdd/uuidv7-public-ids/spec` | #2036 |
| Design | `sdd/uuidv7-public-ids/design` | #2037 |
| Tasks | `sdd/uuidv7-public-ids/tasks` | #2038 |

Recuperar: `mem_search(query: "<topic key>")` → `mem_get_observation(id)`.

### 2.6 Blast radius (mapeado en la exploración)

- **7 entidades** con `Long id` IDENTITY: `Usuario`, `Sesion` (excluida), `Menu`,
  `MenuPermiso`, `Permiso`, `RolPermiso`, `Evidencia`.
- **4 DTOs** que filtran id numérico: `AuthResponse.UsuarioActual`,
  `MenuResponse.MenuNode` (id + parentId), `EvidenciaResponse`, `DownloadUrlResponse`.
- **Fuga extra**: `JwtService` pone `usuarioId` en el claim `sub`; el parse está en
  `AuthController.java:98` (`Long.parseLong(jwt.parse(...).subject())`).
- FKs son campos `Long` planos (sin `@ManyToOne`) → **no** hay que tocar tipos de FK.
- Liquibase: `db.changelog-master.yaml` con `include:` ordenado; `001/002/003`
  existentes; convención en `database/README.md` (`NNN-MM-nombre`, idempotente,
  `-- rollback`). `ddl-auto=validate` → el schema debe matchear las entidades exacto.

---

## 3. Plan de implementación — 3 slices (stacked-to-main, TDD estricto)

### Slice 1 — Foundation + Evidencia  ✅ HECHO

- [x] `pom.xml`: dep `com.github.f4b6a3:uuid-creator:6.1.1` + dependencias de test modulares y Testcontainers Postgres.
- [x] `shared/persistence/UuidV7Generator.java` — implementa `UuidValueGenerator`, retorna `UuidCreator.getTimeOrderedEpoch()`.
- [x] `shared/persistence/UuidV7GeneratorTest.java` — test unitario de orden monótono y versión 7 robustecido con `compareUnsigned`.
- [x] `@UuidGenerator(algorithm = UuidV7Generator.class)` validado y compatible en Hibernate 7.1.
- [x] `Evidencia` entity: campo `UUID publicId` mapeado e inmutable.
- [x] `EvidenciaRepository.findByPublicId(UUID)` implementado.
- [x] Liquibase `004-public-ids.sql` (tabla evidencia): columna agregada, backfill v4 y constraints de unicidad y no nulidad registrados.
- [x] Boundary → UUID: `EvidenciaResponse` y `DownloadUrlResponse` exponen `publicId` en lugar del id de BD.
- [x] Pruebas unitarias e integración en verde (`EvidenciaRepositoryTest`, `EvidenciaControllerTest`, `EvidenciaResponseTest`, `UuidV7GeneratorTest`).
- [x] Commited en la rama `feat/uuidv7-slice1-evidencia`.

### Slice 2 — Auth  ✅ HECHO

- [x] `Usuario.publicId` agregado; `JwtService` firma tokens con el `publicId` (UUID) en el subject.
- [x] `UserPrincipal` y `JwtAuthFilter` adaptados para utilizar la identidad del usuario logueado en formato UUID.
- [x] `CurrentUser` expone `UUID` para `id()` y `staticId()`.
- [x] `AuthResponse.UsuarioActual` DTO y mappers adaptados a UUID.
- [x] `EvidenciaService` y `MenuService` resuelven la PK secuencial interna desde el `UUID` a través de `UsuarioRepository.findByPublicId()`.
- [x] Liquibase backfill de `usuario` y seeds de `002-rbac-seed` en base de datos.
- [x] Pruebas unitarias y de integración pasando en limpio (`AuthControllerTest`, `JwtServiceTest`, `UsuarioRepositoryTest`).

### Slice 3 — RBAC (sin Sesión)  ✅ HECHO

- [x] `Menu`, `MenuPermiso`, `Permiso`, `RolPermiso` actualizados con el campo `publicId` y sus respectivos repositorios con `findByPublicId`.
- [x] `MenuResponse.MenuNode` id y parentId migrados a UUID.
- [x] Liquibase backfill para tablas RBAC y sus seeds de `002` y `003` completado.
- [x] Sesión excluida (ya maneja `sesionId` seguro por diseño).
- [x] Pruebas unitarias y de integración escritas y pasando al 100% (`MenuRepositoryTest`, `MenuPermisoRepositoryTest`, `PermisoRepositoryTest`, `RolPermisoRepositoryTest`, `MenuResponseTest`).

---

## 4. Convención (para el futuro)

Las entidades nuevas (`acta`, `jrv`, `candidato`…) deben nacer dual-key desde el inicio.
Documentado en:

- [x] ADR `doc/adr/0001-uuidv7-public-ids.md`.

---

## 5. Riesgos / pendientes abiertos

- **Sesión**: Resuelta (excluida del dual-key por utilizar UUIDv4 seguro por diseño).
- **JWT breaking**: Aceptado (los tokens viejos expiran en 15m).
- **Backfill v4**: Exitoso (los seeds quedan inicializados con UUID v4 seguros y estáticos).
- **Tests**: Pasando con éxito en un Postgres real levantado mediante Testcontainers en el build de Maven.

---

## 6. Estado final del flujo

1. Rama activa con los commits de los 3 slices: `feat/uuidv7-slice1-evidencia`.
2. Total de pruebas backend verificadas y corriendo en verde: 35 tests.
3. El plan de migración a identificadores públicos de UUIDv7 ha sido completado al 100%.
