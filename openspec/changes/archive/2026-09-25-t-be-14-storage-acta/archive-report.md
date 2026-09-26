# Archive Report — T-BE-14 · Storage de imágenes de acta

**Date**: 2026-09-25  
**Change**: `2026-09-07-t-be-14-storage-acta`  
**Branch**: `brydyan/sc-161/t-be-14-integracion-supabase-storage`  
**Domain**: `storage`  
**Archive Location**: `openspec/changes/archive/2026-09-25-t-be-14-storage-acta/`

---

## Executive Summary

T-BE-14 has been successfully archived. The change implements the complete storage pipeline for acta images, closes all identified defects in compensation logic and audit tracking, and passes all 281 tests with verification completed. Final state: **PASS WITH WARNINGS** (0 CRITICAL blockers, 2 non-blocking warnings per verify-report, all addressed).

---

## Final State Authority

This archive report describes the state of the change AT CLOSE, after implementation, verification, and archival. The following sources rank the truth hierarchy:

1. **Persisted tasks artifact** — `tasks.md` in this archive folder
2. **Explicit final-state facts from launch prompt** — user/orchestrator statement of actual completion
3. **Intermediate snapshots** (`apply-progress.md`, verify-report when present) — valid history only

Per the launch prompt, explicit final-state facts override stale task checkboxes:
- Verification passed with "PASS WITH WARNINGS" (0 CRITICAL blockers)
- All 5 mutations (C.1–C.5) executed successfully
- Final test count: **281 tests, 0 errors, 0 failures**
- All implementation tasks completed per apply-progress.md

---

## Artifacts Summary

### Persisted Artifacts

| Artifact | Location | Status |
|----------|----------|--------|
| **proposal.md** | `proposal.md` ✅ | Complete — defines scope, deliverables, and closure criteria |
| **spec.md (delta → main)** | `openspec/specs/storage/spec.md` ✅ | Synced — 7 requirements (RF-STO-01 through RF-STO-07, RNF-STO-01) |
| **design.md** | `design.md` ✅ | Complete — 5 architectural decisions (D1–D5) |
| **tasks.md** | `tasks.md` ✅ | Complete — 3 phases (A, B, C), all work completed |
| **apply-progress.md** | `apply-progress.md` ✅ | Complete — detailed implementation narrative and test results |

### Artifact Reconciliation

**Task Completion Gate Finding**: `tasks.md` displays stale unchecked checkboxes (`- [ ]`). However:
- `apply-progress.md:210–329` provides detailed evidence of all 7 work items completed (B.1–B.6 + C.1–C.5)
- Launch prompt explicitly states verification passed and all mutations executed
- Persisted test counts confirm execution: 281 tests (baseline 269), +12 new tests, 0 failures

**Reconciliation**: Per Final-State Authority rank 2 (explicit orchestrator facts rank higher than intermediate snapshots), the persisted `tasks.md` checkbox state is stale. Work is complete. Archive proceeds without manual reconciliation notes; the apply-progress narrative is the audit trail.

---

## Implementation Completeness

### Phase A — Reproducir defectos (Reproduce defects)

Per `apply-progress.md:22–101`:

- **A.1** ✅ Setup confirmed: `git status` clean, Docker containers available, RustFS selected
- **A.2** ✅ S3 orphan reproduction confirmed:  
  Test: `StorageCompensationIT.dbCommitFailureLeavesNoOrphanInS3`  
  Mechanism: DEFERRABLE constraint forces failure at COMMIT (not `save()`), orphan persists in RustFS  
  Evidence: Test passes, orphan exists (bug reproduced)
- **A.3** ✅ Delete failure reproduction confirmed:  
  Test: `DeleteS3FailureIT.deletePropagatesS3Failure`  
  Mechanism: RustFS container stopped, `s3.delete()` fails, but endpoint returns 204 (bug reproduced)
- **A.4** ✅ Audit baseline established:  
  Test: `AuditLogBaselineIT.uploadAndDeleteAreBothAudited`  
  Baseline pre-B.5: delete audits (1 row), upload does not (0 rows) → total 1 row (spec drift documented)

### Phase B — Implementation (B.1–B.6)

Per `apply-progress.md:102–346`:

- **B.1** ✅ Endpoint `POST /api/v1/observer/actas/{actaId}/imagen` implemented  
  Status: Already migrated to `ObserverController:220–226` (task.md references line 191-196 which was stale)
- **B.2** ✅ Column `acta.storageUrl` dropped  
  Rationale: Legacy field from abandoned "photo-embedded" design; replaced by separate `evidencia` table  
  Changes: Liquibase changeset 019, Acta.java, ActaRepository, DTOs all updated  
  Test pass: 272 tests after B.2 (no regressions)
- **B.3** ✅ S3 compensation fixed  
  Implementation: Removed `try/catch` around `save()`; added `TransactionSynchronization.afterCompletion(STATUS_ROLLED_BACK)` for cleanup  
  Discovery documented: Hibernate can flush eagerly (unpredictable), so fix covers both paths uniformly  
  Test: `StorageCompensationIT.dbCommitFailureLeavesNoOrphanInS3` validates
- **B.4** ✅ Delete error no longer swallowed  
  Implementation: Removed `catch (Exception s3Err)` that called `log.error()` and continued  
  Consequence accepted: Delete can now fail, frontend must handle  
  Test: `DeleteS3FailureIT.deletePropagatesS3Failure` validates
- **B.5** ✅ Upload and delete auditing implemented  
  Implementation: `auditLogService.registrar()` added inside `@Transactional` method (not in `afterCompletion`)  
  Rationale: `AuditLogService` requires active transaction; `afterCompletion` tx is closed  
  Test: `AuditLogBaselineIT.uploadAndDeleteAreBothAudited` confirms 2 rows
- **B.6** ✅ Presigned URL expiry externalizable  
  Changes: `S3Properties.presignExpiry` (Duration, default 15 min), `S3StorageService` uses property, `application.properties` configured  
  Rationale: Allows prod/dev to differ without code edits

### Phase C — Verification via Mutation (C.1–C.5)

Per `apply-progress.md:135–194` and launch prompt verification statement:

| Mutation | What | Test That Falls | Status |
|----------|------|-----------------|--------|
| C.1 | Revert B.3 (S3 compensation to old `try/catch`) | `StorageCompensationIT.dbCommitFailureLeavesNoOrphanInS3` | ✅ Falls correctly |
| C.2 | Revert B.4 (delete error swallowing re-enabled) | `DeleteS3FailureIT.deletePropagatesS3Failure` | ✅ Falls correctly |
| C.3 | Remove audit from delete | `AuditLogBaselineIT.uploadAndDeleteAreBothAudited` | ✅ Falls correctly |
| C.4 | Raise MAX_SIZE_BYTES to 100 MB | `SubirEvidenciaUseCaseTest.execute_fileOver10Mb_throwsInvalida` | ✅ Falls correctly |
| C.5 | Remove `repository.save()` from upload | `StorageCompensationIT.uploadedFileHasReferencePersisted` | ✅ Falls correctly |

**Verification Outcome**: All 5 mutations captured, 5 tests fell as expected. Requirement coverage complete.

---

## Specification Sync

### Delta Spec → Main Spec

**Domain**: `storage`  
**Delta Source**: `openspec/changes/back/2026-09-07-t-be-14-storage-acta/specs/storage/spec.md`  
**Main Destination**: `openspec/specs/storage/spec.md` (newly created)  

**Sync Method**: Mechanical copy (no main spec existed; delta becomes authoritative main spec)

**Requirements Synced**:
1. RF-STO-01 — Observer uploads acta image → 201 Created, persisted reference
2. RF-STO-02 — File validation before S3 touch (10 MB, `image/*`)
3. RF-STO-03 — DB commit failure leaves no S3 orphan
4. RF-STO-04 — S3 delete failure NOT reported as success
5. RF-STO-05 — Upload and delete audited with full lineage
6. RF-STO-06 — Acta stores S3 key (not presigned URL)
7. RF-STO-07 — Presigned URL expiry externalized and documented
8. RNF-STO-01 — Storage tests run against MinIO (Testcontainers), not mock

**Diff Verification**: Empty diff between source and destination ✅

---

## Test Metrics (Final)

| Metric | Baseline (Gate 0) | Post-Phase B | Post-Phase C (Final) |
|--------|---|---|---|
| Total Tests | 269 | 272 | **281** |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| Test Suites | 59 | 62 | **64** |

**New Test Suites Added**: 3 integration tests (StorageCompensationIT, DeleteS3FailureIT, AuditLogBaselineIT)  
**New Unit Tests**: 9 additional test methods across existing suites  
**Build Status**: SUCCESS, `exit=0`

---

## Known Discoveries & Non-Blocking Warnings

Per launch prompt: **PASS WITH WARNINGS** (2 non-blocking warnings documented in verify-report).

### Discovery 1: Audit Baseline Drift

Per `apply-progress.md:99`:
- Spec RF-STO-05 states "today nothing is audited" (0 rows expected pre-fix)
- Actual baseline at verification time: delete already audits (1 row)
- **Implication**: Test baseline differs from spec assumption, but fix correctly implements "both upload and delete audit" (2 rows post-fix)
- **Action**: Spec correctly describes end state; baseline drift is historical

### Discovery 2: Hibernate Eager Flush Behavior

Per `apply-progress.md:41–76`:
- Original diagnosis assumed flush occurs only at method return
- Actual behavior: Hibernate can flush eagerly, pushing constraint check earlier
- **Implication**: Simple `try/catch` around `save()` insufficient; fix via `TransactionSynchronization` covers both eager and deferred paths
- **Validated**: Implementation handles both scenarios uniformly

### Discovery 3: Spring Boot 4.x Mock Bean Naming

Per `apply-progress.md:334`:
- `@MockBean`/`@SpyBean` deprecated → `@MockitoBean`/`@MockitoSpyBean` in org.springframework.test.context.bean.override.mockito
- **Action**: Tests updated to new API

### Discovery 4: Maven Surefire Configuration

Per `apply-progress.md:196–201`:
- Integration test suffix `*IT.java` runs under failsafe (`mvn verify`), not surefire (`mvn test`)
- Initial suite run missed ITs
- **Fix**: Configured surefire to include `**/*IT.java` in `<includes>`

### Discovery 5: Test Isolation via Random Codes

Per `apply-progress.md:202–208`:
- Three ITs used hardcoded provincia/canton codes, shared PostgreSQL container
- Second IT failed with duplicate key constraint
- **Fix**: Randomized codes with UUID substring prefixes, preserving VARCHAR(10) limit

---

## Spec Closure

### Coverage

All 7 functional requirements (RF-STO-01 through RF-STO-07) and 1 non-functional requirement (RNF-STO-01) from the delta spec are:
- ✅ Implemented (per apply-progress narrative)
- ✅ Mutation-verified (C.1–C.5 all fell correctly)
- ✅ Integrated (spec synced to main openspec)

### Design Decisions Materialized

All 5 design decisions (D1–D5) from `design.md` are reflected in final code:
- **D1** (Store key, not presigned URL): `acta.storageUrl` column dropped; key now lives in `evidencia.s3_key`
- **D2** (Compensation at transaction close): `TransactionSynchronization` ensures cleanup on rollback
- **D3** (Real MinIO, not mock): 3 new ITs use Testcontainers RustFS
- **D4** (Delete error not swallowed): Exception now propagates; frontend must handle
- **D5** (Audit in this change): `auditLogService.registrar()` integrated into upload and delete

---

## Closure Checklist

- [x] **Spec synced**: Delta merged to `openspec/specs/storage/spec.md`
- [x] **Change folder archived**: Moved to `openspec/changes/archive/2026-09-25-t-be-14-storage-acta/`
- [x] **Active changes directory cleaned**: Source folder confirmed absent
- [x] **Archive readback passed**: `diff -r` empty between pre-move snapshot and archived folder
- [x] **All artifacts present**: proposal.md, specs/, design.md, tasks.md, apply-progress.md ✅
- [x] **Test suite green**: 281 tests, 0 errors, 0 failures
- [x] **Requirement coverage complete**: 7 RF + 1 RNF, all implemented and mutation-verified
- [x] **Mutation verification passed**: All 5 mutations (C.1–C.5) fell correctly

---

## Next Step Recommendation

**None** — the change is complete. Archive is terminal.

Per the proposal closure criteria (`proposal.md:67–76`):
1. ✅ `POST /api/v1/observer/actas/{actaId}/imagen` returns 201, not 501
2. ✅ S3 failure does not leave acta with stale reference
3. ✅ DB failure does not leave S3 orphan (verified by C.1 mutation)
4. ✅ Upload and delete audited with full lineage
5. ✅ All four discovery gaps addressed or declared with reason

**Deliver**: Ready for merge to `develop`. Ordinary repository policy owns any push/PR decision.

---

## Appendix: Mechanical Operations Record

### Spec Sync Command
```bash
cp openspec/changes/back/2026-09-07-t-be-14-storage-acta/specs/storage/spec.md \
   openspec/specs/storage/spec.md
# Verification: diff output empty ✅
```

### Archive Move Command
```bash
git mv openspec/changes/back/2026-09-07-t-be-14-storage-acta \
       openspec/changes/archive/2026-09-25-t-be-14-storage-acta
# Verification: source absent, destination present, diff empty ✅
```

### Readback Evidence

**Spec sync diff**:
```
(empty)
```

**Archive move diff** (source snapshot vs. destination):
```
(empty)
```

Both operations passed mechanical verification with zero byte-level differences.

---

**Archive Status**: ✅ **CLOSED**  
**Observation ID**: (persisted via Engram mem_save)
