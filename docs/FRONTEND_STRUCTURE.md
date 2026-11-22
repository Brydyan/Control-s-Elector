# Frontend Structure — Control Electoral App (Angular 18 + RxJS)

Generated: 2026-11-21

## Module Architecture

```
frontend/app/src/

├── app/
│   ├── app.routes.ts                 Main router (lazy-loaded features)
│   ├── app.component.ts              Root component (<router-outlet>)
│   │
│   ├── core/                         Singleton services (once per app)
│   │   ├── auth/
│   │   │   ├── auth.service.ts       JWT token management
│   │   │   ├── auth.interceptor.ts   Add token to all requests
│   │   │   └── auth.guard.ts         Route protection
│   │   │
│   │   ├── http/
│   │   │   ├── http.service.ts       Wrapper around HttpClient
│   │   │   └── error.interceptor.ts  Global error handling
│   │   │
│   │   ├── cache/
│   │   │   └── cache.service.ts      RxJS shareReplay() management
│   │   │                              (cargos$, partidos$, etc.)
│   │   │
│   │   └── models/
│   │       └── *.ts                  Domain models (Usuario, Acta, etc.)
│   │
│   ├── layout/                       Layout components (shell)
│   │   ├── navbar/
│   │   ├── sidebar/
│   │   └── layout.component.ts       Main <app-layout> wrapper
│   │
│   ├── shared/                       Reusable components & utilities
│   │   ├── components/
│   │   │   ├── modal/                Generic modal wrapper
│   │   │   ├── toast/                Notifications
│   │   │   ├── loading-spinner/      Loading indicator
│   │   │   └── confirmation-dialog/  Confirm before delete
│   │   │
│   │   ├── directives/
│   │   │   └── *.ts                  Structural directives
│   │   │
│   │   └── pipes/
│   │       └── *.ts                  Transform pipes (date, currency, etc.)
│   │
│   └── features/                     Feature modules (lazy-loaded)
│       │
│       ├── auth/
│       │   ├── login-page/           Login form
│       │   ├── login.service.ts      Backend /auth/login call
│       │   └── auth.routes.ts        /auth/login route
│       │
│       ├── legal/
│       │   ├── terms-modal/          Legal agreements modal
│       │   ├── legal.service.ts      GET /api/v1/legal/acuerdos (@cache)
│       │   └── legal.routes.ts       /legal route
│       │
│       ├── coordinator/              [COORDINATOR role]
│       │   ├── dashboard/            Coordinator dashboard (home page)
│       │   │   └── dashboard.component.ts
│       │   │       ├── @inject CacheService → cargos$, partidos$
│       │   │       ├── @inject DashboardService
│       │   │       └── shareReplay(1) on dashboard data
│       │   │
│       │   ├── actas/                Electoral acts management
│       │   │   ├── actas-list/       List all acts
│       │   │   ├── acta-create/      Create new act
│       │   │   ├── acta-view/        View/edit act details
│       │   │   ├── acta.service.ts   Backend calls
│       │   │   └── actas.routes.ts   /coordinator/actas routes
│       │   │
│       │   ├── evidencia/            Evidence upload
│       │   │   ├── evidencia-upload/ File upload modal
│       │   │   ├── evidencia.service.ts
│       │   │   └── evidencia.routes.ts
│       │   │
│       │   └── coordinator.routes.ts Coordinator feature routes
│       │
│       ├── observer/                 [OBSERVER role]
│       │   ├── mis-actas/            Observer's own acts
│       │   │   ├── mis-actas-list/
│       │   │   ├── acta-view/
│       │   │   └── mis-actas.service.ts
│       │   │
│       │   ├── jrvs/                 Available JRVs (voting centers)
│       │   │   ├── jrv-list/         Geographic filter + select JRV
│       │   │   ├── jrv-detail/       JRV details modal
│       │   │   └── jrv.service.ts
│       │   │
│       │   └── observer.routes.ts    Observer feature routes
│       │
│       ├── master/                   [MASTER role]
│       │   ├── geografia/            Geographic hierarchy manager
│       │   │   ├── geografia-tree/   Province → canton → parish → JRV tree
│       │   │   ├── geografia.service.ts GET /api/v1/master/geografia (@cache)
│       │   │   └── geografia.routes.ts
│       │   │
│       │   ├── cache-admin/          Cache management
│       │   │   ├── cache-clear-form/ Clear cache button
│       │   │   ├── cache-admin.service.ts GET /api/v1/admin/cache/clear
│       │   │   └── cache-admin.routes.ts
│       │   │
│       │   └── master.routes.ts      Master feature routes
│       │
│       ├── admin/                    [ADMIN role]
│       │   ├── user-management/      Manage users
│       │   ├── permissions/          Assign permissions
│       │   ├── admin.service.ts
│       │   └── admin.routes.ts
│       │
│       ├── dashboard/                [COORDINATOR dashboard only]
│       │   ├── dashboard-container/
│       │   ├── dashboard.service.ts  GET /api/v1/coordinator/dashboard (@cache)
│       │   └── dashboard.routes.ts
│       │
│       └── placeholder/              Fallback page (not implemented yet)
│
├── theme/                            Design system
│   ├── variables.scss                Global colors, fonts
│   ├── mixins.scss                   Common SCSS utilities
│   ├── responsive.scss               Breakpoints, media queries
│   └── animations.scss               Transitions, keyframes
│
├── environments/
│   ├── environment.ts                Dev: http://localhost:3000
│   └── environment.prod.ts           Prod: https://api.example.com
│
├── assets/
│   ├── icons/
│   ├── images/
│   └── i18n/                         Internationalization (if used)
│
└── main.ts                           Bootstrap
```

## Component Hierarchy

```
AppComponent (root)
├── <router-outlet>                   Routes switch here
│   │
│   ├── Layout Shell
│   │   ├── Navbar
│   │   ├── Sidebar (role-based nav)
│   │   └── <router-outlet>           Page content
│   │       └── Feature Pages:
│   │           ├── CoordinatorDashboard
│   │           │   ├── MetricsCard (cargos$, partidos$)
│   │           │   └── ActasList (data-bound via service)
│   │           │
│   │           ├── CoordinatorActasList
│   │           │   ├── ActaTable (track by id)
│   │           │   └── ActaRow (edit/delete actions)
│   │           │
│   │           ├── ObserverMisActas
│   │           │   ├── FilterBar (by JRV, date, status)
│   │           │   └── ActaTable
│   │           │
│   │           ├── ObserverJrvList
│   │           │   ├── GeografiaTree (lazy-load from cache)
│   │           │   └── JrvDetails (modal)
│   │           │
│   │           ├── MasterGeografia
│   │           │   └── GeografiaTree (full hierarchy)
│   │           │
│   │           └── LoginPage (no layout)
│   │
│   └── SharedComponents:
│       ├── TermsModal (above layout)
│       ├── Toast Notifications
│       ├── ConfirmationDialog
│       └── LoadingSpinner
```

## RxJS Caching Pattern

**Central cache service** (`core/cache/cache.service.ts`):

```typescript
export class CacheService {
  // Readonly observables (share across app)
  cargos$ = this.http.get('/api/v1/configuracion/cargos')
    .pipe(shareReplay(1));
  
  partidos$ = this.http.get('/api/v1/configuracion/partidos')
    .pipe(shareReplay(1));
  
  candidatos$ = this.http.get('/api/v1/configuracion/candidatos')
    .pipe(shareReplay(1));
  
  geografia$ = this.http.get('/api/v1/master/geografia')
    .pipe(shareReplay(1));
  
  acuerdos$ = this.http.get('/api/v1/legal/acuerdos')
    .pipe(shareReplay(1));
}
```

**Usage in components:**

```typescript
export class DashboardComponent {
  constructor(private cache: CacheService) {}
  
  cargos$ = this.cache.cargos$;
  partidos$ = this.cache.partidos$;
  
  ngOnInit() {
    // Auto-subscribe via async pipe
    // <div>{{ (cargos$ | async)?.length }}</div>
  }
}
```

## Service Layer

| Service | Purpose | Caches |
|---------|---------|--------|
| `AuthService` | Login, JWT management | session token only |
| `CacheService` | Static data caching | cargos, partidos, candidatos, geografia, acuerdos |
| `HttpService` | Backend calls wrapper | — (delegates to CacheService) |
| `ActaService` | Acta CRUD | — (mutable data) |
| `EvidenciaService` | Evidence upload/download | — |
| `JrvService` | JRV list/details | — |
| `DashboardService` | Dashboard metrics | — (or @CacheResult via backend) |
| `GeografiaService` | Geographic hierarchy | delegates to CacheService.geografia$ |

## Data Flow

```
User Action (click, form submit)
    ↓
Component Handler (e.g., onSaveActa())
    ↓
Service Call (this.actaService.create(data))
    ↓
HttpService.post('/api/v1/...')
    ↓
Backend (JPA + @Transactional)
    ↓
Response (JSON)
    ↓
Component receives data
    ↓
Render (async pipe)

--- Cache Hits (no server call) ---
Cached Observable (shareReplay(1))
    ↓
subscribe() → cached value emitted immediately
    ↓
Component receives data
    ↓
Render (async pipe)
```

## State Management

**No global Redux/NgRx.** Instead:

1. **Service observables** (CacheService, ActaService, etc.)
2. **Component-local state** (form inputs, UI toggles)
3. **Route parameters** (URL carries identity: `/coordinator/actas/:id`)
4. **LocalStorage** (optional: form persistence across reload — T-AUTH-INV-01)

**Principle:** Keep state close to where it's used. Only promote to service if shared by 2+ components.

## Routing Structure

```
/                          Root
├── /auth/login            Public (redirect if authenticated)
├── /legal/acuerdos         Modal overlay (always available)
│
├── /coordinator            Protected (requires COORDINATOR role)
│   ├── /dashboard          Coordinator dashboard (home)
│   ├── /actas              List acts
│   ├── /actas/:id/edit     Edit act
│   ├── /evidencia/upload   Upload evidence (modal)
│   └── /cache-admin        Clear cache (MASTER only)
│
├── /observer               Protected (requires OBSERVER role)
│   ├── /mis-actas          List own acts
│   ├── /mis-actas/:id/view View act
│   └── /jrvs               List voting centers
│
├── /master                 Protected (requires MASTER role)
│   ├── /geografia          Geographic hierarchy editor
│   └── /cache-admin        Cache management (overlay)
│
├── /admin                  Protected (requires ADMIN role)
│   ├── /users              User management
│   ├── /permissions        Permission assignments
│   └── /reports            System reports
│
└── /**                     404 (NotFound page)
```

## Key Files by Feature

### Auth
- `LoginPage` → `auth.service.ts` → `POST /auth/login`
- `AuthGuard` → check JWT, redirect if expired
- `AuthInterceptor` → inject `Authorization: Bearer <token>`

### Coordinator
- `Dashboard` → `CacheService` (cargos$, partidos$) + `DashboardService`
- `ActasList` → `ActaService.list()` → `GET /api/v1/coordinator/actas`
- `ActaCreate` → `ActaService.create()` → `POST /api/v1/coordinator/actas`

### Observer
- `MisActas` → `ActaService.listObserver()` → filtered by user
- `JrvList` → `CacheService.geografia$` → tree picker
- Storage: `localStorage` for persistence (T-AUTH-INV-01)

### Master
- `GeografiaTree` → `CacheService.geografia$` → tree display (10m cache via backend)
- `CacheClearForm` → `CacheAdminService` → `GET /api/v1/admin/cache/clear`

### Legal
- `TermsModal` → `CacheService.acuerdos$` → show on first login only
- Stored in sessionStorage (don't show again this session)

## To Modify

**Component-centric structure.** When modifying:

1. **New page?** → Create `features/{feature}/{page-name}/` directory
2. **New cached data?** → Add observable to `CacheService`, expose via `cargos$`, etc.
3. **New form?** → Create `{page}/` with `.component.ts`, `.component.html`, `.component.scss`
4. **Need backend data?** → Inject `CacheService` or create new `{feature}.service.ts`
5. **Route protection?** → Use `AuthGuard` on route definition
6. **Cache invalidation?** → Call backend `GET /api/v1/admin/cache/clear` (admin-only)

## Styling

**SCSS modules** (not global):

- `theme/variables.scss` — Colors, fonts, spacing
- `theme/responsive.scss` — Breakpoints (@media)
- `theme/animations.scss` — Keyframes, transitions (use `transitions-dev` skill for modern patterns)
- Each component: `.component.scss` (view-encapsulated)

**Modern Web Guidance:** Use `/modern-web-guidance <query>` when styling:
- Form validation → `/modern-web-guidance form validation`
- Modal animations → `/modern-web-guidance dialog animation`
- Tooltip positioning → `/modern-web-guidance tooltip positioning`

## Testing

| Level | Location | Tool |
|-------|----------|------|
| **Unit** | `{feature}/**/*.spec.ts` | Jasmine + Karma (or Vitest) |
| **Integration** | `{feature}/**/*.api.spec.ts` | Mock HttpClient |
| **E2E** | `e2e/*.spec.ts` (Cypress/Playwright) | Full app + real backend |

Run:

```bash
pnpm test                  # All unit tests (watch)
pnpm test:coverage        # Coverage report
pnpm e2e                  # E2E tests
```

---

## For Agents

Use this structure to:
- **Locate components:** Features are role-based (coordinator/, observer/, master/); pages nest one level deep
- **Understand data flow:** Service → HTTP → Backend; CacheService for static data (shareReplay)
- **Cache decisions:** Check if data already cached in `CacheService`; if adding new cached endpoint, add observable + expose method
- **Route navigation:** Role-based lazy loading via `app.routes.ts`; each feature has its own `.routes.ts` for sub-paths
- **Style consistently:** Use `theme/` variables; check `transitions-dev` skill for animations before coding CSS
