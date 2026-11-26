# ODD T2 — Tesseract OCR Processor (Async Worker)

**Reference:** `/openspec/changes/back/2026-11-25-ocr-photo-validation/proposal.md` (SDD D2, D6)  
**Depends on:** T1 (OcrValidatorImpl)  
**Status:** Ready for Builder  
**Effort:** 2-3 hours  

---

## Specs

### S1 — Async Queue Enqueue
EvidenciaService enqueues photo for OCR → job sits in queue  
**Acceptance:**
- OcrValidationQueueImpl.enqueue(evidenciaId, s3Key) adds job to BlockingQueue
- Queue size tracked via pendientes()
- Multiple jobs can be enqueued without blocking

### S2 — Background Worker Processing
Worker thread polls queue → processes jobs sequentially  
**Acceptance:**
- @PostConstruct starts daemon thread
- Thread polls queue every 5s
- Processes 1 job at a time (sequential)
- Errors don't crash worker

### S3 — Validation Flow (T1 integrated)
Worker downloads photo → runs OcrValidatorImpl.validateQuality() → stores result  
**Acceptance:**
- Download from S3 by s3Key
- Call validator.validateQuality(image)
- If validation fails → store errors in OcrResult, mark VALIDACION_FALLIDA
- No OCR if validation fails

### S4 — OCR Extraction (Tesseract)
Worker runs Tesseract → extracts text → stores in OcrResult  
**Acceptance:**
- If validation passes → call Tesseract.doOCR(image)
- Extract raw text (may be 10-50KB)
- Confidence score extracted
- Store in OcrResult.rawText + confidenceScore

### S5 — Result Persistence
Validation/OCR results stored in OcrResult table + Evidencia updated  
**Acceptance:**
- OcrResult table created: id, evidenciaId (unique), rawText, confidenceScore, validationStatus, errors (JSONB)
- Evidencia fields updated: validationStatus, ocrExtracted (cached), validatedAt
- Both persisted transactionally

### S6 — Retry Logic with Backoff
Processing fails → retry with exponential backoff (max 3)  
**Acceptance:**
- Catch Exception → retryCount++
- Wait exponential: 1s, 2s, 4s
- After 3 retries → mark evidencia validationStatus=error
- Log each retry attempt

### S7 — Audit Trail
All validation/OCR results audited for compliance  
**Acceptance:**
- OcrResult created for every processed photo
- Errors stored as JSONB (brightness, contrast, blur metrics)
- rawText captured (compliance requirement)
- Timestamp for when processed

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T2.1** | S1-S2, S6 | Create OcrValidationQueueImpl + worker thread | Inline |
| **T2.2** | S3-S5, S7 | Liquibase migration: ocr_result table + Evidencia fields | Inline |
| **T2.3** | S3-S7 | Integration test: enqueue → process → verify results | Inline |

---

## Implementation Checklist

### T2.1 — OcrValidationQueueImpl + Worker

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/evidencia/application/OcrValidationQueueImpl.java`

**Code structure:**
```java
@ApplicationScoped
public class OcrValidationQueueImpl implements OcrValidationQueue {
  
  private static final Logger log = Logger.getLogger(OcrValidationQueueImpl.class);
  private static final BlockingQueue<OcrValidationJob> queue = new LinkedBlockingQueue<>(1000);
  private static volatile Thread workerThread;
  
  @Inject OcrValidatorImpl validator;
  @Inject S3StorageService s3;
  
  @PostConstruct
  void startWorker() {
    workerThread = new Thread(this::processQueue);
    workerThread.setName("OcrValidationWorker");
    workerThread.setDaemon(false);
    workerThread.setPriority(Thread.NORM_PRIORITY - 1); // Lower priority
    workerThread.start();
    log.info("OcrValidationWorker started");
  }
  
  @PreDestroy
  void stopWorker() {
    if (workerThread != null && workerThread.isAlive()) {
      workerThread.interrupt();
    }
  }
  
  @Override
  public void enqueue(Long evidenciaId, String s3Key) {
    var job = new OcrValidationJob(evidenciaId, s3Key, 0, Instant.now());
    try {
      queue.put(job);
      log.debugf("Enqueued OCR for evidencia %d (pending: %d)", evidenciaId, queue.size());
    } catch (InterruptedException e) {
      log.warnf("Failed to enqueue OCR job for evidencia %d", evidenciaId);
      Thread.currentThread().interrupt();
    }
  }
  
  @Override
  public int pendientes() {
    return queue.size();
  }
  
  private void processQueue() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        OcrValidationJob job = queue.poll(5, TimeUnit.SECONDS);
        if (job != null) {
          processJob(job);
        }
      } catch (InterruptedException e) {
        log.info("OcrValidationWorker interrupted, shutting down");
        Thread.currentThread().interrupt();
        break;
      } catch (Exception e) {
        log.errorf(e, "Unexpected error in OCR processing loop");
      }
    }
  }
  
  private void processJob(OcrValidationJob job) {
    try {
      log.debugf("Processing OCR job for evidencia %d (attempt %d/3)", 
                 job.evidenciaId(), job.retryCount() + 1);
      
      // 1. Download from S3
      byte[] imageBytes = s3.download(job.s3Key());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
      
      // 2. Validate quality (T1 integration)
      ValidationResult validation = validator.validateQuality(image);
      
      if (!validation.passed()) {
        log.warnf("Photo validation failed for evidencia %d: %s",
                  job.evidenciaId(), validation.errors());
        storeValidationFailed(job.evidenciaId(), validation.errors(), validation.metrics());
        return; // Don't run OCR
      }
      
      // 3. Run Tesseract OCR
      OcrResult ocrResult = runOcr(image);
      
      // 4. Store success
      storeOcrSuccess(job.evidenciaId(), ocrResult, validation.metrics());
      
      log.infof("OCR completed for evidencia %d (confidence: %d%%)",
                job.evidenciaId(), ocrResult.confidenceScore());
      
    } catch (Exception e) {
      log.errorf(e, "OCR processing failed for evidencia %d (attempt %d/3)",
                 job.evidenciaId(), job.retryCount() + 1);
      retryOrFail(job);
    }
  }
  
  private OcrResult runOcr(BufferedImage image) throws TesseractException {
    ITesseract tesseract = new Tesseract();
    tesseract.setDatapath("/usr/share/tesseract-ocr/tessdata");
    tesseract.setLanguage("spa");
    tesseract.setPageSegMode(PSM.AUTO);
    
    String text = tesseract.doOCR(image);
    int confidence = extractConfidence(tesseract);
    
    return new OcrResult(text, confidence);
  }
  
  private int extractConfidence(ITesseract tesseract) {
    // Parse Tesseract confidence from output
    // For now, use fixed 85 as default (TODO: implement properly)
    return 85;
  }
  
  @Transactional
  private void storeValidationFailed(Long evidenciaId, List<String> errors, Map<String, Object> metrics) {
    // Update Evidencia
    Evidencia evidencia = Evidencia.findById(evidenciaId);
    if (evidencia == null) {
      log.warnf("Evidencia %d not found", evidenciaId);
      return;
    }
    evidencia.validationStatus = "failed";
    evidencia.estadoUsuario = "VALIDACION_FALLIDA";
    evidencia.persist();
    
    // Store OcrResult
    OcrResult result = new OcrResult();
    result.evidenciaId = evidenciaId;
    result.validationStatus = errors.get(0); // e.g., "brightness_too_low"
    result.errors = Json.encode(metrics);
    result.processedAt = Instant.now();
    result.persist();
    
    log.debugf("Stored validation failure for evidencia %d: %s", evidenciaId, errors);
  }
  
  @Transactional
  private void storeOcrSuccess(Long evidenciaId, OcrResult ocrResult, Map<String, Object> metrics) {
    // Update Evidencia
    Evidencia evidencia = Evidencia.findById(evidenciaId);
    if (evidencia == null) {
      log.warnf("Evidencia %d not found", evidenciaId);
      return;
    }
    evidencia.validationStatus = "success";
    evidencia.ocrExtracted = truncateText(ocrResult.text(), 100000); // 100KB max
    evidencia.validatedAt = Instant.now();
    evidencia.persist();
    
    // Store OcrResult
    OcrResult result = new OcrResult();
    result.evidenciaId = evidenciaId;
    result.rawText = ocrResult.text();
    result.confidenceScore = BigDecimal.valueOf(ocrResult.confidenceScore());
    result.validationStatus = "success";
    result.errors = Json.encode(metrics);
    result.processedAt = Instant.now();
    result.persist();
    
    log.debugf("Stored OCR success for evidencia %d (confidence: %d%%)",
               evidenciaId, ocrResult.confidenceScore());
  }
  
  private void retryOrFail(OcrValidationJob job) {
    if (job.retryCount() < 3) {
      var retryJob = new OcrValidationJob(
          job.evidenciaId(),
          job.s3Key(),
          job.retryCount() + 1,
          Instant.now().plus(Duration.ofSeconds((long) Math.pow(2, job.retryCount())))
      );
      
      try {
        queue.put(retryJob);
        log.infof("Requeued OCR job for evidencia %d (attempt %d/3)",
                  job.evidenciaId(), retryJob.retryCount() + 1);
      } catch (InterruptedException e) {
        log.errorf("Failed to requeue OCR job for evidencia %d", job.evidenciaId());
        Thread.currentThread().interrupt();
      }
    } else {
      // Max retries exhausted
      log.errorf("Max retries exceeded for evidencia %d", job.evidenciaId());
      
      Evidencia evidencia = Evidencia.findById(job.evidenciaId());
      if (evidencia != null) {
        evidencia.validationStatus = "error";
        evidencia.persist();
      }
    }
  }
  
  private String truncateText(String text, int maxLength) {
    return text.length() > maxLength ? text.substring(0, maxLength) : text;
  }
}

public record OcrValidationJob(
    Long evidenciaId,
    String s3Key,
    int retryCount,
    Instant createdAt
) {}

public record OcrResult(
    String text,
    int confidenceScore
) {}
```

**Port interface:**
```java
public interface OcrValidationQueue {
  void enqueue(Long evidenciaId, String s3Key);
  int pendientes();
}
```

---

### T2.2 — Liquibase Migration

**File:** `backend/electoral-api-quarkus/src/main/resources/db/changelog/027-ocr-result-table.yaml`

```yaml
databaseChangeLog:
  - changeSet:
      id: 027-create-ocr-result-table
      author: andy
      changes:
        - createTable:
            tableName: ocr_result
            columns:
              - column:
                  name: id
                  type: BIGINT
                  autoIncrement: true
                  constraints:
                    primaryKey: true
              - column:
                  name: evidencia_id
                  type: BIGINT
                  constraints:
                    nullable: false
                    unique: true
                    foreignKeyName: fk_ocr_result_evidencia
                    references: evidencia(id)
                    deleteCascade: true
              - column:
                  name: raw_text
                  type: LONGTEXT
              - column:
                  name: confidence_score
                  type: DECIMAL(5,2)
              - column:
                  name: validation_status
                  type: VARCHAR(50)
                  constraints:
                    nullable: false
              - column:
                  name: errors
                  type: JSON
              - column:
                  name: processed_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
                  defaultValueComputed: CURRENT_TIMESTAMP
              - column:
                  name: created_at
                  type: TIMESTAMP
                  constraints:
                    nullable: false
                  defaultValueComputed: CURRENT_TIMESTAMP
        
        - createIndex:
            indexName: idx_ocr_result_evidencia_status
            tableName: ocr_result
            columns:
              - column:
                  name: evidencia_id
              - column:
                  name: validation_status
  
  - changeSet:
      id: 027-alter-evidencia-add-validation-fields
      author: andy
      changes:
        - addColumn:
            tableName: evidencia
            columns:
              - column:
                  name: validation_status
                  type: VARCHAR(50)
                  defaultValue: "pending"
              - column:
                  name: ocr_extracted
                  type: LONGTEXT
              - column:
                  name: validated_at
                  type: TIMESTAMP
              - column:
                  name: validation_error_count
                  type: INT
                  defaultValue: 0
```

---

### T2.3 — Integration Test

**File:** `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/evidencia/application/OcrValidationQueueIT.java`

```java
@QuarkusTest
public class OcrValidationQueueIT {
  
  @Inject OcrValidationQueue ocrQueue;
  @Inject EvidenciaRepository evidenciaRepo;
  @Inject OcrResultRepository ocrResultRepo;
  
  @Test
  void enqueue_acceptsJob() {
    ocrQueue.enqueue(123L, "evidencias/abc-123.webp");
    
    assertThat(ocrQueue.pendientes()).isGreaterThan(0);
  }
  
  @Test
  void processQueue_darkPhoto_marksVALIDACION_FALLIDA() throws Exception {
    // Setup: Create Evidencia
    Evidencia evidencia = createTestEvidencia(123L, "dark_photo.webp");
    
    // Enqueue
    ocrQueue.enqueue(evidencia.getId(), "evidencias/dark_photo.webp");
    
    // Wait for processing (with timeout)
    waitFor(() -> ocrQueue.pendientes() == 0, Duration.ofSeconds(10));
    
    // Verify: Evidencia updated
    Evidencia updated = evidenciaRepo.findById(evidencia.getId());
    assertThat(updated.validationStatus).isEqualTo("failed");
    assertThat(updated.estadoUsuario).isEqualTo("VALIDACION_FALLIDA");
    
    // Verify: OcrResult stored
    OcrResult result = ocrResultRepo.findByEvidenciaId(evidencia.getId());
    assertThat(result).isNotNull();
    assertThat(result.validationStatus).isEqualTo("brightness_too_low");
    assertThat(result.errors).containsKey("brightness");
  }
  
  @Test
  void processQueue_goodPhoto_extractsOcr() throws Exception {
    // Setup: Create Evidencia
    Evidencia evidencia = createTestEvidencia(456L, "good_photo.webp");
    
    // Enqueue
    ocrQueue.enqueue(evidencia.getId(), "evidencias/good_photo.webp");
    
    // Wait for processing
    waitFor(() -> ocrQueue.pendientes() == 0, Duration.ofSeconds(10));
    
    // Verify: Evidencia updated
    Evidencia updated = evidenciaRepo.findById(evidencia.getId());
    assertThat(updated.validationStatus).isEqualTo("success");
    assertThat(updated.ocrExtracted).isNotEmpty();
    assertThat(updated.validatedAt).isNotNull();
    
    // Verify: OcrResult stored
    OcrResult result = ocrResultRepo.findByEvidenciaId(evidencia.getId());
    assertThat(result).isNotNull();
    assertThat(result.validationStatus).isEqualTo("success");
    assertThat(result.rawText).isNotEmpty();
    assertThat(result.confidenceScore).isGreaterThan(0);
  }
  
  private Evidencia createTestEvidencia(Long id, String s3Key) {
    Evidencia e = new Evidencia();
    e.setId(id);
    e.setS3Key(s3Key);
    e.setActaId(999L);
    e.setJrvId(1L);
    e.setCargoId(1L);
    e.setUsuarioId(1L);
    e.setContentType("image/webp");
    e.setSizeBytes(1000000);
    e.persist();
    return e;
  }
  
  private void waitFor(BooleanSupplier condition, Duration timeout) throws InterruptedException {
    Instant deadline = Instant.now().plus(timeout);
    while (!condition.getAsBoolean() && Instant.now().isBefore(deadline)) {
      Thread.sleep(100);
    }
    if (!condition.getAsBoolean()) {
      throw new AssertionError("Timeout waiting for condition");
    }
  }
}
```

---

## Verification

**All pass locally:**
```bash
rtk mvn test -Dtest='OcrValidationQueueIT'
# Expected: 3 tests pass, 0 failures
# Logs should show: "OcrValidationWorker started"
```

**DB migration works:**
```bash
rtk mvn db:status -Dflyway.baselineVersion=026
# Expected: No pending migrations after 027
```

**No regressions:**
- Existing evidence tests still pass
- Worker doesn't interfere with other services

---

## Notes

- T2 integrates T1 (OcrValidatorImpl)
- T2 creates async infrastructure (BlockingQueue + worker thread)
- T2 persists to BD (OcrResult table + Evidencia fields)
- T2 is **still not exposed via API** (done in T3)
- Next: T3 integrates into EvidenciaResource endpoint

---

**Risk:** Medium (async worker + DB migration)
