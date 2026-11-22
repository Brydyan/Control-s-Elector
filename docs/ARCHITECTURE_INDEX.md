# Control Electoral — Architecture Index

**Purpose:** Centralized navigation for agents, developers, and QA to understand project structure and locate files for modification.

Generated: 2026-11-21

---

## 📚 Structure Guides

### Backend Architecture
👉 **[BACKEND_STRUCTURE.md](./BACKEND_STRUCTURE.md)**

Modular Java/Quarkus structure:
- 10 domains: auth, acta, evidencia, geografia, dashboard, electoral, auditlog, legal, monitoring, shared
- Layered: REST → UseCase → Entity → Repository → DB
- Caching: `@CacheResult` on 6 key use cases (10m TTL, Caffeine)
- Cache invalidation: `GET /api/v1/admin/cache/clear` (MASTER only)

**Quick links:**
- Modify entity? → `{domain}/domain/{Entity}.java`
- Add endpoint? → `{domain}/web/{Resource}.java` + `app.routes.ts`
- Cache new data? → Add `@CacheResult(cacheName="xyz")` to use case + `application.properties`
- Audit changes? → Call `AuditLogService.record(...)` in use case

---

### Frontend Architecture
👉 **[FRONTEND_STRUCTURE.md](./FRONTEND_STRUCTURE.md)**

Role-based Angular 18 + RxJS structure:
- 8 features: auth, legal, coordinator, observer, master, admin, dashboard, placeholder
- Lazy-loaded routes per role
- Caching: RxJS `shareReplay(1)` for static data (cargos, partidos, geografia, acuerdos)
- State: Service observables + component-local + route params (no Redux)

**Quick links:**
- Modify page? → `features/{role}/{page-name}/`
- Cache new data? → Add observable to `CacheService` or create new `{feature}.service.ts`
- Add route? → Update `{feature}.routes.ts` or root `app.routes.ts`
- Style component? → Check `/modern-web-guidance <query>` before writing CSS

---

## 🗂️ File Navigation by Task

### Common Tasks

| Task | Backend | Frontend |
|------|---------|----------|
| **Add new entity** | Create `{domain}/domain/Entity.java` + JPA annotations | Create model in `core/models/` |
| **Add CRUD endpoint** | Create `{domain}/web/Resource.java` + `@Path` routes | Create service + components in `features/{role}/` |
| **Cache data** | `@CacheResult(cacheName="x")` on use case | `shareReplay(1)` in `CacheService` |
| **Invalidate cache** | Extend `AdminCacheResource.java` | Call `CacheAdminService.clearCache()` |
| **Add permission** | Create `RolPermiso` + row in `rol_permiso` table | Add check in `AuthGuard` or `Autorizador` |
| **Modify form** | Validate in use case + tests | Modify component + template + RxJS validation |
| **Add page** | — | Create in `features/{role}/` with routing |
| **Fix bug** | Search domain module + tests | Search feature module + tests |

---

## 🎯 By Role

### COORDINATOR
- **Backend:** Create/list acts (`/coordinator/actas`), upload evidence, see dashboard
- **Frontend:** `features/coordinator/` → dashboard, actas-list, acta-create, evidencia-upload
- **Files:** `CoordinatorDashboard.component`, `ActaService`, `ActaResource.java`

### OBSERVER
- **Backend:** List own acts (`/observer/actas`), list JRVs (`/observer/jrvs`)
- **Frontend:** `features/observer/` → mis-actas, jrvs
- **Files:** `MisActasComponent`, `JrvService`, `ActaResource.java` (filtered)

### MASTER
- **Backend:** View full geography (`/master/geografia`), clear caches (`/admin/cache/clear`)
- **Frontend:** `features/master/` → geografia-tree, cache-admin
- **Files:** `GeografiaTreeComponent`, `CacheAdminService`, `AdminCacheResource.java`

### ADMIN
- **Backend:** Manage users, permissions
- **Frontend:** `features/admin/` → user-management, permissions
- **Files:** `AdminService`, `AdminResource.java` (not yet implemented)

---

## 🔄 Data Flow Examples

### Create an Electoral Act (Coordinator)

```
1. User clicks "New Act" button
   └─ CoordinatorActaCreate component opens

2. Component calls ActaService.create(formData)
   └─ POST /api/v1/coordinator/actas

3. Backend:
   └─ ActaResource.create(@Valid ActaRequest)
      └─ CrearActaUseCase.execute(request)
         └─ Entity: Acta.java (domain)
         └─ Repository: ActaRepository.persist()
         └─ AuditLogService.record("CREAR_ACTA", ...)
         └─ Return: ActaResponse + 201

4. Frontend receives response
   └─ Store in ActaService observable
   └─ Toast notification: "Act created"
   └─ Redirect to /coordinator/actas (auto-reload)
```

### Load Static Configuration (Coordinator Dashboard)

```
1. User navigates to /coordinator/dashboard
   └─ CoordinatorDashboard component initializes

2. Component injects CacheService
   └─ Subscribes: cargos$ = CacheService.cargos$

3. First subscription:
   └─ HTTP call → GET /api/v1/configuracion/cargos
   └─ Backend: ListarCargosUseCase.execute()
      └─ @CacheResult(cacheName="cargos")
      └─ Caffeine caches (10m TTL)
      └─ Return: JSON list
   └─ Frontend caches via shareReplay(1)
   └─ Component displays cargos

4. User navigates away, back to dashboard
   └─ Second subscription to cargos$
   └─ No HTTP call (RxJS shareReplay returns cached value)
   └─ Instant render
```

### Invalidate All Caches (MASTER)

```
1. User (MASTER role) clicks "Clear Cache"
   └─ CacheClearForm component

2. Component calls CacheAdminService.clearCache()
   └─ GET /api/v1/admin/cache/clear
   └─ Authorization: requires "cache:clear" or MASTER

3. Backend:
   └─ AdminCacheResource.clearCache()
      └─ Autorizador.exigir("cache:clear", "MASTER")
      └─ CacheManager.invalidateAll() (Caffeine)
      └─ AuditLogService.record("LIMPIAR_CACHE", ...)
      └─ Return: { success: true, clearedCaches: [...] }

4. Frontend:
   └─ Receives response
   └─ Toast: "Caches cleared: cargos, partidos, ..."
   └─ OPTIONAL: Reload page or trigger CacheService reset
   └─ Next data request → fresh from backend
```

---

## 📡 API Endpoints

**All endpoints under `/api/v1/`**

### Public (no auth)
- `POST /auth/login` — Issue JWT token

### Configuration (caching enabled)
- `GET /configuracion/cargos` — Positions (@cache 10m)
- `GET /configuracion/partidos` — Parties (@cache 10m)
- `GET /configuracion/candidatos` — Candidates (@cache 10m)
- `GET /legal/acuerdos` — Legal agreements (@cache 10m)

### Geography (caching enabled)
- `GET /master/geografia` — Full tree (provinces → cantons → parishes → JRVs) (@cache 10m)

### Coordinator
- `GET /coordinator/actas` — List acts
- `POST /coordinator/actas` — Create act
- `PUT /coordinator/actas/{id}` — Update act
- `DELETE /coordinator/actas/{id}` — Delete act
- `GET /coordinator/dashboard` — Dashboard metrics (@cache 10m)

### Observer
- `GET /observer/actas` — List own acts (filtered by role)
- `GET /observer/jrvs` — List available JRVs

### Admin
- `GET /admin/cache/clear` — Clear all caches (or ?cacheName=x for specific)
  - Requires: `cache:clear` permission or `MASTER` role
  - Query param: `?cacheName=cargos` (optional; default = all)

---

## 🛡️ Security Model

| Layer | Mechanism | Location |
|-------|-----------|----------|
| **Authentication** | JWT token (issued by `/auth/login`) | `AuthService.ts` (frontend), `LoginResource.java` (backend) |
| **Route Protection** | `AuthGuard` checks token + role | `app.routes.ts` (frontend), `app.routes.ts` (backend) |
| **Authorization** | `Autorizador.exigir(...)` per endpoint | `AdminCacheResource.java`, etc. (backend) |
| **Audit Trail** | All mutations logged to `audit_log` table | `AuditLogService.record()` (backend) |
| **Row-Level Security** | PostgreSQL RLS policy on audit_log | Database level (users see only their scope) |

**Roles:** MASTER, COORDINATOR, ADMIN, OBSERVER

---

## 🧪 Testing

### Backend
```bash
mvn test                       # All unit tests
mvn test -Dtest=ActaResourceTest  # Single test class
mvn verify                     # Run + coverage (JaCoCo)
```

Coverage: Min 70% required. High-risk: auth, acta, evidencia domains.

### Frontend
```bash
pnpm test                      # All unit tests (watch)
pnpm test:coverage             # Coverage report
pnpm e2e                       # E2E tests (Cypress/Playwright)
pnpm test:watch=false          # Single run (CI mode)
```

Coverage: Min 50% required. Focus on: auth guards, service calls, state mutations.

---

## 🚀 For Agents

When implementing a task, consult:

1. **Understand scope** → Which backend domain? Which frontend feature?
   - Use `BACKEND_STRUCTURE.md` and `FRONTEND_STRUCTURE.md`

2. **Locate files** → Modular structure makes this fast
   - Backend: `{domain}/{layer}/{file}.java`
   - Frontend: `features/{role}/{page}/{file}.ts`

3. **Plan dependencies** → Data flow from endpoint to component
   - Backend: REST → UseCase → Entity → Repository → DB
   - Frontend: Component → Service → HTTP → Backend

4. **Cache decisions** → Check if data already cached
   - Backend: Search use case for `@CacheResult`
   - Frontend: Check `CacheService` for existing observable

5. **Test requirements** → Write tests per spec, verify coverage
   - Backend: Unit test use cases + integration tests for cache
   - Frontend: Unit test services + E2E for user flows

6. **Review before commit** → Ask for `control-electoral-review` skill to validate architecture compliance

---

## 📖 Related Docs

- [T-FE-15 Caching Strategy](../openspec/changes/back/2026-11-19-caching-strategy/proposal.md) — Full SDD for caching implementation
- [Application Configuration](./CONFIG_GUIDE.md) — Environment variables, database setup, Quarkus config
- [Database Schema](./DATABASE_SCHEMA.md) — Table relationships, constraints, RLS policies (if exists)
- [API Contract](./API_CONTRACT.md) — Request/response schemas for all endpoints (if exists)

---

Generated for agents to understand project structure quickly. Last updated: **2026-11-21**
