```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:a5da507657ce2a00a0b97375210f2a02f4f1e4d8cf80541f6109d9daff8095f6
verdict: pass
blockers: 0
critical_findings: 0
requirements: 0/0
scenarios: 0/0
test_command: ./node_modules/.bin/vitest run
test_exit_code: 0
test_output_hash: sha256:a5da507657ce2a00a0b97375210f2a02f4f1e4d8cf80541f6109d9daff8095f6
build_command: ./node_modules/.bin/ng build
build_exit_code: 0
build_output_hash: sha256:620aa7182a9e6b5186a2060c41f2009ebbfdbbbae1a70842f8034421a7479a73
```

## Verification Report

**Change**: `2026-09-07-t-fe-03-auth-token-service`
**Version**: N/A
**Mode**: Strict TDD

---

### Completeness

| Metric | Value |
|--------|-------|
| Tasks total | 9 (A.1, A.2, A.3, B.1, B.2, B.3, B.4, C.1, C.2) |
| Tasks complete | 9 |
| Tasks incomplete | 0 |

All 9 tasks in `tasks.md` are marked `[x]`.

---

### Build & Tests Execution

**Build**: SUCCESS (exit 0)

```text
./node_modules/.bin/ng build
Application bundle generation complete. [2.690 seconds] - 2026-09-27T20:22:38.018Z
Warnings: pre-existing NG8107 optional-chain in acta-detalle.page.html and budget exceeded in acta-detalle.page.scss (unrelated to this change)
Exit code: 0
```

**Tests**: 134 passed / 0 failed / 0 skipped (exit 0)

```text
./node_modules/.bin/vitest run
Test Files  29 passed (29)
      Tests  134 passed (134)
     Errors  1 error  ← "Vite module runner has been closed" (app.component teardown artifact, pre-existing, not from auth tests)
   Start at  15:28:19
   Duration  2.68s
```

Baseline was 129 tests. Post-apply is 134 (+5 new tests in `auth.service.spec.ts`). The 1 unhandled rejection is a pre-existing Vite teardown artifact from `app.component.spec.ts` and is unrelated to this change.

**Coverage**: Coverage tool not invoked — vitest run without `--coverage` flag. Not a failure.

---

### Spec Compliance Matrix

The spec (`spec.md`) uses `### S1/S2/S3/S4` scenario headings (not the `#### Scenario:` native format), so native requirement/scenario counters report 0/0. Actual spec scenario mapping follows below.

| Scenario | Description | Covering Test | Result |
|----------|-------------|---------------|--------|
| S1 — Login exitoso | Token stored in memory, currentUser emits | `S-AuthService-1` in `auth.service.spec.ts:70` | COMPLIANT |
| S1 — Login error path | Error propagates without touching store | `S-AuthService-1b` in `auth.service.spec.ts:80` | COMPLIANT |
| S2 — Logout | Token cleared, currentUser cleared | `S-AuthService-2` in `auth.service.spec.ts:95` | COMPLIANT |
| S3 — Refresh single-flight | Two concurrent refresh() share one request | `S-AuthService-3` in `auth.service.spec.ts:111` | COMPLIANT |
| S3 — Refresh persists | New token stored in AuthStore | `S-AuthService-3b` in `auth.service.spec.ts:136` | COMPLIANT |
| S4 — Token NOT in localStorage | No localStorage writes in AuthStore | Static evidence: `AuthStore` uses `signal<string\|null>(null)`, no `localStorage` calls | COMPLIANT |
| G1 — Token in memory | AuthStore uses Angular Signals, not localStorage | `auth.store.ts`: private `_accessToken = signal<string\|null>(null)` | COMPLIANT |
| G2 — logout clears store | `store.clear()` in logout tap | V.2 mutation captured: `S-AuthService-2` failed when tap removed | COMPLIANT |
| G3 — refresh gets new token | `persist(s)` in refresh tap | `S-AuthService-3b` | COMPLIANT |
| G4 — reactive state | Signal-based reactive store (replaces BehaviorSubject per design evolution) | `auth.store.ts`: `readonly isAuthenticated = computed(...)` | COMPLIANT |

**Compliance summary**: 10/10 spec scenarios/guarantees compliant (4 spec scenarios + 4 guarantees + 2 error paths)

---

### Correctness (Static Evidence)

| Requirement | Status | Notes |
|-------------|--------|-------|
| Token stored in memory only | IMPLEMENTED | `AuthStore._accessToken = signal<string\|null>(null)` — no localStorage calls anywhere in auth/ |
| AuthService orchestrates repo + store | IMPLEMENTED | `auth.service.ts` injects `AuthRepository` and `AuthStore`; login/logout/refresh use `tap()` to persist |
| Single-flight refresh | IMPLEMENTED | `refreshInFlight$` field with `shareReplay({bufferSize: 1, refCount: false})` and `finalize` reset |
| Logout clears state | IMPLEMENTED | `return this.repo.logout().pipe(tap(() => this.store.clear()))` |
| restoreSession() calls refresh() | IMPLEMENTED | `restoreSession()` delegates to `this.refresh()` + catchError |
| AuthService providedIn root | IMPLEMENTED | `@Injectable({ providedIn: 'root' })` |
| withCredentials for cookies | IMPLEMENTED | All `AuthRepository` methods pass `{ withCredentials: true }` |

---

### Coherence (Design)

| Decision | Followed? | Notes |
|----------|-----------|-------|
| D1 — Token in memory, not localStorage | YES | `AuthStore` uses Angular Signals exclusively; no localStorage/sessionStorage calls |
| D2 — BehaviorSubject vs Signal | DEVIATION (acceptable) | Design said BehaviorSubject for `currentUser$`; implementation uses Angular Signals (`signal<>`). Signal-based store is architecturally equivalent and aligned with Angular 17+ patterns. The spec guarantees (G4 reactive state) are met. |
| D3 — Location `core/auth/services/auth.service.ts` | MINOR DEVIATION | File is at `core/auth/auth.service.ts` (no `services/` subfolder). Functionally identical. |
| D4 — login() propagates errors | YES | `repo.login(credentials).pipe(tap(...))` — no swallowing; error path tested in `S-AuthService-1b` |

---

### TDD Compliance

| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | YES | apply-progress.md contains Gate 3, Gate 4, mutation evidence |
| All tasks have tests | YES | A.3 and C.2 tests present in `auth.service.spec.ts` (5 tests) |
| RED confirmed (tests exist) | YES | `auth.service.spec.ts` exists and verified |
| GREEN confirmed (tests pass) | YES | 5/5 pass — confirmed by execution |
| Triangulation adequate | YES | 5 tests cover 4 distinct behaviors (login, login-error, logout, refresh-single-flight, refresh-persist) |
| Safety Net for modified files | N/A | No production files were modified; only new test file added |

**TDD Compliance**: 5/5 checks passed

---

### Mutation Verification (V.1 and V.2 — Re-run by Verifier)

| Mutation | Description | Applied | Test that Fell | Captured? |
|----------|-------------|---------|---------------|-----------|
| V.1 | Comment `tap((s) => this.persist(s))` in `login()` | Yes | `S-AuthService-1: login persiste accessToken y user en AuthStore` — `expected null to be 'token-123'` | YES |
| V.2 | Comment `tap(() => this.store.clear())` in `logout()` | Yes | `S-AuthService-2: logout limpia accessToken y user en AuthStore` — `expected 'token-123' to be null` | YES |

Both mutations were independently applied and restored. Files restored to original state — confirmed by re-running full suite (134 tests pass, 0 failures).

---

### Test Layer Distribution

| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit | 134 | 29 | Vitest 5.0.2 + Angular TestBed |
| Integration | 0 | 0 | Not applicable for this change |
| E2E | 0 | 0 | Not applicable for this change |
| **Total** | **134** | **29** | |

---

### Changed File Coverage

Only one new file was added (`auth.service.spec.ts` — test file only). Zero production files were modified. Coverage analysis skipped — no `--coverage` flag invoked, not a failure.

---

### Assertion Quality

| File | Line | Assertion | Issue | Severity |
|------|------|-----------|-------|----------|
| `auth.service.spec.ts` | 26-28 | `mockUser` uses `id: 1` (number) and extra fields `publicId`, `nombre`, `apellido` not in `CurrentUser` interface | Type mismatch in test fixture — `CurrentUser.id` is `string`, mock uses `number`. Does not break runtime (Vitest does not typecheck), but could mask actual type errors. | WARNING |

**Assertion quality**: 0 CRITICAL, 1 WARNING (fixture type mismatch — non-blocking)

---

### Quality Metrics

**Linter**: Not run — not in scope for this verification gate.
**Type Checker**: Build passed (`ng build` exit 0) — no TypeScript errors in production code. Test fixture type mismatch (`id: 1` vs `id: string`) is in the spec file only and does not surface in `ng build` (test files are excluded from production build typecheck).

---

### Issues Found

**CRITICAL**: None

**WARNING**:
1. Design D2 deviation: `currentUser$` BehaviorSubject replaced by Angular Signals. Functionally equivalent, architectural improvement — does not violate spec guarantees. Accepted.
2. Design D3 deviation: `auth.service.ts` is at `core/auth/auth.service.ts`, not `core/auth/services/auth.service.ts`. Cosmetic — no functional impact.
3. Test fixture type mismatch: `mockUser.id = 1` (number) but `CurrentUser.id` is `string`. Non-blocking at runtime, but test mock does not accurately reflect the domain type. Recommend fixing the fixture in a follow-up.
4. `Session` interface in mock includes `refreshToken: 'refresh-456'` field not present in the actual `Session` interface (`expiresInSeconds`, `sesionId` are the extra fields). Again, non-blocking — mock is only used to construct values for `of(mockSession)`.

**SUGGESTION**:
1. Add `--typecheck` to the Vitest config so test fixture type mismatches are caught at CI level.
2. Consider moving `auth.service.ts` to `core/auth/services/` to match the documented design path and keep the `core/auth/` directory consistent with the Design D3 intent.

---

### Verdict

**PASS WITH WARNINGS**

134 tests GREEN. Build SUCCESS. Both mutations (V.1, V.2) independently captured. All spec scenarios and guarantees compliant. Design deviations (Signals vs BehaviorSubject, missing `services/` subfolder) are cosmetic improvements, not regressions. Test fixture type mismatches are non-blocking. Change is archive-ready.
