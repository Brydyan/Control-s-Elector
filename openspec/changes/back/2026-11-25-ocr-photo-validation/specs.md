# Technical Specifications — OCR Photo Validation

**Reference:** `proposal.md` (decisions D1-D6) + `design.md` (architecture)  
**Audience:** Architect (review) + Builder (implementation)  
**Scope:** Backend service only (frontend integration TBD)

---

## Functional Specifications

### S1 — Photo Quality Metrics (OpenCV)

**Requirement:** System must detect illegible photos before OCR to avoid wasted processing.

**Input:** BufferedImage (JPEG/PNG from S3)

**Output:** ValidationResult record
```java
record ValidationResult(
  boolean passed,
  List<String> errors,  // ["brightness_too_low", "contrast_too_low", "blur_detected"]
  Map<String, Object> metrics  // {brightness, contrast, blur_variance}
) {}
```

**Thresholds (configurable, hardcoded for MVP):**
| Metric | Threshold | Unit | Meaning |
|--------|-----------|------|---------|
| brightness | > -50 | -100 to 100 | Avg pixel intensity (black to white) |
| contrast | > 0.3 | 0 to 1 | Std dev of pixel values |
| blur | > 2.5 | — | Laplacian kernel variance |

**Errors (mutually exclusive OR combined):**
- `"brightness_too_low"` → brightness ≤ -50
- `"contrast_too_low"` → contrast ≤ 0.3
- `"blur_detected"` → blur_variance ≤ 2.5

**Example:**
```java
// Dark + blurry photo
ValidationResult result = new ValidationResult(
  false,
  ["brightness_too_low", "blur_detected"],
  {brightness: -60, contrast: 0.42, blur_variance: 1.8}
);
```

**Acceptance Criteria:**
- ✅ Bright photo (brightness -20, contrast 0.6, blur 4.0) → passed = true, errors = []
- ✅ Dark photo (brightness -60) → passed = false, errors contains "brightness_too_low"
- ✅ Low contrast (0.2) → passed = false, errors contains "contrast_too_low"
- ✅ Blurry (variance 1.8) → passed = false, errors contains "blur_detected"
- ✅ Multiple errors captured together
- ✅ Metrics always present (for audit trail)

---

### S2 — Async 202 Workflow

**Requirement:** Photo uploads must not block user (HTTP 202 semantics).

**Flow:**
```
1. POST /evidencias (upload photo + metadata)
   ├─ Store Blob in S3 (async via driver)
   ├─ Create Evidencia record (estado=VALIDANDO)
   ├─ Enqueue for OCR processing
   └─ Return 202 Accepted {validationStatus: "validando", evidenciaId: X}

2. [Background] OCR Worker (BlockingQueue)
   ├─ Download photo from S3
   ├─ Run S1 (quality check)
   ├─ If PASS: Run Tesseract OCR
   ├─ Store OcrResult {evidenciaId, raw_text, confidence, validation_status, errors}
   ├─ Update Evidencia (estado=PENDIENTE, ocrExtracted=raw_text)
   └─ If FAIL: Mark OcrResult {validation_status: "VALIDACION_FALLIDA", errors: [...]}

3. [Frontend] Poll for status (optional, polling)
   └─ GET /evidencias/{id} → {validationStatus: "pendiente", ocrExtracted: "..."}
```

**Acceptance Criteria:**
- ✅ POST /evidencias returns 202 (not 201)
- ✅ Response includes validationStatus = "validando"
- ✅ Response includes evidenciaId (for polling)
- ✅ No blocking on photo processing (user sees 202 <500ms)
- ✅ OCR processing happens async (2-5s typical)

---

### S3 — Validation Status State Machine

**Requirement:** Track photo validation lifecycle.

**States:**
```
Evidencia.validationStatus:
├─ "VALIDANDO" — Photo queued for OCR (after upload)
├─ "PENDIENTE" — Validation complete (success or failure)
└─ NULL — Pre-OCR feature (old Evidencias)

OcrResult.validation_status:
├─ "SUCCESS" — Photo passed + OCR extracted
├─ "BRIGHTNESS_TOO_LOW" — Brightness ≤ -50
├─ "CONTRAST_TOO_LOW" — Contrast ≤ 0.3
├─ "BLUR_DETECTED" — Blur variance ≤ 2.5
└─ "OCR_FAILED" — Tesseract error (rare)
```

**Transitions:**
```
NULL ──upload──> VALIDANDO ──(success + OCR)──> PENDIENTE
                     │
                     └──(validation failed)──> PENDIENTE
                     
Revalidate: PENDIENTE ──POST /revalidate──> VALIDANDO ──> PENDIENTE
```

**Acceptance Criteria:**
- ✅ Evidencia.validationStatus = VALIDANDO after upload
- ✅ Evidencia.validationStatus = PENDIENTE after worker completes
- ✅ OcrResult.validation_status records detailed reason
- ✅ State transitions are idempotent (revalidate can repeat)

---

### S4 — Retry Logic (Exponential Backoff)

**Requirement:** Transient failures (network, S3) should auto-retry.

**Backoff:**
```
Attempt 1: Immediate
Attempt 2: Wait 1s
Attempt 3: Wait 2s
Attempt 4: Wait 4s (3 max, so this doesn't happen)
Failure: Mark as OCR_FAILED, log error, alert ops
```

**Retryable errors:**
- S3 access timeout
- Network error downloading photo
- Tesseract subprocess timeout

**Non-retryable errors:**
- Photo validation failed (brightness, blur) — this is expected, not an error
- Invalid JPEG/PNG format
- Tesseract language not installed

**Acceptance Criteria:**
- ✅ Transient errors retry with backoff
- ✅ Max 3 attempts
- ✅ After 3 failures: OcrResult.validation_status = "OCR_FAILED"
- ✅ Error logged with attempt count + reason
- ✅ Non-retryable errors fail immediately

---

### S5 — RLS (Row-Level Security) — Visibility

**Requirement:** OBSERVER should not see OCR text (sensitive).

**Permission:** `evidencia:validate`

**Visibility Matrix:**
| Field | OBSERVER | COORDINATOR | MASTER |
|-------|----------|-------------|--------|
| validationStatus | ✓ Show | ✓ Show | ✓ Show |
| validationErrors | ✓ Show | ✓ Show | ✓ Show |
| ocrExtracted | ✗ NULL | ✓ Show | ✓ Show |
| confidenceScore | ✗ NULL | ✓ Show | ✓ Show |
| validatedAt | ✓ Show | ✓ Show | ✓ Show |

**Implementation:**
```java
// In EvidenciaMapper.toResponse()
if (autorizador.puede("evidencia:validate")) {
  response.setOcrExtracted(ocr.getRawText());
  response.setConfidenceScore(ocr.getConfidenceScore());
} else {
  response.setOcrExtracted(null);
  response.setConfidenceScore(null);
}
```

**Acceptance Criteria:**
- ✅ OBSERVER GET /evidencias/{id} → ocrExtracted = null
- ✅ COORDINATOR GET /evidencias/{id} → ocrExtracted = "extracted text"
- ✅ OBSERVER POST /revalidate → 403 Forbidden
- ✅ COORDINATOR POST /revalidate → 200 OK

---

### S6 — Audit Trail (Compliance)

**Requirement:** CNE compliance requires complete OCR audit trail.

**OcrResult Table Schema:**
```sql
CREATE TABLE ocr_result (
  id BIGINT PRIMARY KEY,
  evidencia_id BIGINT UNIQUE NOT NULL,
  raw_text LONGTEXT,                    -- Complete OCR output
  confidence_score DECIMAL(5,2),        -- Tesseract confidence [0, 100]
  validation_status VARCHAR(50),        -- SUCCESS, BRIGHTNESS_TOO_LOW, etc.
  errors JSON,                          -- {"brightness": -60, "contrast": 0.2, ...}
  processed_at TIMESTAMP DEFAULT NOW(), -- When OCR completed
  
  FOREIGN KEY (evidencia_id) REFERENCES evidencia(id) ON DELETE CASCADE
);
```

**Fields captured:**
- `raw_text` — Verbatim OCR output (never modified)
- `confidence_score` — Tesseract confidence (for statistical analysis)
- `validation_status` — Reason for acceptance/rejection
- `errors` — Diagnostic metrics (brightness, contrast, blur, reason codes)
- `processed_at` — Immutable timestamp

**Acceptance Criteria:**
- ✅ Every upload creates 1 OcrResult record (or NULL if validation fails immediately)
- ✅ OcrResult records are immutable (never updated)
- ✅ Revalidate creates new OcrResult record (old one preserved)
- ✅ audit.logs queries: `SELECT COUNT(*) FROM ocr_result WHERE processed_at > NOW() - INTERVAL 1 DAY`

---

## Non-Functional Specifications

### S7 — Performance Targets

| Metric | Target | Actual |
|--------|--------|--------|
| Upload return time | <500ms | [TBD] |
| OCR processing | <5s p99 | [TBD] |
| Quality validation | <1s | [TBD] |
| Tesseract OCR | <4s | [TBD] |
| DB persistence | <100ms | [TBD] |

**Acceptance Criteria:**
- ✅ POST /evidencias returns within 500ms
- ✅ OCR processes within 5s (p99) on typical hardware
- ✅ No UI blocking during OCR

---

### S8 — Dependencies (3rd Party)

| Dependency | Version | Purpose | License |
|------------|---------|---------|---------|
| OpenCV | 4.8.0 | Image processing (brightness, contrast, blur) | Apache 2.0 |
| Tesseract | 5+ | OCR text extraction | Apache 2.0 |
| tess4j | 5.9.0 | Tesseract Java wrapper | Apache 2.0 |

**Installation:**
```dockerfile
# Dockerfile
RUN apt-get install -y libopencv-dev tesseract-ocr tesseract-ocr-spa
```

**Acceptance Criteria:**
- ✅ CI Dockerfile includes all 3
- ✅ Maven build includes tess4j
- ✅ Native image reflection config includes OpenCV

---

### S9 — Error Handling & Logging

**Log Levels:**
```
INFO:  "OCR queue: processing evidencia_id={id}"
INFO:  "OCR result: validation_status={status}, confidence={score}%"
WARN:  "OCR retry attempt {N}: {error}"
ERROR: "OCR failed after 3 retries: {error}"
ERROR: "S3 access error: {error}"
```

**Acceptance Criteria:**
- ✅ All errors logged (never silent)
- ✅ Retry attempts logged
- ✅ Final failure logged with full stack trace
- ✅ Logs exportable to CloudWatch/ELK

---

### S10 — Security Considerations

**Data Classification:**
- OCR text (ocrExtracted) = SENSITIVE (PII from documents)
- Validation metrics = INTERNAL (brightness, etc.)

**Controls:**
- RLS enforces visibility (S5)
- API requires authentication + `evidencia:validate` for revalidate
- OCR results never cached externally (only DB)
- S3 photos encrypted at rest + in transit

**Acceptance Criteria:**
- ✅ OBSERVER never sees ocrExtracted (RLS verified)
- ✅ No logs contain OCR text
- ✅ API requires valid JWT token
- ✅ Revalidate requires explicit permission check

---

## Implementation Guidelines

### For OcrValidatorImpl (Task A)

**Input:** BufferedImage (from S3)  
**Output:** ValidationResult  
**No side effects:** Pure function, no DB access

```java
@ApplicationScoped
public class OcrValidatorImpl {
  public ValidationResult validateQuality(BufferedImage image) {
    // 1. Convert to grayscale if needed
    // 2. Calculate brightness (mean pixel value, -100 to 100)
    // 3. Calculate contrast (std dev, 0 to 1)
    // 4. Apply Laplacian kernel, calculate variance
    // 5. Check thresholds
    // 6. Collect errors
    // 7. Return ValidationResult
  }
}
```

**Unit Test Cases:**
- Dark photo → brightness_too_low
- Low contrast → contrast_too_low
- Blurry → blur_detected
- Good photo → passed
- Multiple errors → all captured
- Metrics captured → validation result includes map

---

### For OcrValidationQueueImpl (Task B)

**Architecture:**
```java
@ApplicationScoped
public class OcrValidationQueueImpl {
  private BlockingQueue<OcrJob> queue = new LinkedBlockingQueue<>(1000);
  private ExecutorService executor = Executors.newSingleThreadExecutor();
  
  public void enqueue(Long evidenciaId, String s3Uri) {
    queue.offer(new OcrJob(evidenciaId, s3Uri));
  }
  
  private void processJob(OcrJob job) {
    // 1. Download from S3
    // 2. Load into BufferedImage
    // 3. Call OcrValidatorImpl.validateQuality()
    // 4. If PASS: call Tesseract
    // 5. Store OcrResult
    // 6. Update Evidencia
    // 7. If FAIL: store OcrResult with status=FAILED
    // With retry backoff (1s, 2s, 4s)
  }
}
```

**DB Schema (Liquibase):**
```sql
CREATE TABLE ocr_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evidencia_id BIGINT UNIQUE,
  raw_text LONGTEXT,
  confidence_score DECIMAL(5,2),
  validation_status VARCHAR(50),
  errors JSON,
  processed_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (evidencia_id) REFERENCES evidencia(id) ON DELETE CASCADE
);
```

---

### For EvidenciaResource + RLS (Task C)

**API Changes:**
```java
// Before
@POST
public Response uploadEvidencia(@FormParam("file") InputStream file) {
  // 1. Validate
  // 2. Store in S3
  // 3. Create Evidencia (estado=PENDIENTE)
  // 4. Return 201 Created { evidenciaId, ... }
}

// After
@POST
public Response uploadEvidencia(@FormParam("file") InputStream file) {
  // 1. Validate
  // 2. Store in S3
  // 3. Create Evidencia (estado=VALIDANDO)  // Changed
  // 4. Enqueue for OCR  // New
  // 5. Return 202 Accepted { validationStatus: "validando", ... }  // Changed
}

// New endpoint
@POST
@Path("/{id}/revalidate")
public Response revalidateEvidencia(@PathParam("id") Long id) {
  // 1. Check permission: Autorizador.puede("evidencia:validate")
  // 2. If denied: return 403
  // 3. If allowed: Enqueue for re-OCR
  // 4. Return 200 { validationStatus: "validando" }
}
```

**RLS in EvidenciaMapper:**
```java
public EvidenciaResponse toResponse(Evidencia evidencia) {
  EvidenciaResponse response = new EvidenciaResponse();
  response.setValidationStatus(evidencia.getValidationStatus());
  response.setValidationErrors(evidencia.getValidationErrors());
  response.setValidatedAt(evidencia.getValidatedAt());
  
  // RLS: only expose OCR text if authorized
  if (autorizador.puede("evidencia:validate")) {
    OcrResult ocr = ocrResultRepository.findByEvidenciaId(evidencia.getId());
    if (ocr != null) {
      response.setOcrExtracted(ocr.getRawText());
      response.setConfidenceScore(ocr.getConfidenceScore());
    }
  } else {
    response.setOcrExtracted(null);
    response.setConfidenceScore(null);
  }
  
  return response;
}
```

---

## Acceptance Criteria Summary

| Category | Count | Status |
|----------|-------|--------|
| Functional | 20+ | [TBD] |
| Performance | 4 | [TBD] |
| Security | 4 | [TBD] |
| Deployment | 6 | [TBD] |

**All must be verified before Task D (Manual QA).**

---

**Document Version:** 1.0  
**Generated:** 2026-11-25  
**Status:** Ready for Architect + Builder review

