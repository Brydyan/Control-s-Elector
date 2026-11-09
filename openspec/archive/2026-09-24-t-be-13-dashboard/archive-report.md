# Archive Report — T-BE-13 · Dashboard Endpoints

**Change**: `2026-09-07-t-be-13-dashboard`  
**Archived to**: `openspec/changes/archive/2026-09-24-t-be-13-dashboard/`  
**Date archived**: 2026-09-24  
**Mode**: openspec  
**Archive status**: ✅ COMPLETE

---

## Executive Summary

The T-BE-13 dashboard endpoints change has been successfully archived. Four read-only endpoints (`/resumen`, `/resultados`, `/progreso`, `/novedades`) were implemented with RBAC enforcement (ADMIN, MASTER, COORDINATOR only) and comprehensive test coverage. The change passed verification (5/5 core mutations detected + 1 APPROVED on Q6 after coverage fix), executed 269 passing tests, and completes the SDD cycle without open critical issues.

---

## Scope Delivered

| Component | Status | Details |
|-----------|--------|---------|
| **Endpoints** | ✅ DONE | 4× `GET /api/v1/dashboard/*` (resumen, resultados, progreso, novedades) |
| **RBAC** | ✅ DONE | `@PreAuthorize("hasAnyRole('ADMIN','MASTER','COORDINATOR')")` at class level |
| **Read-only enforcement** | ✅ DONE | No write methods; `@GetMapping` only (verified by reflection test) |
| **Aggregation queries** | ✅ DONE | JPQL in `ActaRepository`, `ActaDetalleRepository`, `JrvRepository` with ANULADA filter |
| **ANULADA exclusion** | ✅ DONE | Applied in all 4 aggregation queries; 5/5 mutations detected (Q1-Q5) |
| **TZ-invariant time calculation** | ✅ DONE | `Duration.between(Instant, Instant)` without `ZoneId.systemDefault()` |
| **Tests** | ✅ DONE | 23 tests (1 rerun), covering RF-DASH-01 through RNF-DASH-01 |

---

## Verification State (Final Authority)

Source: `verify-report.md` rounds 1–2, interpreted per Final-State Authority hierarchy.

### Round 1 (Initial Verification)
**Verdict**: PARTIAL — 5 mutations detected, 1 gap identified

| Question | Status | Finding |
|----------|--------|---------|
| Q1 — RF-DASH-04: ANULADA excluded in `votosTotalesPorCandidato()` | ✅ PASS | Mutation detected: `expected:<90> but was:<590>` |
| Q2 — RF-DASH-05: `COUNT(DISTINCT a.jrv.id)` prevents triple-count | ✅ PASS | Mutation detected: `expected:<1> but was:<3>` |
| Q3 — RF-DASH-05: ANULADA excluded in `countDistinctJrvsWithActaByCanton()` | ✅ PASS | Mutation detected: `expected:<0> but was:<1>` |
| Q4 — RF-DASH-03: `totalActivas` excludes ANULADA | ✅ PASS | 2 mutations detected: `5L vs 10L`, `7 vs 8` |
| Q5 — RBAC + read-only: `@PreAuthorize` + no write methods | ✅ PASS | Mutations detected: 403→200 + reflection message exact |
| Q6 — RF-DASH-07: TZ-invariant time | ⚠️ FAIL — cobertura | Original test could not detect `ZoneId.systemDefault()` reintroduction |

**Baseline**: 266 tests (244 base + 22 new), 0 failures, 2 skipped, BUILD SUCCESS

### Round 2 (Coverage Fix Verification)
**Verdict**: APPROVED — gap closed

Per `fixes-required.md` F1, the Builder replaced the original `RF_DASH_07_..._minutosTranscurridosConsistenteConInstant` with two new tests using `@Autowired JdbcTemplate` to fix `fecha_registro` in the past:
- `RF_DASH_07_novedades_minutosTranscurridos_actaDeAyer2300UTC_coherenteEnUTCyGYE`
- `RF_DASH_07_novedades_minutosTranscurridos_actaDeAyer0400UTC_coherenteEnUTCyGYE`

| Question | Status | Finding |
|----------|--------|---------|
| Q1 — New tests pass in UTC and GYE | ✅ PASS | Both tests green in both TZs; 253 min (UTC) = 253 min (GYE), 1393 = 1393 |
| Q2 — Exacerbated mutation detected | ✅ PASS | Asymmetry confirmed: `was:<1440>` (UTC) vs `was:<0>` (GYE) for `ayer2300` |
| Q3 — No regresion in Q1-Q5 | ✅ PASS | Code unchanged (git diff + mtime); 21 non-F1 tests remain green |
| Q4 — CI runner final gate | ✅ PASS | 269 tests / 0 failures / 2 skipped / BUILD SUCCESS |

**Final baseline**: 269 tests (244 base + 22 dashboard + 3 parallel additions), 0 failures, 2 skipped, BUILD SUCCESS

---

## Implementation Evidence (per apply-progress.md)

All implementation tasks completed and verified by mutation:

### Discovery & Preparation (§A)
- A.1: Confirmed existing components (`CantonRepository`, `ActaRepository` methods, `Usuario` fields)
- A.1: Added missing queries: `JrvRepository.countByParroquia_Canton()` + `countJrvsGroupedByCanton()`
- A.2: Baseline measured: 244 tests, clean build

### Aggregation Queries (§B)
- B.1: `JrvRepository` — added 3 derivedquery methods and custom JPQL
- B.2: `ActaDetalleRepository.votosTotalesPorCandidato()` with `WHERE d.acta.estado <> 'ANULADA'` (Q1 mutation: `90 vs 590`)
- B.3: `ActaRepository.countDistinctJrvsWithActaByCanton()` with DISTINCT filter (Q2/Q3 mutations: `1 vs 3`, `0 vs 1`)
- B.4: Confirmed `fechaRegistro` column matches JPA field; `registradaEn` was reference-only, not a real column

### Controller & DTOs (§C)
- C.1: DTOs in new package `ec.twintailcs.electoral.dashboard.web.dto`: 5 records
- C.2: `DashboardController` with 4× `@GetMapping` + class-level `@PreAuthorize`
- C.3: `minutosTranscurridos` using `Instant.now()` + `Duration.between()` without `ZoneId.systemDefault()`
- C.4: 22 tests (1 rerun to 23 after F1 fix) covering all requirements

### Mutation Verification (§D)
- D.1: WHERE clause in B.2 — detected by Q1
- D.2a: DISTINCT in B.3 — detected by Q2
- D.2b: ANULADA filter in B.3 — detected by Q3
- D.3: totalActivas calculation — detected by Q4 (2 tests)
- D.4: @PreAuthorize removal — detected by Q5a
- D.5: TZ-invariant calculation — gap found, covered by new tests in Round 2 (Q6)
- D.6: Reflection test for write methods — detected by Q5b

---

## Discoveries Documented

All discoveries properly logged in `apply-progress.md`:

1. **Discovery #1 — `Acta.motivoDisputa` absent**: Field does not exist in JPA or Liquibase migrations. Implemented `NovedadDto.motivo` as nullable; documented for future change when T-BE-16 resolves `TipoObservacion` enum.

2. **Discovery #2 — Geography entities lack `publicId`**: `Canton`, `Parroquia`, `Jrv`, `Cargo`, `Candidato`, `Partido` do not use UUID public IDs. DTOs expose Long IDs (consistent with `CantonResponse`, `CargoResponse`). Only `NovedadDto` uses UUID (for `actaPublicId`, `observerPublicId`).

3. **Discovery #3 — Unique index `uk_acta_activa`**: Constraint `(jrv_id, cargo_id) WHERE estado IN (BORRADOR, ENVIADA, EN_DISPUTA, CORREGIDA)` limits test setup. Documented; tests adjusted to use distinct JRVs/cargos for each active state variant.

---

## Artifacts Archived

All SDD artifacts mechanically moved to `openspec/changes/archive/2026-09-24-t-be-13-dashboard/`:

- ✅ `proposal.md` — goal, approach, rollback plan
- ✅ `design.md` — 5 architectural decisions (D1-D5) with rationale
- ✅ `tasks.md` — task breakdown (A-D sections) with execution order
- ✅ `apply-progress.md` — detailed implementation log and discoveries
- ✅ `verify-report.md` — 2-round verification with mutation evidence
- ✅ `fixes-required.md` — F1 gap closure plan (executed in Round 2)
- ✅ `specs/dashboard/spec.md` — delta spec with 6 requirements (RF-DASH-01 through RNF-DASH-01)

**Spec merge**: Delta spec copied (mechanical `cp`) to `openspec/specs/dashboard/spec.md` (new main spec created; no existing spec to merge).

---

## Verification Gates

### Task Completion Gate ✅
All implementation tasks marked complete in `apply-progress.md`:
- A.1, A.2, B.1-B.4, C.1-C.4, D.1-D.6: ✅ DONE
- No unchecked tasks remain in archived `tasks.md`

### CRITICAL Issues Gate ✅
No CRITICAL issues in `verify-report.md`:
- Q1-Q5: PASS (mutations detected as expected)
- Q6: FAIL → gap documented, covered by Round 2 new tests → APPROVED
- F1-F5: no CRITICAL severity

### Archive Readiness ✅
- Change folder moved: `openspec/changes/back/2026-09-07-t-be-13-dashboard/` → archived
- Main spec created: `openspec/specs/dashboard/spec.md` (delta spec copied)
- No stale checkboxes; all completed work documented
- Active source absent after move (verified by `diff -r`)

---

## Next Steps Recommended

1. **Commit** the archive + spec creation:
   ```bash
   git commit -m "archive: move t-be-13-dashboard to archive; sync dashboard spec"
   ```

2. **No follow-up SDD required** — change is complete. F1 closure and all mutations verified by independent evidence.

3. **Monitoring recommendations** (design.md D5):
   - Monitor `/novedades` and `/resumen` response sizes in production
   - If either exceeds ~1000 rows, open change for pagination

4. **Future changes**:
   - **T-BE-16 follow-up**: Once `TipoObservacion` enum is resolved, merge evidences into `/novedades` (populate `NovedadDto.motivo` from audit log)
   - **Schema stability**: When migrations are finalized, reevaluate creating a SQL view `acta_activa` to centralize ANULADA exclusion rule

---

## Audit Trail

| Phase | Worker | Session | Time | Result |
|-------|--------|---------|------|--------|
| Proposal | — | — | 2026-09-07 | Created |
| Design | — | — | 2026-09-07 | Created |
| Tasks | — | — | 2026-09-07 | Created |
| Apply | `brydyan` | — | 2026-09-07 to 2026-09-24 | 269 tests / BUILD SUCCESS |
| Verify (R1) | `mvs_77bc405f4943418cbb3f5bec549a2593` | worker | 2026-09-24 21:21–21:52 | PARTIAL (5/6 pass, Q6 gap) |
| Verify (R2) | `mvs_78e547e803984a18888ce1be3f1701c5` | worker | 2026-09-24 22:11–22:30 | APPROVED (F1 closed) |
| Archive | `sdd-archive` | executor | 2026-09-24 23:05 | ✅ COMPLETE |

---

## Key Learnings

1. ANULADA exclusion in aggregation queries cannot be filtered in Java — must live in the query or risk silent omission in future endpoints.
2. Mutation testing for timezone-invariant time calculations requires tests that create data far in the past (yesterday) with fixed timestamps; simple same-second tests cannot detect `ZoneId.systemDefault()` asymmetries.
3. Unique constraints like `uk_acta_activa` on conditional state tuples force test setup to use distinct JRVs/cargos for each active-state variant; this constraint is correct but must be documented.
4. Five out of six implementation requirements achieved deterministic mutation-based verification; the sixth (TZ invariance) required a refinement cycle to close the cobertura gap.
5. The `@PreAuthorize` guard at class level combined with exclusive `@GetMapping` methods provides defense-in-depth for the "ADMIN never writes" invariant; both layers are necessary.
