```yaml
schema: gentle-ai.verify-result/v1
evidence_revision: sha256:3c89ccd267a338bf6793505e016d749262169377d0ade7f79ee5ff615f000512
verdict: pass_with_warnings
blockers: 0
critical_findings: 0
requirements: 0/0
scenarios: 0/0
test_command: ./node_modules/.bin/vitest run
test_exit_code: 0
test_output_hash: sha256:06e663283fdc3bac825b6519695e92d83007445470905d20c08321933a876224
build_command: ./node_modules/.bin/ng build
build_exit_code: 0
build_output_hash: sha256:f3a91bc40f7b79161f9e0a3f2dd55b085a81e321efa8dc6fb7dd8bbe93215bb2
```

## Verification Report

**Change**: 2026-09-07-t-fe-04-http-interceptors
**Version**: N/A
**Mode**: Strict TDD

### Completeness
| Metric | Value |
|--------|-------|
| Tasks total | 8 |
| Tasks complete | 8 |
| Tasks incomplete | 0 |

### Build & Tests Execution
**Build**: ✅ Passed — 0 errors, 3 pre-existing warnings (unrelated to change)
```text
./node_modules/.bin/ng build
Application bundle generation complete. [2.666 seconds]
Output location: frontend/app/www
Warnings: NG8107 (x2 in acta-detalle, pre-existing), scss budget overage (pre-existing)
```

**Tests**: ✅ 135 passed / 0 failed / 0 skipped
```text
./node_modules/.bin/vitest run
Test Files  29 passed (29)
Tests  135 passed (135)
Duration  2.75s
```

**Interceptor spec**: ✅ 7/7 passed
```text
./node_modules/.bin/vitest run src/app/core/auth/auth.interceptor.spec.ts
Test Files  1 passed (1)
Tests  7 passed (7)
```

**Coverage**: ➖ Not available

### Spec Compliance Matrix
Note: spec.md uses non-canonical heading format (### S1 — ...) so native count is 0/0.
All scenarios manually mapped below.

| Scenario | Test | Result |
|----------|------|--------|
| S1 — JWT token attached to API request | `auth.interceptor.spec.ts > should attach Bearer token to API requests when token exists` | ✅ COMPLIANT |
| S2 — No token: 401 triggers refresh | `auth.interceptor.spec.ts > should attempt refresh on 401 and retry original request when refresh succeeds` | ✅ COMPLIANT |
| S3 — Refresh succeeds: retry with new token | `auth.interceptor.spec.ts > should attempt refresh on 401 and retry original request when refresh succeeds` | ✅ COMPLIANT |
| S4 — Refresh fails: propagate error | `auth.interceptor.spec.ts > should clear session and redirect to /login when refresh attempt fails on 401` | ✅ COMPLIANT |
| D.1 — 403 OBSERVER redirect loop fix | `auth.interceptor.spec.ts > should redirect OBSERVER to /observer/mis-jrvs on 403 Forbidden` | ✅ COMPLIANT |
| D.1 — 403 non-OBSERVER redirect | `auth.interceptor.spec.ts > should redirect to /dashboard on 403 Forbidden without clearing session` | ✅ COMPLIANT |
| D.1 — No token leak to non-API | `auth.interceptor.spec.ts > should not attach Bearer token to non-API external requests` | ✅ COMPLIANT |
| D.1 — 401 on auth endpoint: clear+redirect | `auth.interceptor.spec.ts > should redirect to /login and clear session on 401 from /auth/refresh` | ✅ COMPLIANT |

**Compliance summary**: 8/8 scenarios compliant (4 spec + 4 additional)

### Mutation Verification V.1
| Check | Result |
|-------|--------|
| Mutation applied | ✅ OBSERVER branch removed (ternary replaced with hardcoded '/dashboard') |
| Test with mutant | ❌ 1 failed / 6 passed — `should redirect OBSERVER to /observer/mis-jrvs on 403 Forbidden` |
| Failure message | `Expected '/observer/mis-jrvs', Received '/dashboard'` |
| Original restored | ✅ 7/7 passing |
| Bug capture confirmed | ✅ Test kills the mutant |

### TDD Compliance
| Check | Result | Details |
|-------|--------|---------|
| TDD Evidence reported | ✅ | Found in apply-progress.md |
| All tasks have tests | ✅ | 8/8 tasks covered |
| RED confirmed (tests exist) | ✅ | auth.interceptor.spec.ts exists |
| GREEN confirmed (tests pass) | ✅ | 7/7 pass on execution |
| Triangulation adequate | ✅ | 7 tests, 8 scenarios, dual assertions on OBSERVER test |
| Safety Net for modified files | ✅ | 6 pre-existing tests ran before D.1 test added |

**TDD Compliance**: 6/6 checks passed

---

### Test Layer Distribution
| Layer | Tests | Files | Tools |
|-------|-------|-------|-------|
| Unit (TestBed + HttpTestingController) | 7 | 1 | vitest |
| Integration | 0 | 0 | not applicable |
| E2E | 0 | 0 | not installed |
| **Total** | **7** | **1** | |

---

### Changed File Coverage
Coverage analysis skipped — no coverage tool detected

---

### Assertion Quality
**Assertion quality**: ✅ All assertions verify real behavior
- Mock count: 2, Assertion count: 20 — ratio excellent (1:10)
- No tautologies, no ghost loops, no type-only assertions, no smoke-test-only tests
- OBSERVER test has dual assertions: positive (`toHaveBeenCalledWith('/observer/mis-jrvs')`) AND negative (`not.toHaveBeenCalledWith('/dashboard')`)

---

### Quality Metrics
**Linter**: ➖ Not run standalone
**Type Checker**: ✅ ng build exits 0 — TypeScript compilation clean for changed files

### Correctness (Static Evidence)
| Requirement | Status | Notes |
|------------|--------|-------|
| G1: Authorization header added automatically | ✅ Implemented | Lines 34-48: token attached for isApiRequest |
| G2: 401 triggers automatic refresh | ✅ Implemented | Lines 78-79: refreshAndRetry() called on 401 |
| G3: Original request retried with new token | ✅ Implemented | refreshAndRetry() lines 96-101: switchMap + cloned request |
| D.1: OBSERVER 403 loop fix | ✅ Implemented | Lines 59-66: ternary on store.user()?.role |

### Coherence (Design)
| Decision | Followed? | Notes |
|----------|-----------|-------|
| D1: HttpInterceptorFn functional | ✅ Yes | `export const authInterceptor: HttpInterceptorFn` |
| D2: Two separate files | ⚠️ Partial | Single unified file used (pre-existing decision). Both responsibilities present. |
| D3: JWT first, then error handling | ✅ Yes | Token attached before next(), catchError after response |
| D4: Exclude auth endpoints from token | ✅ Yes | isAuthEndpoint check prevents circular refresh |
| D5: Retry on 401, propagate if fails | ✅ Yes | refreshAndRetry() with switchMap + catchError |

### Issues Found
**CRITICAL**: None

**WARNING**:
- W1 (Design D2): Single unified file `auth.interceptor.ts` instead of two separate files. Pre-existing deviation from Phases A/B/C — not introduced in this apply session. Both JWT and refresh responsibilities are present and tested. No functional impact.
- W2 (Spec format): Non-canonical heading format in spec.md prevents native scenario counting (returns 0/0). All 4 scenarios verified manually with passing tests.

**SUGGESTION**:
- S1: The OBSERVER ternary in the 403 handler should become a role-to-route map if MASTER/COORDINATOR also need restricted 403 redirects. Documented in apply-progress.md.
- S2: `acta-detalle.page.scss` budget overage (pre-existing, unrelated) should be tracked as separate tech debt.

### Verdict
PASS WITH WARNINGS

All 8 tasks complete. 135/135 tests GREEN. ng build SUCCESS (0 errors). All spec scenarios (S1-S4) covered by passing tests. Mutation V.1 confirmed captured: OBSERVER test kills the mutant on both assertions. Two warnings are pre-existing structural issues not introduced by this change.
