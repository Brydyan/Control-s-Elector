# Archive Report: T-BE-17-observer-listado-rls-mockvc

**Change**: 2026-09-07-t-be-17-observer-listado-rls-mockvc  
**Archived**: 2026-09-25  
**Status**: PASS (0 CRITICAL, 0 WARNING)  
**Cycle**: proposal → spec → design → tasks → apply → verify → archive (COMPLETE)

---

## Change Summary

**Feature**: HTTP-level MockMvc test for OBSERVER RLS listado scenario.

Closes **WARNING from T-BE-15**: RF-TEST-04 Scenario 1 (OBSERVER B cannot see OBSERVER A's actas in GET /api/v1/observer/mis-actas via HTTP).

Test validates:
- Spring Security context propagation through MockMvc
- RLS filter (`registradaPorId`) applied at HTTP response level
- Two distinct OBSERVER principals with separate JRV assignments
- Mutation verification: removing filter causes test to fail (OBSERVER B sees A's actas)

---

## Implementation

**Files added**:
- `ObserverControllerRlsIT.java` (1 test, ~120 lines)
- DB setup: 2 OBSERVERs, 2 JRVs, 2 actas with distinct registradaPor

**Test method**:
- `observerBNoVeActasDelObserverA()`
  - Setup: OBSERVER-A registers acta in JRV-1, OBSERVER-B in JRV-2
  - Authenticate as OBSERVER-B via `SecurityMockMvcRequestPostProcessors.authentication()`
  - GET /api/v1/observer/mis-actas
  - Assert: response contains 1 acta (own only), A's acta absent

**Mutation**: Remove `registradaPorId IS NULL OR ...` guard from `findWithFilters()`
- Result: Test fails with size(2) instead of size(1) — mutation captured

---

## Verification Cycle

**Initial state**: Proposal-only (proposal.md, design.md, specs, tasks)

**Implementation**: sdd-apply built full test with MockMvc + data setup

**Verification**: 
- 303 tests total (302 + 1 new), exit=0
- Mutation C.1 independently confirmed
- 4 spec requirements satisfied (RF-TEST-04-H1, RNF-TEST-01/02/03)
- 0 CRITICAL, 0 WARNING

---

## Design Decisions Preserved

1. **MockMvc over Testcontainers**: HTTP-level test requires Spring Security context propagation, which Testcontainers alone cannot provide
2. **Real `UserPrincipal` over `@WithMockUser`**: `CurrentUser.id()` expects `UserPrincipal` type; `@WithMockUser` produces Spring `User` which returns null
3. **Dynamic ID capture in setup**: `@BeforeEach` with RESTART IDENTITY requires capturing IDs at runtime, not hardcoding

---

## Closing Checklist

- [x] RF-TEST-04 Scenario 1 test implemented and passing
- [x] Mutation independently verified (removed guard → test fails)
- [x] Spring Security integration working correctly
- [x] 0 CRITICAL blockers
- [x] Verification report generated
- [x] Archived to `openspec/changes/archive/2026-09-25-t-be-17-observer-listado-rls-mockvc/`
- [x] Merged into main branch `brydyan/sc-162/t-be-15-tests-de-integracion-criticos`

---

## Impact

**T-BE-15 WARNING CLOSED**: RF-TEST-04 Scenario 1 (HTTP listado isolation) now has complete coverage via this MockMvc test. Combined with RF-TEST-05 (transaction-level RLS) and RF-TEST-06 (JPQL filter), RLS is now proven across all three layers: transaction, query, HTTP.

---

## Ready for Production

✅ Merged into main branch `brydyan/sc-162/t-be-15-tests-de-integracion-criticos` alongside T-BE-14, T-BE-15, T-BE-16.
✅ 0 CRITICAL blockers.
✅ All requirements satisfied.
✅ Single PR to `develop` ready (user will create manually).
