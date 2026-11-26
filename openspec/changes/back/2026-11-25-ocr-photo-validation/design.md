# Design Document — OCR Photo Validation

## Decisiones Alternativas Consideradas

### D1 Alternative: Cloud-based OCR (Google Vision API)

**Propuesta Alt:**
```
POST /evidencias?actaId=X
  → Send to Google Vision API (async)
  → 202 Accepted
  → More accurate OCR (95%+)
```

**Pros:**
- Better OCR accuracy (95%+ vs 85-90% local)
- Google handles infrastructure
- Mature, battle-tested API

**Contras:**
- Cost: €0.6 per 1000 images → €600 per 1M images
- Privacy: images leave infrastructure
- Network latency: 500ms + retry overhead
- Compliance: CNE might not allow external API
- Over-engineering for initial use case

**Decision:** ❌ Rejected  
**Why:** Local is free, private, and "good enough" for flagging. Can upgrade later.

---

### D1 Alternative: Blocking OCR (Wait for validation before 201)

**Propuesta Alt:**
```
POST /evidencias?actaId=X
  → Validate synchronously (3-5s wait)
  → If pass → 201 Created
  → If fail → 400 Bad Request ("too dark, retake")
```

**Pros:**
- No async complexity
- Immediate feedback
- Prevents bad photos from ever being stored

**Contras:**
- 3-5s timeout per photo (bad UX)
- If OCR fails → entire upload fails (retry loop)
- N photos = N * 5s (sequential)
- Scales poorly (high latency endpoints)

**Decision:** ❌ Rejected  
**Why:** 202 Async is better UX. Photos might be legible even if "dark" (conservative approach).

---

### D3 Alternative: Auto-reject VALIDACION_FALLIDA

**Propuesta Alt:**
```
Photo too dark → estado = RECHAZADA (auto)
User can't approve it, must retake
```

**Pros:**
- Prevents bad photos completely
- Stricter quality gate

**Contras:**
- Some "dark" photos ARE readable with contrast boost
- User frustration (rejected without chance to review)
- System decides, human doesn't approve (wrong authority model)

**Decision:** ❌ Rejected  
**Why:** Conservative: flag and let human decide. Respect reviewer authority.

---

### D4 Alternative: OCR only for specific roles

**Propuesta Alt:**
```
Only COORDINATOR+ can trigger OCR
OBSERVER just uploads, coordinator validates later
```

**Pros:**
- Saves CPU (fewer validations)
- Clearer permission model

**Contras:**
- Delays feedback to observer (bad UX)
- Observer doesn't know photo is unusable until later
- More manual work (coordinator validates everything)

**Decision:** ❌ Rejected  
**Why:** Automatic for all uploads. Observer gets instant feedback. Coordinator still reviews.

---

### D5 Alternative: Tesseract in separate Python service

**Propuesta Alt:**
```
Backend (Java) → HTTP call → Python microservice (Tesseract)
```

**Pros:**
- Python has better ML/image libs
- Decouple OCR from main app

**Contras:**
- More infrastructure to manage
- Network overhead (HTTP round-trip)
- Deployment complexity (2 services)
- Not worth it for simple Tesseract wrapper

**Decision:** ❌ Rejected  
**Why:** OpenCV + Tesseract Java are good enough. Keep it simple.

---

## Arquitectura Detallada

### Database Schema

```sql
-- New table: OCR extraction results
CREATE TABLE ocr_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evidencia_id BIGINT NOT NULL UNIQUE,
  raw_text LONGTEXT,                    -- Full OCR extraction (may be 10-50KB)
  confidence_score DECIMAL(5, 2),       -- 0-100, Tesseract confidence
  validation_status VARCHAR(50) NOT NULL, -- success|brightness_low|contrast_low|blur|angle|error
  errors JSON,                          -- {"brightness": -15, "contrast": 0.42, "blur_variance": 2.1}
  processed_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (evidencia_id) REFERENCES evidencia(id) ON DELETE CASCADE,
  INDEX idx_evidencia_status (evidencia_id, validation_status)
);

-- Modify existing table: Evidencia
ALTER TABLE evidencia ADD COLUMN (
  validation_status VARCHAR(50),        -- pending|validating|success|failed|retry
  ocr_extracted LONGTEXT,               -- Cached from ocr_result.raw_text for quick reads
  validated_at TIMESTAMP,
  validation_error_count INT DEFAULT 0
);

-- New queue table (optional, for durability)
CREATE TABLE ocr_validation_queue (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evidencia_id BIGINT NOT NULL,
  s3_key VARCHAR(255) NOT NULL,
  retry_count INT DEFAULT 0,
  max_retries INT DEFAULT 3,
  next_attempt_at TIMESTAMP,
  error_message TEXT,
  created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
  FOREIGN KEY (evidencia_id) REFERENCES evidencia(id) ON DELETE CASCADE,
  INDEX idx_next_attempt (next_attempt_at)
);
```

### Request/Response DTOs

**EvidenciaResponse (modified):**
```java
@RegisterForReflection
public record EvidenciaResponse(
    UUID id,
    String s3Key,
    
    // Current fields
    String estado,                  // PENDIENTE|APROBADA|RECHAZADA|VALIDANDO|VALIDACION_FALLIDA
    String contentType,
    long sizeBytes,
    
    // NEW fields (D2, D3, D4)
    String validationStatus,        // pending|validating|success|failed|retry
    String ocrExtracted,            // Full text extracted by Tesseract
    List<String> validationErrors,  // ["brightness_too_low", "contrast_too_low", ...]
    Integer confidenceScore,        // 0-100 from Tesseract
    Instant validatedAt,
    
    // Existing fields
    Instant creadoEn,
    Instant actualizadoEn
) {}
```

**New DTO for revalidation request:**
```java
@RegisterForReflection
public record RevalidateEvidenciaRequest(
    @NotNull UUID evidenciaId
) {}
```

---

### Service Layer

**OcrValidatorImpl:**
```java
@ApplicationScoped
public class OcrValidatorImpl {
  
  private static final Logger log = Logger.getLogger(OcrValidatorImpl.class);
  
  // Thresholds (tunable)
  private static final int BRIGHTNESS_THRESHOLD = -50;      // -100 to 100, below is "too dark"
  private static final double CONTRAST_THRESHOLD = 0.3;     // below is "too low"
  private static final double BLUR_VARIANCE_THRESHOLD = 2.5; // below is "too blurry"
  
  /**
   * Validate photo quality using OpenCV.
   * Returns ValidationResult with errors and pass/fail.
   */
  public ValidationResult validateQuality(BufferedImage image) {
    var errors = new ArrayList<String>();
    
    // 1. Brightness check
    int brightness = calculateAverageBrightness(image);
    if (brightness < BRIGHTNESS_THRESHOLD) {
      errors.add("brightness_too_low");
    }
    
    // 2. Contrast check
    double contrast = calculateContrast(image);
    if (contrast < CONTRAST_THRESHOLD) {
      errors.add("contrast_too_low");
    }
    
    // 3. Blur detection (Laplacian variance)
    double blurVariance = calculateBlurVariance(image);
    if (blurVariance < BLUR_VARIANCE_THRESHOLD) {
      errors.add("blur_detected");
    }
    
    // 4. Angle detection (optional, TBD)
    // double angle = detectSkew(image);
    // if (Math.abs(angle) > 10) errors.add("angle_skewed");
    
    return new ValidationResult(
        errors.isEmpty(),
        errors,
        Map.of(
            "brightness", brightness,
            "contrast", contrast,
            "blur_variance", blurVariance
        )
    );
  }
  
  /**
   * Run Tesseract OCR on photo.
   * Returns extracted text + confidence score.
   */
  public OcrResult runOcr(BufferedImage image) {
    try {
      ITesseract tesseract = new Tesseract();
      tesseract.setDatapath("/usr/share/tesseract-ocr/tessdata"); // Spanish data
      tesseract.setLanguage("spa");                              // Spanish language
      
      String text = tesseract.doOCR(image);
      
      // Get confidence (Tesseract mean confidence)
      // Note: requires parsing from Tesseract output
      int confidence = extractConfidence(tesseract);
      
      return new OcrResult(text, confidence);
    } catch (TesseractException e) {
      log.errorf(e, "Tesseract failed");
      throw new OcrProcessingException("OCR failed", e);
    }
  }
  
  private int calculateAverageBrightness(BufferedImage image) {
    // Convert to grayscale, calculate mean pixel value
    // -100 (black) to 100 (white)
    // ...implementation
    return 0;
  }
  
  private double calculateContrast(BufferedImage image) {
    // Calculate standard deviation of pixel values
    // 0 (all same color) to 1 (max variance)
    // ...implementation
    return 0.0;
  }
  
  private double calculateBlurVariance(BufferedImage image) {
    // Apply Laplacian kernel, calculate variance
    // Higher = less blurry, lower = more blurry
    // ...implementation
    return 0.0;
  }
}

public record ValidationResult(
    boolean passed,
    List<String> errors,
    Map<String, Object> metrics
) {}

public record OcrResult(
    String text,
    int confidenceScore
) {}
```

**OcrValidationQueueImpl (async worker):**
```java
@ApplicationScoped
public class OcrValidationQueueImpl implements OcrValidationQueue {
  
  private static final Logger log = Logger.getLogger(OcrValidationQueueImpl.class);
  private static final BlockingQueue<OcrValidationJob> queue = new LinkedBlockingQueue<>(1000);
  
  @Inject OcrValidatorImpl validator;
  @Inject S3StorageService s3;
  @Inject OcrResultRepository ocrResults;
  
  @PostConstruct
  void startWorker() {
    // Start background worker thread
    Thread workerThread = new Thread(this::processQueue);
    workerThread.setName("OcrValidationWorker");
    workerThread.setDaemon(false);
    workerThread.start();
  }
  
  @Override
  public void enqueue(Long evidenciaId, String s3Key) {
    var job = new OcrValidationJob(evidenciaId, s3Key, 0);
    try {
      queue.put(job); // Blocks if queue full
      log.debugf("Enqueued OCR for evidencia %d", evidenciaId);
    } catch (InterruptedException e) {
      log.warnf("Failed to enqueue OCR job: %s", e.getMessage());
      Thread.currentThread().interrupt();
    }
  }
  
  private void processQueue() {
    while (!Thread.currentThread().isInterrupted()) {
      try {
        OcrValidationJob job = queue.poll(5, TimeUnit.SECONDS);
        if (job != null) {
          processJob(job);
        }
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }
  }
  
  private void processJob(OcrValidationJob job) {
    try {
      // 1. Download from S3
      byte[] imageBytes = s3.download(job.s3Key());
      BufferedImage image = ImageIO.read(new ByteArrayInputStream(imageBytes));
      
      // 2. Validate quality (OpenCV)
      ValidationResult validation = validator.validateQuality(image);
      
      // 3. Store validation result
      if (!validation.passed()) {
        log.warnf("Photo validation failed for evidencia %d: %s", 
                  job.evidenciaId(), validation.errors());
        storeValidationFailed(job.evidenciaId(), validation.errors(), validation.metrics());
        return; // Don't run OCR
      }
      
      // 4. Run OCR (if validation passed)
      OcrResult ocrResult = validator.runOcr(image);
      
      // 5. Store OCR result
      storeOcrSuccess(job.evidenciaId(), ocrResult);
      
      log.infof("OCR completed for evidencia %d (confidence: %d%%)", 
                job.evidenciaId(), ocrResult.confidenceScore());
      
    } catch (Exception e) {
      log.errorf(e, "OCR processing failed for evidencia %d", job.evidenciaId());
      retryOrFail(job);
    }
  }
  
  private void storeValidationFailed(Long evidenciaId, List<String> errors, Map<String, Object> metrics) {
    // Update Evidencia: estado=VALIDACION_FALLIDA, validationStatus=failed
    // Store in OcrResult: validation_status=failed, errors=JSON
    Evidencia evidencia = Evidencia.findById(evidenciaId);
    evidencia.validationStatus = "failed";
    evidencia.estadoUsuario = "VALIDACION_FALLIDA";
    evidencia.persist();
    
    OcrResult result = new OcrResult();
    result.evidenciaId = evidenciaId;
    result.validationStatus = errors.get(0); // e.g., "brightness_too_low"
    result.errors = Json.encode(metrics);
    result.persist();
  }
  
  private void storeOcrSuccess(Long evidenciaId, OcrResult ocrResult) {
    // Update Evidencia: estado=PENDIENTE, validationStatus=success, ocrExtracted=text
    Evidencia evidencia = Evidencia.findById(evidenciaId);
    evidencia.validationStatus = "success";
    evidencia.ocrExtracted = ocrResult.text().substring(0, Math.min(100000, ocrResult.text().length()));
    evidencia.validatedAt = Instant.now();
    evidencia.persist();
    
    OcrResult result = new OcrResult();
    result.evidenciaId = evidenciaId;
    result.rawText = ocrResult.text();
    result.confidenceScore = BigDecimal.valueOf(ocrResult.confidenceScore());
    result.validationStatus = "success";
    result.persist();
  }
  
  private void retryOrFail(OcrValidationJob job) {
    if (job.retryCount() < 3) {
      var retryJob = new OcrValidationJob(
          job.evidenciaId(), 
          job.s3Key(), 
          job.retryCount() + 1
      );
      // Re-enqueue with exponential backoff
      try {
        Thread.sleep((long) Math.pow(2, job.retryCount()) * 1000); // 1s, 2s, 4s
        queue.put(retryJob);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }
    } else {
      // Max retries exhausted
      log.errorf("Max retries exceeded for evidencia %d", job.evidenciaId());
      Evidencia evidencia = Evidencia.findById(job.evidenciaId());
      evidencia.validationStatus = "error";
      evidencia.persist();
    }
  }
  
  @Override
  public int pendientes() {
    return queue.size();
  }
}

public record OcrValidationJob(
    Long evidenciaId,
    String s3Key,
    int retryCount
) {}
```

---

### Endpoint Changes

**EvidenciaResource.subir() — MODIFIED:**
```java
@POST
@Consumes(MediaType.MULTIPART_FORM_DATA)
public Response subir(
    @RestForm("file") FileUpload archivo,
    @QueryParam("actaId") Long actaId,
    @HeaderParam("X-Forwarded-For") String xForwardedFor) {
  
  if (archivo == null || archivo.filePath() == null) {
    throw new EvidenciaInvalidaException("El archivo 'file' es obligatorio");
  }
  if (actaId == null) {
    throw new EvidenciaInvalidaException("El parametro 'actaId' es obligatorio");
  }

  autorizador.exigir("evidencia:upload");

  EvidenciaResponse dto = service.subir(leer(archivo), actaId, xForwardedFor);
  
  // CHANGE: Return 202 Accepted (not 201 Created)
  return Response.status(Response.Status.ACCEPTED).entity(dto).build();
}
```

**New endpoint: Revalidate photo (manual retry):**
```java
@PATCH
@Path("/{id}/revalidate")
public Response revalidate(@PathParam("id") UUID evidenciaId) {
  autorizador.exigir("evidencia:validate");  // NEW permission
  
  Evidencia evidencia = consultarEvidenciaUseCase.get(evidenciaId);
  
  // Re-enqueue for OCR validation
  ocrValidationQueue.enqueue(evidencia.getId(), evidencia.getS3Key());
  
  Evidencia updated = Evidencia.find("publicId", evidenciaId).firstResult();
  updated.validationStatus = "validating";
  updated.persist();
  
  return Response.ok(mapper.toDto(updated)).build();
}
```

---

### Dependencies (Maven pom.xml)

```xml
<!-- OpenCV for image analysis -->
<dependency>
  <groupId>org.opencv</groupId>
  <artifactId>opencv-java</artifactId>
  <version>4.8.0</version>
</dependency>

<!-- Tesseract OCR (Java wrapper) -->
<dependency>
  <groupId>net.sourceforge.tess4j</groupId>
  <artifactId>tess4j</artifactId>
  <version>5.9.0</version>
</dependency>

<!-- JSON processing for JSONB errors -->
<!-- (already in Quarkus) -->
```

**Runtime deps:**
```bash
# Ubuntu/Debian
apt-get install tesseract-ocr tesseract-ocr-spa libopencv-dev

# macOS
brew install tesseract opencv
```

---

## Configuración

**application.properties:**
```properties
# OCR configuration
app.ocr.enabled=true
app.ocr.brightness-threshold=-50
app.ocr.contrast-threshold=0.3
app.ocr.blur-variance-threshold=2.5
app.ocr.max-retries=3
app.ocr.retry-backoff-ms=1000

# Tesseract path
app.ocr.tesseract-data=/usr/share/tesseract-ocr/tessdata
app.ocr.tesseract-language=spa
```

---

## Risk & Mitigations

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| OCR too slow (5s+) | Medium | Users wait | Async + background worker |
| Tesseract false positives | Medium | Reject good photos | Manual revalidate endpoint |
| Memory bloat (large OCR text) | Low | OOM crashes | Limit raw_text to 100KB |
| S3 download fails | Low | OCR fails | Retry with exponential backoff |
| New permissions confusing | Low | RBAC issues | Clear docs + tests |

---

## Success Criteria

✅ Photos upload returns 202 (not 201)  
✅ Dark photos flagged as VALIDACION_FALLIDA  
✅ OCR text extracted for all valid photos  
✅ COORDINATOR can revalidate manually  
✅ All validation results audited in BD  
✅ No blocking on main request thread  
✅ Contract tests pass (new 202 response)

---

## Performance Targets

| Operation | Target | Acceptable |
|-----------|--------|------------|
| POST /evidencias (sync upload check) | <200ms | <500ms |
| OCR validation (async) | <5s per photo | <10s |
| Batch 100 photos | ~8 min | ~15 min |
| Memory per photo | <50MB | <100MB |

---

## Open Questions for Architect

1. **Is OcrResult table necessary?** Could we just update Evidencia + audit log?
2. **Confidence score usefulness?** Worth extracting from Tesseract output?
3. **Language support?** Only Spanish, or multi-lang (ENG, FR, PT)?
4. **Batch revalidation?** Should we have endpoint to revalidate N photos (compliance audit)?
5. **Webhook notification?** Should frontend get push when validation completes (vs polling)?
6. **Photo preprocessing?** Should we enhance contrast before OCR (or just validate)?

---

**Next:** Design review + stakeholder approval → move to 3 ODD tasks
