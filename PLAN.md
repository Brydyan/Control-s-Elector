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

Test runner backend: `cd backend/electoral-api && ./mvnw test`.
Cada slice = un PR encadenado apuntando al anterior; PR1 → `develop`.

### Slice 1 — Foundation + Evidencia  ⏳ EN CURSO (parcial)

Rama: `feat/uuidv7-slice1-evidencia` (creada desde `develop`).

**Ya hecho (parcial, SIN commit, SIN build corrido):**

- [x] `pom.xml`: dep `com.github.f4b6a3:uuid-creator:6.1.1` + deps test-scope.
  ⚠️ Los artefactos de test que se agregaron (`spring-boot-data-jpa-test`,
  `spring-boot-webmvc-test`, `testcontainers-junit-jupiter`, `testcontainers-postgresql`,
  `spring-boot-testcontainers`) tienen **nombres no-estándar y NO están verificados**
  — el build nunca corrió. Validar contra el BOM de Spring Boot 4.1 antes de confiar.
- [x] `shared/persistence/UuidV7Generator.java` — implementa `UuidValueGenerator`,
  retorna `UuidCreator.getTimeOrderedEpoch()`. Completo.
- [x] `shared/persistence/UuidV7GeneratorTest.java` — test unitario (version()==7 +
  orden monótono). ⚠️ **No corrido**. El test de monotonía usa `UUID.compareTo`, que
  en Java hace comparación de longs con signo y es poco fiable para ordenar UUIDs —
  revisar/reforzar cuando se corra.

**Falta en Slice 1:**

- [ ] **SPI gate (primer red-green)**: probar que `@UuidGenerator(algorithm = UuidV7Generator.class)`
  compila y genera v7 en Hibernate 7.1. Si el atributo `algorithm` no existe en 7.1,
  fallback a una anotación custom con `@IdGeneratorType`. **Bloquea el resto del slice.**
- [ ] `Evidencia` entity: agregar `UUID publicId` (`@UuidGenerator(...)`,
  `@Column(nullable=false, unique=true, updatable=false, columnDefinition="uuid")`);
  mantener `Long id` IDENTITY.
- [ ] `EvidenciaRepository.findByPublicId(UUID)`.
- [ ] Liquibase `004-public-ids.sql` (tabla evidencia): add column, backfill
  `gen_random_uuid()`, NOT NULL + UNIQUE + índice, idempotente, `-- rollback`;
  registrar en `db.changelog-master.yaml`.
- [ ] Boundary → UUID: `EvidenciaResponse` + `DownloadUrlResponse` exponen `publicId`
  como `id`; `EvidenciaController` path vars `Long id` → `UUID id`; el service resuelve
  `findByPublicId` y mapea desconocido → `EvidenciaNotFoundException` (404).
- [ ] Tests: unit (generador), `@WebMvcTest` (controller con UUID, 200 + 404),
  `@DataJpaTest` + Testcontainers Postgres (publicId autogenerado, round-trip, backfill).
- [ ] `./mvnw test` GREEN. Commits en work units. Push + PR1 → `develop`.

### Slice 2 — Auth  🔜

- [ ] `Usuario.publicId`; `JwtService` pone `publicId` en `sub`; `AuthController:98`
  parse `Long.parseLong(...)` → `findByPublicId(UUID.fromString(...))`.
- [ ] `AuthResponse.UsuarioActual` mapper → UUID.
- [ ] Liquibase backfill tabla `usuario` + rows seed de `002-rbac-seed`.
- [ ] Tests + `./mvnw test` GREEN.
- ⚠️ **Solapa con PR #12** (que modifica `AuthController`/`AuthResponse`). Coordinar:
  mergear PR #12 antes de Slice 2, o rebasar Slice 2 sobre esos cambios.
- ~180–220 líneas.

### Slice 3 — RBAC (sin Sesión)  🔜

- [ ] `Menu`, `MenuPermiso`, `Permiso`, `RolPermiso`: dual-column + `findByPublicId`.
- [ ] `MenuResponse.MenuNode` mapper (id + `parentId` vía publicId del padre).
- [ ] Liquibase backfill seeds de `002` / `003`.
- [ ] **Sesión NO entra** (ya usa `sesionId` directo).
- [ ] Tests + `./mvnw test` GREEN.
- ~350–400 líneas (rozaba 400; sin Sesión queda holgado).

---

## 4. Convención (para el futuro)

Las entidades nuevas (`acta`, `jrv`, `candidato`…) nacen dual-key desde el inicio.
Documentar en:

- [ ] `backend/electoral-api/src/main/resources/database/README.md` — sección del patrón.
- [ ] ADR `backend/electoral-api/docs/adr/0001-dual-key-public-ids.md`.

---

## 5. Riesgos / pendientes abiertos

- **Build nunca corrió** en el parcial de Slice 1 → deps de test y SPI gate sin validar.
- **Cobertura backend casi nula** (solo un smoke test) + **Strict TDD activo** → los
  primeros PRs backend van más lentos (test primero, obligatorio).
- **Testcontainers necesita Docker** disponible en local y CI.
- **JWT breaking (D3)**: tokens viejos dejan de resolver; se banca la expiry de 15m.
- **Backfill v4 (D4)**: los seeds viejos quedan con UUID v4, no v7 (aceptado).
- **`openspec/config.yaml`** está stale (dice npm, solo frontend) — irrelevante en modo
  engram, pero reescribir si el equipo pasa a openspec/hybrid.
- **Java 25→21** sin aplicar (sección 1.2).
- **Backend de PR #12** sin revisar (sección 1.1).

---

## 6. Cómo retomar

1. Rama activa del slice en curso: `feat/uuidv7-slice1-evidencia` (parcial, sin commit).
   - Retomar: relanzar `sdd-apply` de Slice 1, o continuar a mano desde el checklist §3.
   - Descartar el parcial: `git restore backend/electoral-api/pom.xml` y borrar
     `shared/persistence/` (main + test).
2. Config SDD cacheada: **Engram · Automatic · ask-on-risk · stacked-to-main**.
3. Test: backend `cd backend/electoral-api && ./mvnw test` · frontend `cd frontend/app && pnpm test`.
4. Los artefactos completos (spec/design/tasks con file:line) están en Engram (§2.5).
