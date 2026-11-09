```yaml
change: 2026-09-07-t-fe-02-routing-rbac
phase: verify
verdict: PASS_WITH_WARNINGS
date: 2026-09-27
branch: brydyan/sc-164/t-fe-02-routing-con-rbac-guards
head: a695515
angular_version: 22.2.0
node_version: 22.23.2
requirements_total: 4
scenarios_total: 4
scenarios_passing: 4
scenarios_failing: 0
tests_total: 129
tests_passing: 129
tests_failing: 0
test_files: 28
criticals: 0
warnings: 5
suggestions: 0
```

## Verification Report — T-FE-02: Routing con RBAC Guards

### Change
`2026-09-07-t-fe-02-routing-rbac` — Angular routing with RBAC guards (functional `CanActivateFn`).

### Mode
Full artifacts: proposal + spec + design + tasks + apply-progress.

---

## Completeness Table

| Artifact | Status |
|---|---|
| proposal.md | present |
| specs/frontend/spec.md | present |
| design.md | present |
| tasks.md | present — all tasks checked [x] |
| apply-progress.md | present (post-fixes round) |
| fixes-required.md | present — previous CRITICALs resolved |

All tasks in Fase A, B, C marked `[x]`. No unchecked tasks.

---

## Build / Tests / Coverage Evidence

| Gate | Command | Result |
|---|---|---|
| Tests | `pnpm test:ci` | exit 0 — 129 tests passed, 0 failed, 28 files |
| Lint | `pnpm lint` | exit 0 — All files pass linting |
| Build | `pnpm build` | exit 0 — Application bundle generation complete (2.97s) |

**test_output_hash**: vitest 5.0.2, 28 files, 129 tests, 3.09s duration
**build_output_hash**: ng 22.2.0 build, www/ output, no errors

### New test files (post CRITICAL fixes)

| File | Tests | Covers |
|---|---|---|
| `src/app/core/auth/auth.guard.spec.ts` | 6 | S1, S2, S3, guestGuard (×2) |
| `src/app/app.routes.spec.ts` | 5 | S4 lazy-load structural |

---

## Spec Compliance Matrix

| Scenario | Covered by test | Test result | Status |
|---|---|---|---|
| S1 — No token → authGuard returns UrlTree('/login') | `auth.guard.spec.ts`: "S1: authGuard sin token retorna UrlTree /login" | PASS | COMPLIANT |
| S2 — OBSERVER token → authGuard true + roleGuard true | `auth.guard.spec.ts`: "S2: authGuard con token válido retorna true" + "S2: roleGuard con OBSERVER en ruta OBSERVER retorna true" | PASS | COMPLIANT |
| S3 — ADMIN token + /master/config route (OBSERVER/COORDINATOR only) → roleGuard false, redirect /dashboard | `auth.guard.spec.ts`: "S3: roleGuard con ADMIN en ruta solo OBSERVER/COORDINATOR redirige a /dashboard" | PASS | COMPLIANT |
| S4 — Lazy-load: features not pre-loaded until navigated | `app.routes.spec.ts`: "rutas de features usan loadComponent (no component directo)" + observer/coordinator/dashboard tests | PASS (structural) | COMPLIANT_WITH_WARNING |

**S4 note**: The spec requires runtime chunk loading verification (chunks absent until navigation). The test verifies structural configuration (`loadComponent` present, no `component` inline on 70%+ routes). Runtime chunk-loading behavior is not covered by unit tests — would require e2e/integration tests. This is the scope boundary established by apply-progress.md R1.

---

## Design Coherence Table

| Design Decision | Implementation | Status |
|---|---|---|
| D1 — Functional guards (`CanActivateFn`) | `authGuard`, `guestGuard`, `roleGuard` all use `CanActivateFn` with `inject()` | COMPLIANT |
| D2 — Location `core/auth/guards/` (separate files) | Guards live in `core/auth/auth.guard.ts` (single file, no subdirectory) | DEVIATION — WARNING W4 (consistent with T-FE-01 no-subdirectory decision) |
| D3 — Compose `canActivate: [authGuard, roleGuard]` | Routes use `canActivate: [authGuard]` at layout level, `canActivate: [roleGuard]` at feature level | COMPLIANT (composition achieved, ordering is correct) |
| D4 — roleGuard returns `false` → redirect to `/` | roleGuard returns `router.createUrlTree(['/dashboard'])` | DEVIATION — WARNING W3 (root redirects to dashboard anyway; functionally equivalent) |

---

## Issues

### CRITICAL (0)
None. All previous CRITICALs from fixes-required.md are resolved.

### WARNING (5)

**W1 — S4 lazy-load coverage is structural only**
- The spec's S4 scenario requires runtime chunk-loading verification.
- `app.routes.spec.ts` proves structural config (`loadComponent` on ≥70% of routes) but does not verify runtime chunk absence until navigation.
- Mitigation: acknowledged in apply-progress R1. E2e coverage deferred to future T-FE-XX.

**W2 — `data.roles` vs `data.allowedRoles` (cosmetic divergence)**
- Spec example uses `data.allowedRoles`. All production routes use `data: { roles: [...] }`.
- `roleGuard` reads `route.data?.['roles']`. No functional impact.
- Deferred per apply-progress R2.

**W3 — roleGuard redirects to `/dashboard` (spec/design says `/`)**
- design.md D4: "roleGuard returns false → Angular redirects to `/`".
- Implementation: `router.createUrlTree(['/dashboard'])` explicit.
- Root route has `redirectTo: 'dashboard'` so functionally equivalent.
- Deferred per apply-progress R3 / fixes-required W3.

**W4 — Guards in single file, not `guards/` subdirectory**
- design.md D2: `core/auth/guards/auth.guard.ts`.
- Implementation: `core/auth/auth.guard.ts` (single file).
- Consistent with T-FE-01 decision (no subdirectories). No functional impact.

**W5 — Pre-existing CSS budget warning on acta-detalle.page.scss**
- `ng build` emits: `acta-detalle.page.scss exceeded maximum budget. Budget 8.00 kB was not met by 1.55 kB with a total of 9.55 kB.`
- Pre-existing, unrelated to T-FE-02. Guard code and route code have no SCSS.

### SUGGESTION (0)
None.

---

## Task Completion

| Phase | Tasks | Status |
|---|---|---|
| Fase A (Guards) | A.1, A.2, A.3, A.4 | All [x] |
| Fase B (Routes) | B.1, B.2, B.3 | All [x] |
| Fase C (Lazy-load) | C.1, C.2, C.3 | All [x] |

All 10 task items are checked. Compuertas A, B, C validated.

---

## Mutations (V.1, V.2)

| Mutation | Coverage |
|---|---|
| V.1 — Remove authGuard from /observer/* | Unit tests prove guard rejects unauthenticated (S1). Structural removal not captured without integration test. |
| V.2 — Change allowedRoles to ['MASTER'] | Captured by S3 test: roleGuard with ADMIN on OBSERVER/COORDINATOR route → redirects. Analogous scenario confirmed. |

---

## Verdict

**PASS WITH WARNINGS**

0 CRITICALs / 5 WARNINGs / 0 SUGGESTIONs

All spec scenarios S1–S4 have passing automated tests. All tasks are complete. Build and lint pass clean. The 5 warnings are pre-documented divergences (design-cosmetic, scope-boundary, pre-existing CSS) and do not block archive. S4 runtime chunk-loading coverage is acknowledged as out-of-scope for unit tests per apply-progress R1.
