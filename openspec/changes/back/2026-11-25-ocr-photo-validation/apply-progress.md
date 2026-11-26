# OCR Photo Validation — Implementation Progress Log

**Feature:** OCR Photo Validation (Backend)  
**Branch:** `feature/ocr-evidence-validation`  
**Builder:** [Name]  
**Start Date:** [YYYY-MM-DD]  
**Target Completion:** [YYYY-MM-DD]

---

## Task A — Photo Quality Validation (OpenCV)

**Status:** ⏳ Pending  
**Effort:** 1-2h  
**Start:** [date]  
**End:** [date]  

### Checklist
- [ ] OcrValidatorImpl created (@ApplicationScoped)
- [ ] 3 validators implemented (brightness, contrast, blur)
- [ ] Thresholds configured (-50, 0.3, 2.5)
- [ ] ValidationResult record created
- [ ] Metric capture working (Map<String, Object>)
- [ ] 8 unit tests written + passing
- [ ] 1 integration test written + passing
- [ ] pom.xml updated (org.opencv:4.8.0)
- [ ] No regressions in existing tests

**Verification:**
```bash
rtk mvn test -Dtest='OcrValidatorTest,OcrValidatorIT'
# Expected: 9 tests ✓
```

**Issues:**
- [List any blockers or questions]

**Commit:** `[commit-hash]`  
**Evidence:** 9/9 tests ✓

---

## Task B — Async Queue + Persistence

**Status:** ⏳ Pending  
**Effort:** 2-3h  
**Start:** [date]  
**End:** [date]  
**Depends on:** Task A ✓

### Checklist
- [ ] OcrValidationQueueImpl created (@ApplicationScoped bean)
- [ ] BlockingQueue-based worker thread
- [ ] Download from S3 + OpenCV validation (uses T1)
- [ ] Tesseract OCR on pass
- [ ] Retry logic (1s, 2s, 4s exponential, 3 max)
- [ ] OcrResult entity created
- [ ] OcrResultRepository created
- [ ] Liquibase migration 027 created + tested
- [ ] Evidencia entity extended (validationStatus, validatedAt, ocrExtracted)
- [ ] 3 integration tests written + passing
- [ ] pom.xml updated (net.sourceforge.tess4j:5.9.0)
- [ ] No regressions

**Verification:**
```bash
rtk mvn test -Dtest='OcrValidationQueueIT'
# Expected: 3 tests ✓

liquibase validate
# Expected: clean
```

**Issues:**
- [List any blockers]

**Commit:** `[commit-hash]`  
**Evidence:** 3/3 tests ✓

---

## Task C — API Endpoints + RLS

**Status:** ⏳ Pending  
**Effort:** 2-3h  
**Start:** [date]  
**End:** [date]  
**Depends on:** Tasks A + B ✓

### Checklist
- [ ] EvidenciaResource modified (202 Accepted, enqueue)
- [ ] POST /evidencias/{id}/revalidate endpoint added
- [ ] EvidenciaResponse DTO extended (4 fields: validationStatus, ocrExtracted, errors, confidence, validatedAt)
- [ ] EvidenciaMapper RLS logic (OBSERVER no ocrExtracted, COORDINATOR all)
- [ ] Permission check: `Autorizador.puede("evidencia:validate")`
- [ ] 8+ contract tests written + passing
- [ ] E2E integration test written + passing
- [ ] RLS verified (OBSERVER can't see OCR, COORDINATOR can)
- [ ] Permission verify (OBSERVER can't revalidate, COORDINATOR can)
- [ ] No regressions

**Verification:**
```bash
rtk mvn test -Dtest='EvidenciaOcrContractIT,EvidenciaOcrIT'
# Expected: 8+ tests ✓

# Manual test
curl -X POST http://localhost:8080/api/evidencias \
  -F "file=@photo.jpg" \
  -H "Authorization: Bearer $TOKEN"
# Expected: 202 Accepted
```

**Issues:**
- [List any blockers]

**Commit:** `[commit-hash]`  
**Evidence:** 8+/8 tests ✓

---

## Task D — Manual QA

**Status:** ⏳ Pending  
**Effort:** 1h  
**Start:** [date]  
**End:** [date]  
**Depends on:** Tasks A-C ✓

### Checklist
- [ ] Photo upload → "validando" status
- [ ] Dark photo → "brightness_too_low" error
- [ ] Good photo → OCR text visible after 2-5s
- [ ] OBSERVER user → no ocrExtracted text
- [ ] COORDINATOR user → ocrExtracted text visible
- [ ] COORDINATOR → "Revalidar" works
- [ ] OBSERVER → "Revalidar" forbidden (403)
- [ ] Database OcrResult table has 10+ records
- [ ] Existing evidence upload still works
- [ ] Upload <500ms (not blocking)
- [ ] CI: all tests pass
- [ ] No new test failures

**Issues:**
- [List any findings]

**Sign-off:** [date] ✓

---

## Task E — Staging Deployment

**Status:** ⏳ Pending  
**Effort:** 30m  
**Start:** [date]  
**End:** [date]  
**Depends on:** Tasks A-D ✓

### Checklist
- [ ] CI pipeline passes
- [ ] Deploy to staging
- [ ] Dockerfile has OpenCV + Tesseract
- [ ] S3 access verified
- [ ] Contract tests pass on staging
- [ ] Feature flag enabled (10%)
- [ ] Error rates normal (<0.1%)
- [ ] Latency acceptable (<5s p99)
- [ ] Logs clean (no errors)

**Issues:**
- [List any findings]

**Sign-off:** [date] ✓

---

## Task F — Production Rollout

**Status:** ⏳ Pending  
**Effort:** 1h  
**Start:** [date]  
**End:** [date]  
**Depends on:** Tasks A-E ✓

### Checklist
- [ ] Feature flag: 100% rollout
- [ ] Error rates <0.1%
- [ ] OCR latency p99 <5s
- [ ] OcrResult table growing (>100 records / 1h)
- [ ] Existing functionality unaffected
- [ ] Breaking change documented (202 vs 201)
- [ ] Clients notified of API change
- [ ] Monitoring active

**Issues:**
- [List any findings]

**Sign-off:** [date] ✓

---

## Summary

**Total Effort:** [hours] (planned: 5-8h)  
**Actual:** [hours]  
**Blockers:** [count] resolved  
**Regressions:** [count] (expected: 0)  
**Tests Passing:** [count]/[total]  

**Timeline:**
- A: [actual] h
- B: [actual] h (depends A)
- C: [actual] h (depends B)
- D: [actual] h (depends C)
- E: [actual] h (depends D)
- F: [actual] h (depends E)

---

## Notes

- Each task has linked ODD in `/odd/tasks/`
- Read ODD specs before starting each task
- All tests must pass locally before push
- Contract tests verify no breaking changes
- RLS verified with OBSERVER + COORDINATOR roles
- DB migration dry-run before prod
- Monitor error rates post-deployment

---

**Next Steps After Completion:**
- [ ] Merge to develop
- [ ] Merge develop → main (release)
- [ ] Monitor production logs
- [ ] Gather user feedback

---

**Generated:** 2026-11-25  
**Last Updated:** [date]

