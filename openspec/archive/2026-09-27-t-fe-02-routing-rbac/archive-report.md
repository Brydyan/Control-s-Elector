# Archive Report — T-FE-02: Routing con RBAC Guards

**Date Archived**: 2026-09-27  
**Change ID**: T-FE-02 (2026-09-07-t-fe-02-routing-rbac)  
**Branch**: brydyan/sc-164/t-fe-02-routing-con-rbac-guards (HEAD: a695515)  
**Archived Location**: `openspec/changes/archive/2026-09-27-t-fe-02-routing-rbac/`

---

## Executive Summary

T-FE-02 (Angular routing with functional RBAC guards) completed the full SDD cycle successfully. Implementation and verification achieved **PASS_WITH_WARNINGS** (0 CRITICALs, 5 non-blocking WARNINGs). All 10 implementation tasks are complete. Delta spec synced to main specs at `openspec/specs/frontend/spec.md`. Change folder moved to archive.

---

## SDD Cycle Completion

| Phase | Artifact | Status | Notes |
|-------|----------|--------|-------|
| Proposal | `proposal.md` | ✓ Present | Scope: guards, routes, lazy-load; Criteria: authGuard, roleGuard, 4 scenarios |
| Spec | `specs/frontend/spec.md` | ✓ Present → Synced to main | 4 scenarios (S1–S4), mutate tests (M1–M2), 3 guarantees (G1–G3) |
| Design | `design.md` | ✓ Present | D1–D4: functional guards, core/auth location, composition, redirect strategy |
| Tasks | `tasks.md` | ✓ Complete | Fase A (4 tasks), Fase B (3 tasks), Fase C (3 tasks) — all [x] checked |
| Apply | `apply-progress.md` | ✓ Complete | 7 sub-tasks (A.1–C.3), linting/build clean, 129 tests pass |
| Verify | `verify-report.md` | ✓ PASS_WITH_WARNINGS | 0 CRITICALs, 5 non-blocking WARNINGs, 4 scenarios passing |

### Final Implementation State

**Branch**: `brydyan/sc-164/t-fe-02-routing-con-rbac-guards`  
**HEAD Commit**: a695515  
**Tests**: 129 passing, 0 failing (28 test files)  
**Build**: ✓ ng build success (2.97s)  
**Lint**: ✓ pnpm lint clean  
**Angular Version**: 22.2.0  
**Node Version**: 22.23.2  

---

## Specs Merged

### Domain: Frontend

**Action**: New spec created and synced to main specs.

**Destination**: `openspec/specs/frontend/spec.md`

**Contents Synced**:
- **4 Scenarios (S1–S4)**
  - S1: No token → authGuard returns `/login`
  - S2: Valid token (OBSERVER) → both guards pass
  - S3: Wrong role (ADMIN on `/master/*`) → roleGuard rejects
  - S4: Lazy-load verification (chunks load on demand)
- **2 Mutation Tests (M1–M2)**
  - M1: Remove authGuard verification
  - M2: Change allowedRoles verification
- **3 Guarantees (G1–G3)**
  - G1: No token → no access to protected routes
  - G2: Wrong role → redirection
  - G3: Lazy-load works (features on-demand)

**Mechanical Copy Verification**: ✓ Byte-identical (60 lines, `diff -r` returned no differences)

---

## Archive Contents

```
openspec/changes/archive/2026-09-27-t-fe-02-routing-rbac/
├── proposal.md              (1.6 KB, 44 lines)
├── design.md                (1.2 KB, 26 lines)
├── tasks.md                 (2.6 KB, 67 lines) — all tasks [x]
├── apply-progress.md        (8.6 KB, 228 lines)
├── verify-report.md         (6.6 KB, 157 lines) — PASS_WITH_WARNINGS
├── fixes-required.md        (4.9 KB, 148 lines)
├── specs/
│   └── frontend/
│       └── spec.md          (60 lines, 4 scenarios + mutate + guarantees)
└── archive-report.md        (this file)
```

All artifacts present. All implementation tasks marked complete. No unchecked tasks.

---

## Task Completion Validation

Per **Task Completion Gate** (Section 4, `sdd-archive` SKILL.md):

| Phase | Tasks | Checked | Status |
|-------|-------|---------|--------|
| Fase A: Guards Funcionales | A.1, A.2, A.3, A.4 | All [x] | ✓ Complete |
| Fase B: Rutas Base | B.1, B.2, B.3 | All [x] | ✓ Complete |
| Fase C: Lazy-load Verificación | C.1, C.2, C.3 | All [x] | ✓ Complete |

**Gate Result**: ✓ PASS — All implementation tasks marked complete in `tasks.md`. No stale unchecked tasks. Archive may proceed.

---

## Verification State (Final Authority)

**Source of Truth**: `verify-report.md` dated 2026-09-27 (at verification time), plus explicit final-state facts from launch prompt (no stale snapshot claims reported).

| Aspect | Metric | Status |
|--------|--------|--------|
| **Verdict** | PASS_WITH_WARNINGS | ✓ |
| **CRITICALs** | 0 | ✓ Archive unblocked |
| **WARNINGs** | 5 (non-blocking) | ✓ Documented below |
| **SUGGESTIONs** | 0 | ✓ |
| **Test Coverage** | 129/129 passing | ✓ |
| **Build** | ng build success | ✓ |
| **Lint** | pnpm lint clean | ✓ |
| **Scenarios** | S1–S4 all passing | ✓ |
| **Mutations** | M1–M2 confirmed testable | ✓ |

### Warnings (Non-Blocking, Documented)

**W1 — S4 lazy-load coverage is structural only**
- Spec requires runtime chunk-loading verification (chunks absent until navigation).
- Test (`app.routes.spec.ts`) verifies structural config (`loadComponent` on ≥70% of routes).
- Runtime chunk-loading behavior deferred to e2e/integration tests (future scope).
- **Mitigation**: Acknowledged in `apply-progress.md` R1. Archive not blocked.

**W2 — `data.roles` vs `data.allowedRoles` (cosmetic divergence)**
- Spec example uses `data.allowedRoles`; production routes use `data: { roles: [...] }`.
- `roleGuard` reads `route.data?.['roles']`. No functional impact.
- **Mitigation**: Acknowledged in `apply-progress.md` R2. Deferred to future standardization.

**W3 — roleGuard redirects to `/dashboard` (spec says `/`)**
- Design.md D4 states "roleGuard returns false → Angular redirects to `/`".
- Implementation: `router.createUrlTree(['/dashboard'])` explicit.
- Root route has `redirectTo: 'dashboard'` so functionally equivalent.
- **Mitigation**: Acknowledged in `apply-progress.md` R3 and `fixes-required.md` W3.

**W4 — Guards in single file, not `guards/` subdirectory**
- Design.md D2 specifies `core/auth/guards/auth.guard.ts`.
- Implementation: `core/auth/auth.guard.ts` (single file in `core/auth/`).
- Consistent with T-FE-01 decision (no subdirectories). No functional impact.
- **Mitigation**: Accepted deviation, documented in verify-report.md.

**W5 — Pre-existing CSS budget warning on acta-detalle.page.scss**
- `ng build` emits: `acta-detalle.page.scss exceeded maximum budget. Budget 8.00 kB was not met by 1.55 kB with a total of 9.55 kB.`
- Pre-existing, unrelated to T-FE-02 scope (guard code and route code have no SCSS).
- **Mitigation**: Pre-existing issue, acknowledged in verify-report.md.

**All warnings are non-blocking and documented in original artifacts.** No CRITICAL issues block archive.

---

## Design Decisions Implemented

| Decision | Implementation | Status |
|----------|----------------|--------|
| D1 — Functional guards (`CanActivateFn`) | `authGuard`, `roleGuard` use functional syntax with `inject()` | ✓ Compliant |
| D2 — Location of guards | `core/auth/auth.guard.ts` (single file, consistent with T-FE-01) | ✓ Compliant (documented deviation) |
| D3 — Guard composition | Routes use `canActivate: [authGuard, roleGuard]` in order | ✓ Compliant |
| D4 — Redirect on auth failure | authGuard → `/login`; roleGuard → `/dashboard` | ✓ Compliant (documented deviation) |

---

## Spec Compliance

All 4 spec scenarios covered by automated tests:

| Scenario | Test File | Test Name | Status |
|----------|-----------|-----------|--------|
| S1 — No token → `/login` | `auth.guard.spec.ts` | "S1: authGuard sin token retorna UrlTree /login" | PASS |
| S2 — OBSERVER + authGuard/roleGuard | `auth.guard.spec.ts` | "S2: authGuard con token válido retorna true" + role test | PASS |
| S3 — Wrong role → redirect | `auth.guard.spec.ts` | "S3: roleGuard con ADMIN en ruta solo OBSERVER/COORDINATOR" | PASS |
| S4 — Lazy-load structural | `app.routes.spec.ts` | "rutas de features usan loadComponent (no component directo)" | PASS |

**Mutation Tests**:
- M1 (remove authGuard): Tested implicitly by S1 (authGuard rejection with no token).
- M2 (change allowedRoles): Tested by S3 (roleGuard with ADMIN on OBSERVER/COORDINATOR route).

---

## Implementation Hightlights

### Routing Guards Implemented

1. **`authGuard` (CanActivateFn)**
   - Checks if token exists in `TokenService`
   - Redirects to `/login` if absent
   - Returns `true` if valid token

2. **`roleGuard` (CanActivateFn)**
   - Injects `TokenService` to read user role
   - Reads `route.data?.['roles']` for allowed roles
   - Returns `true` if user role matches
   - Returns `/dashboard` (UrlTree) if role mismatch

3. **Route Configuration**
   - `app.routes.ts` defines base routes with guards
   - Feature routes use `loadChildren()` or `loadComponent()` for lazy-load
   - Each feature module declares internal routes

### Test Coverage

- **auth.guard.spec.ts** (6 tests): S1, S2, S3 scenarios
- **app.routes.spec.ts** (5 tests): S4 lazy-load structural verification
- Total: 129 tests passing, all scenarios green

### Lazy-Load Architecture

- Features not pre-bundled with main app
- Chunks loaded on first navigation to feature route
- Structural verification confirms `loadComponent`/`loadChildren` usage

---

## Git State at Archive Time

**Branch**: `brydyan/sc-164/t-fe-02-routing-con-rbac-guards`  
**HEAD**: a695515  
**Status**: Working directory clean (no uncommitted changes at time of apply)

**Commits in branch** (partial log):
- a695515 — Latest implementation commit (per verify-report)
- Earlier commits: guards implementation, route structure, lazy-load tests

**Note**: This branch remains open for potential further work. Archive does not delete the branch. Merge to `develop` or `main` is a separate decision under ordinary repository policy (not part of SDD archive phase).

---

## Known Open Items (Not Blocking Archive)

1. **E2E runtime chunk-loading tests** — S4 coverage is structural only. E2E/integration tests deferred to future task.
2. **CSS budget fix (acta-detalle.page.scss)** — Pre-existing, unrelated to T-FE-02 scope. Tracked separately.
3. **Route naming consistency** — `data.roles` vs `data.allowedRoles` standardization deferred.

---

## Artifact Quality Checklist

- [x] All tasks marked complete in `tasks.md`
- [x] No CRITICAL issues in `verify-report.md`
- [x] All 4 spec scenarios passing
- [x] 129/129 tests green
- [x] Build clean (`ng build` success)
- [x] Lint clean (`pnpm lint` success)
- [x] Delta spec synced to main specs (mechanical copy verified)
- [x] Change folder moved to archive (pre-move snapshot verified)
- [x] Archived folder byte-identical to pre-move snapshot
- [x] Source directory removed after archive move
- [x] Archive-report created

---

## Archival Evidence

### Spec Sync (Mechanical Copy)

```bash
# Delta spec location (pre-archive)
openspec/changes/front/2026-09-07-t-fe-02-routing-rbac/specs/frontend/spec.md

# Main spec destination (post-sync)
openspec/specs/frontend/spec.md

# Verification: diff -r returned empty (byte-identical)
# Command: diff openspec/changes/front/2026-09-07-t-fe-02-routing-rbac/specs/frontend/spec.md openspec/specs/frontend/spec.md
# Result: 0 (no differences, byte-identical copy confirmed)
```

### Archive Move (Mechanical Move)

```bash
# Source (pre-archive)
openspec/changes/front/2026-09-07-t-fe-02-routing-rbac

# Destination (post-archive)
openspec/changes/archive/2026-09-27-t-fe-02-routing-rbac

# Verification: diff -r between pre-move snapshot and archived folder returned empty
# Result: 0 (no differences, byte-identical archive confirmed)
# Source removed after move (verified absent)
```

---

## Transitions

**From this SDD cycle to next work**:
- Routing infrastructure (T-FE-02) complete and archived
- Next phases in frontend track:
  - **T-FE-03** (auth services): Implement real token persistence and refresh
  - **T-FE-04** (login UI): Login form component
  - **T-FE-05+** (feature UIs): Observer, coordinator, master, dashboard components

This change is ready for delivery per ordinary repository policy. The branch may be merged to `develop` or `main` as workflow dictates.

---

## Closure Statement

**T-FE-02: Routing con RBAC Guards** has completed the full SDD cycle:
- ✓ Planned (proposal)
- ✓ Specified (spec + 4 scenarios)
- ✓ Designed (4 decisions)
- ✓ Implemented (10 tasks, all complete, 129 tests pass)
- ✓ Verified (PASS_WITH_WARNINGS, 0 CRITICALs)
- ✓ Archived (specs synced, folder moved, audit trail complete)

**Status**: CLOSED, ready for delivery.
