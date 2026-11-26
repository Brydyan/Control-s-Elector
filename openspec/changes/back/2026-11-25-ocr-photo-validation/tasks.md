# SDD Tasks — OCR Photo Validation

**Reference:** `/openspec/changes/back/2026-11-25-ocr-photo-validation/proposal.md` + `design.md`  
**Timeline:** 1 day design ✓ + 2-3 days build (T1-T3)  
**Effort:** ~5-8 hours total (backend only)

---

## Task Breakdown (A-F Model)

| Task | Scope | Effort | Linked ODD | Deliverable | Mutations |
|------|-------|--------|-----------|-------------|-----------|
| **A** | Photo quality validation (OpenCV) | 1-2h | T1 | OcrValidatorImpl + unit tests (8) | JAVA code + tests |
| **B** | Async queue + retry + persistence | 2-3h | T2 | OcrValidationQueueImpl + Liquibase 027 + IT | JAVA + SQL migration |
| **C** | API endpoints + RLS + E2E tests | 2-3h | T3 | EvidenciaResource + DTO + E2E contract (8+) | JAVA endpoints + tests |
| **D** | Manual QA + regressions | 1h | — | 12-point checklist ✓ | NONE (verification) |
| **E** | Staging deployment + logs | 30m | — | Verify production env | NONE (deployment) |
| **F** | Prod rollout + monitoring | 1h | — | Verify audit trail active | NONE (rollout) |

---

## Detailed Tasks

### **Task A — Photo Quality Validation (OpenCV)**

**Linked:** ODD `t1-ocr-photo-quality-validation.md`

**Scope:**
- Create `OcrValidatorImpl` (@ApplicationScoped bean)
- Implement 3 validators:
  - `calculateAverageBrightness()` — Range -100 to 100
  - `calculateContrast()` — Range 0 to 1
  - `calculateBlurVariance()` — Laplacian kernel variance
- Thresholds:
  - Brightness: > -50 ✓
  - Contrast: > 0.3 ✓
  - Blur: > 2.5 ✓
- Metric capture in `ValidationResult` record

**Mutations:**
- NEW: `src/main/java/ec/twintailcs/electoral/evidencia/application/OcrValidatorImpl.java`
- NEW: `src/main/java/ec/twintailcs/electoral/evidencia/application/ValidationResult.java` (record)
- MOD: `pom.xml` (add `org.opencv:opencv-java:4.8.0`)
- NEW: `src/test/java/ec/twintailcs/electoral/evidencia/application/OcrValidatorTest.java` (8 tests)
- NEW: `src/test/java/ec/twintailcs/electoral/evidencia/application/OcrValidatorIT.java` (1 integration)

**Acceptance Criteria:**
- ✅ All 8 unit tests pass (brightness, contrast, blur, good photo, metrics, errors combined)
- ✅ OcrValidatorImpl injectable (@ApplicationScoped)
- ✅ No static methods (testable)
- ✅ Metrics captured in Map<String, Object>

**Verification:**
```bash
rtk mvn test -Dtest='OcrValidatorTest,OcrValidatorIT'
# Expected: 9 tests ✓
```

---

### **Task B — Async Queue + Persistence**

**Linked:** ODD `t2-ocr-async-processor.md`

**Scope:**
- Create `OcrValidationQueueImpl` (@ApplicationScoped bean)
- BlockingQueue-based async worker (daemon thread)
- Process flow:
  1. Enqueue job (evidencia_id + S3 URI)
  2. Worker: download from S3 → validate quality (T1) → OCR if pass (Tesseract)
  3. Store results in `OcrResult` table
  4. Update `Evidencia.validationStatus` (VALIDANDO → PENDIENTE)
- Retry logic: 1s, 2s, 4s exponential backoff (max 3 attempts)

**Mutations:**
- NEW: `src/main/java/ec/twintailcs/electoral/evidencia/application/OcrValidationQueueImpl.java`
- NEW: `src/main/java/ec/twintailcs/electoral/evidencia/domain/OcrResult.java` (entity)
- NEW: `src/main/java/ec/twintailcs/electoral/evidencia/data/OcrResultRepository.java`
- NEW: `src/main/resources/db/changelog/027-ocr-result-table.yaml` (Liquibase migration)
- MOD: `src/main/java/ec/twintailcs/electoral/evidencia/domain/Evidencia.java` (add fields: validationStatus, validatedAt, ocrExtracted)
- NEW: `src/test/java/ec/twintailcs/electoral/evidencia/application/OcrValidationQueueIT.java` (3 tests)
- MOD: `pom.xml` (add `net.sourceforge.tess4j:tess4j:5.9.0`)

**Acceptance Criteria:**
- ✅ All 3 integration tests pass (enqueue, dark photo → VALIDACION_FALLIDA, good photo → OCR extracted)
- ✅ DB migration applies cleanly
- ✅ Retry logic tested
- ✅ OcrResult table populated
- ✅ Evidencia.validationStatus = VALIDANDO while processing

**Verification:**
```bash
rtk mvn test -Dtest='OcrValidationQueueIT'
# Expected: 3 tests ✓

# DB migration check
liquibase validate
```

---

### **Task C — API Endpoints + RLS**

**Linked:** ODD `t3-ocr-api-integration.md`

**Scope:**
- Modify `EvidenciaResource`:
  - `POST /evidencias` → Return 202 Accepted (not 201)
  - Enqueue validation after upload
  - Return `validationStatus: "validando"`
  - NEW: `POST /evidencias/{id}/revalidate` (requires `evidencia:validate` permission)
- Extend `EvidenciaResponse` DTO:
  - `validationStatus` (VALIDANDO, PENDIENTE, etc.)
  - `ocrExtracted` (sensitive: RLS filtered)
  - `validationErrors` (list of error codes)
  - `validatedAt` (timestamp)
  - `confidenceScore` (Tesseract result)
- RLS in `EvidenciaMapper`:
  - OBSERVER: sees `validationStatus` but `ocrExtracted = null`
  - COORDINATOR+: sees all fields (passes `Autorizador.puede("evidencia:validate")` check)
- Contract tests: Verify 202, RLS filtering, revalidate permission checks

**Mutations:**
- MOD: `src/main/java/ec/twintailcs/electoral/evidencia/web/EvidenciaResource.java` (202, POST revalidate)
- MOD: `src/main/java/ec/twintailcs/electoral/evidencia/web/dto/EvidenciaResponse.java` (4 new fields)
- MOD: `src/main/java/ec/twintailcs/electoral/evidencia/application/EvidenciaMapper.java` (RLS logic)
- NEW: `src/test/java/ec/twintailcs/electoral/contract/EvidenciaOcrContractIT.java` (8+ contract tests)
- NEW: `src/test/java/ec/twintailcs/electoral/evidencia/web/EvidenciaOcrIT.java` (E2E integration)

**Acceptance Criteria:**
- ✅ POST /evidencias returns 202 (not 201)
- ✅ validationStatus in response = "validando"
- ✅ Dark photo → VALIDACION_FALLIDA + error code
- ✅ Good photo → eventually OCR text visible
- ✅ OBSERVER can't see ocrExtracted (null in response)
- ✅ COORDINATOR can see ocrExtracted
- ✅ COORDINATOR can POST /revalidate
- ✅ OBSERVER can't POST /revalidate (403 Forbidden)
- ✅ All 8+ contract tests pass
- ✅ E2E test completes: upload → wait → fetch → verify results

**Verification:**
```bash
rtk mvn test -Dtest='EvidenciaOcrContractIT,EvidenciaOcrIT'
# Expected: 8+ tests ✓

# Manual API test
curl -X POST http://localhost:8080/api/evidencias \
  -F "file=@photo.jpg" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 202 Accepted { validationStatus: "validando", ocrExtracted: null }
```

---

### **Task D — Manual QA**

**Scope:** Verification checklist (no code mutations)

**Checklist:**
- [ ] Photo upload → sees "validando" status
- [ ] Dark photo → "brightness_too_low" error flag
- [ ] Good photo → OCR text appears after 2-5s
- [ ] OBSERVER user logs in → no ocrExtracted text visible
- [ ] COORDINATOR user logs in → ocrExtracted text visible
- [ ] COORDINATOR clicks "Revalidar" → photo reprocessed
- [ ] OBSERVER tries "Revalidar" → 403 error
- [ ] Database: OcrResult table populated with 10+ records
- [ ] No regressions: existing evidence upload still works
- [ ] Upload <500ms (not blocking)

**Acceptance Criteria:**
- ✅ All 12 checks pass
- ✅ Existing tests still pass (CI green)

**Verification:**
```bash
rtk mvn verify
# Expected: all tests pass, no new failures
```

---

### **Task E — Staging Deployment**

**Scope:** Pre-prod validation (no code mutations)

**Steps:**
1. Deploy to staging with feature flag OFF (testing prep)
2. Verify Dockerfile + CI have OpenCV + Tesseract installed
3. Check production logs for no errors
4. Verify S3 access works
5. Run contract tests against staging API
6. Enable feature flag (rollout 10%)
7. Monitor error rates, latency

**Acceptance Criteria:**
- ✅ CI passes (all tests)
- ✅ Staging deployment completes
- ✅ No new errors in logs
- ✅ Feature works end-to-end on staging

---

### **Task F — Production Rollout**

**Scope:** Production deployment + monitoring (no code mutations)

**Steps:**
1. Enable feature flag (100% rollout)
2. Monitor:
   - Error rates (target: <0.1%)
   - OCR latency (target: <5s p99)
   - Audit trail: OcrResult table growing
3. Verify existing functionality unaffected
4. Document breaking change (202 instead of 201)

**Acceptance Criteria:**
- ✅ Prod deployment completes
- ✅ Error rates normal (<0.1%)
- ✅ OCR working (OcrResult table has >100 records within 1h)
- ✅ No customer-facing issues

---

## Implementation Order

```
Sequential (each depends on prior):
A (OcrValidatorImpl) → B (Queue + DB) → C (API endpoints) → D (QA) → E (Staging) → F (Prod)

Parallelizable:
- A + B: technically could overlap, but B needs T1 output
- D + E: QA on develop branch, staging prep on staging branch
```

---

## Risk Register

| Risk | Prob | Impact | Mitigation |
|------|------|--------|-----------|
| OpenCV build failure | Medium | Blocks A | Test on CI container first |
| Tesseract missing languages | Low | B fails | Pre-install tesseract-ocr-spa |
| 202 breaks client apps | Low | C blocking | Document API change, notify clients |
| RLS bug (OBSERVER sees OCR) | Low | Security | Contract test with both roles |
| Performance: OCR >5s | Low | UX | Queue size limit + monitoring |
| DB migration fails | Very Low | Rollback | Test locally first, dry-run on prod |

---

## Notes

- Each task A-C has linked ODD in `/odd/tasks/`
- Builder reads ODD for detailed specs
- Architect approves tasks before Builder starts A
- After C passes tests → D/E/F follow ordinary deployment policy
- Timeline: A (1-2h) + B (2-3h) + C (2-3h) = 5-8h total

---

**Status:** Ready for Architect approval → Builder implementation

