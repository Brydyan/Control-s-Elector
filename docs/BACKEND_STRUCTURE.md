# Backend Structure — Control Electoral API (Quarkus)

Generated: 2026-11-21

## Module Architecture

```
electoral-api-quarkus/
├── auth/                    ← Authentication & Authorization
│   ├── domain/              ← Entity: Usuario, Rol, RolPermiso
│   ├── application/         ← Use cases: JwtService, PasswordHasher
│   ├── web/                 ← REST: LoginResource, AdminCacheResource
│   └── security/            ← Autorizador, SecurityContext
│
├── acta/                    ← Electoral Acts (minutes)
│   ├── domain/              ← Entity: Acta, ActaDetalle
│   ├── application/         ← Use cases: CrearActaUseCase, ListarActasUseCase
│   ├── web/                 ← REST: ActaResource
│   └── infrastructure/      ← ActaRepository (JPA)
│
├── evidencia/               ← Evidence (photos, documents)
│   ├── domain/              ← Entity: Evidencia, EvidenciaDetalle
│   ├── application/         ← Use cases: SubirEvidenciaUseCase
│   ├── web/                 ← REST: EvidenciaResource
│   └── infrastructure/      ← EvidenciaRepository, S3Storage
│
├── geografia/               ← Geographic hierarchy
│   ├── domain/              ← Entity: Provincia, Canton, Parroquia, JRV
│   ├── application/         ← Use case: ListarGeografiaUseCase (@CacheResult)
│   ├── web/                 ← REST: GeografiaResource
│   └── infrastructure/      ← GeografiaRepository
│
├── dashboard/               ← Coordinator dashboard
│   ├── domain/              ← Value objects: DashboardMetrics
│   ├── application/         ← Use case: ObtenerDashboardUseCase (@CacheResult)
│   ├── web/                 ← REST: DashboardResource
│   └── infrastructure/      ← DashboardRepository
│
├── electoral/               ← Electoral configuration
│   ├── domain/              ← Entity: Cargo, Partido, Candidato
│   ├── application/         ← Use cases: ListarCargosUseCase, ListarPartidosUseCase (@CacheResult)
│   ├── web/                 ← REST: ConfigResource, ContextoSolicitud
│   └── infrastructure/      ← ElectoralRepository
│
├── auditlog/                ← Audit trail
│   ├── domain/              ← Entity: AuditLog
│   ├── application/         ← Use case: AuditLogService (record all changes)
│   └── infrastructure/      ← AuditLogRepository (RLS policy)
│
├── legal/                   ← Legal agreements (terms)
│   ├── domain/              ← Entity: AcuerdoLegal
│   ├── application/         ← Use case: ObtenerAcuerdosUseCase (@CacheResult)
│   ├── web/                 ← REST: LegalResource
│   └── infrastructure/      ← LegalRepository
│
├── monitoring/              ← System monitoring
│   ├── application/         ← Health checks, metrics
│   └── web/                 ← REST: HealthResource
│
├── common/                  ← Shared utilities
│   ├── exception/           ← DomainException, NotFoundException, etc.
│   ├── filter/              ← CORSFilter, RequestIdFilter
│   └── validator/           ← Input validators
│
└── shared/                  ← Cross-cutting concerns
    ├── config/              ← AppConfig, CacheConfig, DatabaseConfig
    └── interceptor/         ← Logging, error handling
```

## Dependency Flow

```
REST Layer (web/*.Resource)
    ↓
Application Layer (application/*.UseCase, *.Service)
    ↓
Domain Layer (domain/Entity, domain/ValueObject)
    ↓
Infrastructure Layer (infrastructure/*.Repository)
    ↓
Persistence (JPA/Hibernate) + Cache (@CacheResult)
    ↓
PostgreSQL Database
```

## Caching Strategy

| Module | Cache Name | Method | TTL |
|--------|-----------|--------|-----|
| `geografia` | `geografia` | `ListarGeografiaUseCase.execute()` | 10m |
| `dashboard` | `dashboard` | `ObtenerDashboardUseCase.execute()` | 10m |
| `electoral` | `cargos` | `ListarCargosUseCase.execute()` | 10m |
| `electoral` | `partidos` | `ListarPartidosUseCase.execute()` | 10m |
| `electoral` | `candidatos` | `ListarCandidatosUseCase.execute()` | 10m |
| `legal` | `acuerdos` | `ObtenerAcuerdosUseCase.execute()` | 10m |

**Cache Invalidation:** `GET /api/v1/admin/cache/clear` (requires `cache:clear` permission or `MASTER` role)

## Key Files by Domain

### Auth
- `Usuario.java` — User entity
- `Rol.java` — Role entity
- `RolPermiso.java` — Permission mapping
- `JwtService.java` — Token generation/validation
- `LoginResource.java` — `POST /auth/login`
- `AdminCacheResource.java` — `GET /api/v1/admin/cache/clear`

### Acta
- `Acta.java` — Act entity
- `ActaResource.java` — CRUD endpoints
- `CrearActaUseCase.java` — Create logic
- `ListarActasUseCase.java` — List logic (filtered by user role/location)

### Evidencia
- `Evidencia.java` — Evidence entity
- `EvidenciaResource.java` — Upload/download endpoints
- `SubirEvidenciaUseCase.java` — Storage to S3

### Geografia
- `Provincia.java`, `Canton.java`, `Parroquia.java`, `JRV.java` — Hierarchy entities
- `ListarGeografiaUseCase.java` — Tree structure builder (@CacheResult)
- `GeografiaResource.java` — `GET /api/v1/master/geografia`

### Dashboard
- `ObtenerDashboardUseCase.java` — Coordinator metrics (@CacheResult)
- `DashboardResource.java` — `GET /api/v1/coordinator/dashboard`

### Electoral (Config)
- `Cargo.java`, `Partido.java`, `Candidato.java` — Configuration entities
- `ListarCargosUseCase.java` — `GET /api/v1/configuracion/cargos` (@CacheResult)
- `ListarPartidosUseCase.java` — `GET /api/v1/configuracion/partidos` (@CacheResult)
- `ConfigResource.java` — Configuration endpoints

### Audit Log
- `AuditLog.java` — Audit entity (Row-Level Security)
- `AuditLogService.java` — Record all mutations centrally

## REST Endpoints Structure

```
/api/v1/

├── auth/
│   └── login                           POST (public)
│
├── admin/
│   └── cache/clear                     GET (requires cache:clear | MASTER)
│
├── configuracion/                      GET (requires configuracion:read)
│   ├── cargos                          @CacheResult(cacheName="cargos")
│   ├── partidos                        @CacheResult(cacheName="partidos")
│   └── candidatos                      @CacheResult(cacheName="candidatos")
│
├── master/
│   └── geografia                       GET (requires master:read)
│                                       @CacheResult(cacheName="geografia")
│
├── coordinator/
│   ├── dashboard                       GET @CacheResult(cacheName="dashboard")
│   ├── actas                           GET/POST/PUT/DELETE
│   └── ...
│
├── observer/
│   ├── actas                           GET (filtered)
│   ├── jrvs                            GET
│   └── ...
│
└── legal/
    └── acuerdos                        GET @CacheResult(cacheName="acuerdos")
```

## Security Model

- **Authentication:** JWT (issued by `LoginResource`)
- **Authorization:** `Autorizador.exigir(String... permisos)` per endpoint
- **Roles:** MASTER, COORDINATOR, ADMIN, OBSERVER
- **Audit Trail:** All mutations logged to `audit_log` via `AuditLogService`
- **Row-Level Security:** PostgreSQL RLS policy on audit_log (users see only their scope)

## Database Schema (Key Tables)

| Table | Rows | Purpose |
|-------|------|---------|
| `usuario` | ~100 | Users (email, name, role, password hash) |
| `rol` | 4 | MASTER, COORDINATOR, ADMIN, OBSERVER |
| `rol_permiso` | ~20 | Permission assignments per role |
| `provincia` | 24 | Ecuador provinces |
| `canton` | 221 | Cantons per province |
| `parroquia` | 1,207 | Parishes per canton |
| `jrv` | ~36,000 | Voting centers (JRV) per parish |
| `acta` | Variable | Electoral acts submitted by coordinators/observers |
| `evidencia` | Variable | Photos/documents attached to acts |
| `audit_log` | Variable | All mutations with RLS protection |

## To Modify

**Backend structure is stable.** When modifying:

1. **New domain entity?** → Create `{domain}/{domain}/domain/{Entity}.java`
2. **New use case?** → Create `{domain}/application/{UseCase}.java` (add `@Transactional`, `@CacheResult` if needed)
3. **New REST endpoint?** → Create `{domain}/web/{Resource}.java` with `@Path("/api/v1/...")`
4. **Cache invalidation?** → Extend `AdminCacheResource.java`
5. **Audit changes?** → Use `AuditLogService.record(...)` in use case
6. **Authorization?** → Call `autorizador.exigir(...)` in Resource before delegating to use case

---

## For Agents

Use this structure to:
- **Locate files:** Domain modular (auth → Usuario, Rol; acta → Acta; etc.)
- **Understand dependencies:** REST → UseCase → Entity → Repository → DB
- **Modify safely:** Each domain is isolated; changes propagate through layers
- **Cache decisions:** Check if use case already has `@CacheResult`; if adding new cached data, add cache name to `application.properties` and `ListarGeografiaUseCase`
