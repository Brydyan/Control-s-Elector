# OCR Photo Validation — SDD + 3 ODDs (Complete Package)

**Status:** Ready for Architect review → Builder implementation  
**Branch:** `feature/ocr-evidence-validation`  
**Timeline:** 1 day design, 2-3 days build (T1-T3)  

---

## 📚 Documentation Structure

| Document | Purpose | Audience | Status |
|----------|---------|----------|--------|
| **proposal.md** | Executive summary + 6 design decisions | Architect | ✅ Done |
| **design.md** | Technical details (schema, code, arch) | Architect + Builder | ✅ Done |
| **specs.md** | Functional + non-functional requirements (S1-S10) | Architect + Builder | ✅ Done |
| **tasks.md** | Implementation tasks A-F with mutations | Architect + Builder | ✅ Done |
| **apply-progress.md** | Progress log template (Builder fills in) | Builder | ✅ Done |
| **README.md** (this file) | Index + handoff notes | Everyone | ✅ Done |
| **t1-ocr-photo-quality-validation.md** | ODD Task 1 — OpenCV validators | Builder | ✅ Done |
| **t2-ocr-async-processor.md** | ODD Task 2 — Tesseract + queue | Builder | ✅ Done |
| **t3-ocr-api-integration.md** | ODD Task 3 — API + E2E | Builder | ✅ Done |

---

## 🎯 What Gets Built

### Phase 1: Design (Today)
- ✅ SDD approved by Architect
- ✅ 3 ODDs created + ready for Builder

### Phase 2: Implementation (Tasks A-F, full details in `tasks.md`)

| Phase | Task | Input | Output | Duration | ODD |
|-------|------|-------|--------|----------|-----|
| **A** | **T1** | — | OcrValidatorImpl + unit tests | 1-2h | t1-ocr-photo-quality-validation.md |
| **B** | **T2** | A | OcrValidationQueueImpl + DB migration | 2-3h | t2-ocr-async-processor.md |
| **C** | **T3** | A+B | API endpoints + contract tests | 2-3h | t3-ocr-api-integration.md |
| **D** | QA | A-C | Manual checklist (12 points) | 1h | — |
| **E** | Staging | A-D | Deploy + monitoring setup | 30m | — |
| **F** | Prod | A-E | Rollout 100% + verify | 1h | — |

**See `tasks.md` for complete A-F breakdown with mutation details.**

### Phase 3: Deploy & Verify (Tasks D-F)
- ✅ All tests pass (unit + integration + contract)
- ✅ Manual QA: upload photo → see validation status
- ✅ Staging deployment + logs verified
- ✅ Prod rollout + monitoring active

---

## 🔧 Key Decisions

### D1 — Async 202 Workflow
```
POST /evidencias → 202 Accepted immediately
                ├─ File stored in S3 ✓
                ├─ DB record created (estado=VALIDANDO) ✓
                └─ Enqueued for OCR validation (async)
                   ├─ OpenCV: brightness/contrast/blur check
                   ├─ Tesseract: extract text (if validation passes)
                   └─ Store results in OcrResult table
```

**Why:** No blocking on user. OCR takes 2-5s, frontend waits 200ms for 202.

---

### D2 — OcrResult Table
```sql
CREATE TABLE ocr_result (
  id BIGINT PRIMARY KEY,
  evidencia_id BIGINT UNIQUE,
  raw_text LONGTEXT,                -- Compliance audit trail
  confidence_score DECIMAL(5,2),    -- Tesseract confidence
  validation_status VARCHAR(50),    -- success|brightness_low|...
  errors JSON,                      -- {brightness, contrast, blur}
  processed_at TIMESTAMP
);
```

**Why:** Audit trail for CNE compliance. Searchable by status/metrics.

---

### D3 — New Permission evidencia:validate
- **OBSERVER:** Can upload + see validationStatus (NOT ocrExtracted)
- **COORDINATOR:** Can upload + see validationStatus + ocrExtracted + revalidate
- **MASTER:** Same as COORDINATOR

**Why:** RLS: OBSERVER should see "photo is validating" but not sensitive text.

---

### D5 — Local Stack (OpenCV + Tesseract)
| Aspect | Local | Cloud |
|--------|-------|-------|
| Cost | €0 | €600/M images |
| Privacy | On-prem | Google stores |
| Accuracy | 85-90% | 95%+ |
| **Decision** | ✅ **Local** | ❌ Cloud |

**Why:** Free, private, good enough for flagging.

---

## 📖 How to Use This SDD

**For Architect (pre-approval):**
1. Read `proposal.md` (decisions D1-D6)
2. Review `design.md` (technical details)
3. Review `specs.md` (all functional/non-functional requirements S1-S10)
4. Approve tasks A-F in `tasks.md`
5. Provide feedback on open questions (design.md end)

**For Builder (implementation):**
1. Read `proposal.md` (understand why)
2. Read `specs.md` (functional requirements)
3. Read `tasks.md` (scope A-F, mutations)
4. Read linked ODD files (T1, T2, T3) for detailed specs
5. Fill in `apply-progress.md` as you complete each task
6. Run verification commands per task

**Reference Flow:**
```
proposal.md (why)
         ↓
design.md (how)
         ↓
specs.md (what exactly)
         ↓
tasks.md (A-F breakdown)
         ↓
/odd/tasks/t1,t2,t3.md (detailed implementation)
         ↓
apply-progress.md (track progress)
```

---

## 📋 Implementation Path

### Step 1: Builder starts T1 (1-2 hours)
```
Create: OcrValidatorImpl (OpenCV)
├─ calculateAverageBrightness()
├─ calculateContrast()
├─ calculateBlurVariance()
└─ validateQuality() → ValidationResult

Tests:
├─ darkPhoto_returnsFAILED()
├─ lowContrast_returnsFAILED()
├─ blurry_returnsFAILED()
└─ goodPhoto_returnsSUCCESS()
```

**Verification:**
```bash
rtk mvn test -Dtest='OcrValidatorTest,OcrValidatorIT'
# Expected: 8 tests ✓
```

---

### Step 2: Builder starts T2 (2-3 hours, depends on T1)
```
Create: OcrValidationQueueImpl (Async worker)
├─ Liquibase: 027-ocr-result-table.yaml
├─ OcrResult entity
├─ BlockingQueue + worker thread
├─ Enqueue → Process → Retry
└─ DB persistence

Tests:
├─ enqueue_acceptsJob()
├─ darkPhoto_marksVALIDACION_FALLIDA()
└─ goodPhoto_extractsOcr()
```

**Verification:**
```bash
rtk mvn test -Dtest='OcrValidationQueueIT'
# Expected: 3 tests ✓
```

---

### Step 3: Builder starts T3 (2-3 hours, depends on T1+T2)
```
Modify: EvidenciaResource
├─ POST /evidencias → 202 Accepted (not 201)
├─ POST /evidencias/{id}/revalidate (NEW)
├─ Enqueue on upload

Modify: EvidenciaResponse DTO
├─ validationStatus
├─ ocrExtracted
├─ validationErrors
└─ validatedAt

RLS: EvidenciaMapper
├─ OBSERVER → no ocrExtracted
└─ COORDINATOR → see all fields

Tests:
├─ subirEvidencia_devuelve202()
├─ revalidarEvidencia_coordinatorOK()
├─ revalidarEvidencia_observerForbidden()
└─ endToEnd_uploadGoodPhoto_eventuallyReturnsOcr()
```

**Verification:**
```bash
rtk mvn test -Dtest='EvidenciaOcrContractIT,EvidenciaOcrIT'
# Expected: 8+ tests ✓
```

---

## ✅ Acceptance Criteria (End-to-End)

| Criteria | How to Test | Expected Result |
|----------|------------|-----------------|
| **A1** | POST /evidencias returns 202 | Status = 202 Accepted |
| **A2** | validationStatus in response | "validating" (not null) |
| **A3** | Dark photo flagged | VALIDACION_FALLIDA + "brightness_too_low" |
| **A4** | Good photo processed | OCR text extracted, confidence > 0 |
| **A5** | OBSERVER can't see ocrExtracted | GET /evidencias/{id} → ocrExtracted = null |
| **A6** | COORDINATOR can revalidate | PATCH /evidencias/{id}/revalidate → 200 |
| **A7** | All results audited | OcrResult table populated |
| **A8** | No blocking | Upload returns <500ms |

---

## 🚀 Deployment Checklist

- [ ] T1 implemented + tests ✓
- [ ] T2 implemented + tests ✓
- [ ] T3 implemented + tests ✓
- [ ] All 3 ODDs tests pass locally
- [ ] Integration test passes (end-to-end)
- [ ] Contract tests pass (API changes)
- [ ] Manual QA: upload photo → see validation
- [ ] Manual QA: dark photo → VALIDACION_FALLIDA
- [ ] Manual QA: COORDINATOR → see OCR text
- [ ] Manual QA: OBSERVER → can't see OCR text
- [ ] Database migration applies cleanly
- [ ] No regressions in existing evidence tests
- [ ] Deploy to staging
- [ ] Deploy to production

---

## ⚠️ Risk Mitigation

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| OpenCV installation fails | Medium | Build breaks | Add to Dockerfile + CI setup |
| Tesseract missing languages | Low | OCR fails | Pre-install tesseract-ocr-spa |
| API change (201→202) breaks clients | Low | Client errors | Document breaking change |
| RLS visibility bug (OBSERVER sees OCR) | Low | Security leak | Contract test verifies RLS |
| OCR too slow (>5s) | Low | Workers pile up | Async + queue size limit |
| DB migration fails | Very Low | Rollback | Test migration locally first |

---

## 📞 Handoff to Architect

**Architect must review:**
1. ✅ D1-D6 design decisions
2. ✅ Schema (OcrResult table)
3. ✅ Permission model (evidencia:validate)
4. ✅ 6 open questions (design.md end)

**Architect feedback requested:**
- [ ] Approve D1-D6 decisions
- [ ] Is OcrResult table necessary?
- [ ] Confidence score usefulness?
- [ ] Language support (Spanish only)?
- [ ] Webhook notification vs polling?
- [ ] Photo preprocessing (enhance before OCR)?

**Once approved:**
→ Handoff to Builder to implement T1-T3

---

## 📞 Handoff to Builder

**Builder receives:**
1. ✅ 3 ODD tasks (T1, T2, T3)
2. ✅ Code examples + API specs
3. ✅ Test templates
4. ✅ DB migration SQL

**Builder implements sequentially:**
1. **T1** (1-2h) → OcrValidatorImpl
2. **T2** (2-3h) → OcrValidationQueueImpl + DB
3. **T3** (2-3h) → API endpoints + E2E

**Builder verifies:**
- All tests pass locally
- Integration test passes (end-to-end)
- Manual QA checklist complete
- No regressions

---

## 🎓 Key Learning Points

- **Async pattern:** T2 uses same BlockingQueue pattern as InvitationEmailQueueImpl
- **RLS:** T3 uses Autorizador.puede() to check permissions before exposing sensitive fields
- **State machine:** Evidencia now has VALIDANDO state (between upload and PENDIENTE)
- **202 Accepted:** Proper HTTP semantics for queued operations
- **Audit trail:** OcrResult table for compliance (CNE requirement)

---

## 📞 Questions?

If Builder has questions:
1. Check the ODD task specs (S1-S6)
2. Check design.md for technical details
3. Check test templates in each ODD
4. Ask Architect for design questions

---

**Next Step:** Architect reviews + approves → Builder starts T1

**ETA:** 1 day design + 2-3 days build = **~1 week to production**

---

**Documents generated:** 2026-11-25  
**Status:** Ready for review
