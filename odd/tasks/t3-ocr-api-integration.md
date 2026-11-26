# ODD T3 — API Integration + End-to-End

**Reference:** `/openspec/changes/back/2026-11-25-ocr-photo-validation/proposal.md` (SDD D3, D4)  
**Depends on:** T1 (OcrValidatorImpl) + T2 (OcrValidationQueueImpl)  
**Status:** Ready for Builder  
**Effort:** 2-3 hours  

---

## Specs

### S1 — Change POST /evidencias to 202 Accepted
Currently returns 201 Created. Must return 202 Accepted (processing queued)  
**Acceptance:**
- POST /evidencias?actaId=X multipart file
- Response: 202 Accepted (not 201)
- Body includes new fields: validationStatus, ocrExtracted, validationErrors
- S3 file is stored, DB record created with estado=VALIDANDO

### S2 — New Permission evidencia:validate
COORDINATOR/MASTER can see OCR results  
**Acceptance:**
- New permission string: "evidencia:validate"
- COORDINATOR, MASTER, ADMIN have this permission
- Used in: GET /evidencias/{id}, POST /evidencias/{id}/revalidate
- OBSERVER does NOT have this permission

### S3 — EvidenciaResponse DTO Extended
Add validation fields to response  
**Acceptance:**
- validationStatus: "pending" | "validating" | "success" | "failed" | "retry"
- ocrExtracted: String (NULL until success)
- validationErrors: List<String> (empty if success)
- confidenceScore: Integer (0-100, NULL until success)
- validatedAt: Instant (NULL until processed)

### S4 — New Endpoint: POST /evidencias/{id}/revalidate
COORDINATOR+ can manually revalidate photo  
**Acceptance:**
- Requires permission evidencia:validate
- Re-enqueues photo for OCR validation
- Sets validationStatus=validating
- Returns updated EvidenciaResponse

### S5 — GET /evidencias/{id} Includes Validation Status
Users can check photo validation progress  
**Acceptance:**
- GET /evidencias/{id} includes validationStatus
- OBSERVER sees validationStatus but NOT ocrExtracted
- COORDINATOR+ see both validationStatus + ocrExtracted

### S6 — Contract Tests: 202 Response
Contract tests verify new 202 response  
**Acceptance:**
- POST /evidencias returns 202 (not 201)
- GET /evidencias/{id} returns validationStatus in response
- Revalidate endpoint works (COORDINATOR only)

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T3.1** | S1-S3 | Update EvidenciaResponse DTO + EvidenciaService | Inline |
| **T3.2** | S1 | Modify EvidenciaResource.subir() → 202 Accepted | Inline |
| **T3.3** | S2, S4 | Add evidencia:validate permission + revalidate endpoint | Inline |
| **T3.4** | S3, S5 | Modify EvidenciaMapper: RLS-aware visibility of OCR fields | Inline |
| **T3.5** | S6 | Contract + integration tests (202, permissions, revalidate) | Inline |

---

## Implementation Checklist

### T3.1 — EvidenciaResponse DTO Extension

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/evidencia/web/dto/EvidenciaResponse.java`

**Current:**
```java
public record EvidenciaResponse(
    UUID id,
    String s3Key,
    String estado,
    String contentType,
    long sizeBytes,
    Instant creadoEn,
    Instant actualizadoEn
) {}
```

**Modified:**
```java
@RegisterForReflection
public record EvidenciaResponse(
    UUID id,
    String s3Key,
    
    // Current fields
    String estado,
    String contentType,
    long sizeBytes,
    
    // NEW: OCR Validation fields (D3, S3)
    String validationStatus,          // pending | validating | success | failed | retry | error
    String ocrExtracted,              // Full text (NULL if validation failed or still processing)
    List<String> validationErrors,    // ["brightness_too_low", "contrast_too_low", ...] - empty if success
    Integer confidenceScore,          // 0-100 (NULL if not yet processed)
    Instant validatedAt,              // When validation completed (NULL if still processing)
    
    // Current fields (kept at end for compatibility)
    Instant creadoEn,
    Instant actualizadoEn
) {}
```

**EvidenciaService modified:**
```java
@Transactional
public EvidenciaResponse subir(ArchivoEvidencia archivo, Long actaId, String xForwardedFor) {
  Long usuarioId = contexto.usuarioId();
  rls.activar(usuarioId);

  Evidencia evidencia =
      subirEvidenciaUseCase.execute(
          archivo, actaId, usuarioId, currentUser.rol(), contexto.ip(xForwardedFor));

  // Enqueue OCR validation (NEW - T2 integration)
  ocrValidationQueue.enqueue(evidencia.getId(), evidencia.getS3Key());
  
  // Update state to VALIDANDO (NEW)
  evidencia.validationStatus = "validating";
  evidencia.persist();

  return mapper.toDto(evidencia);
}
```

---

### T3.2 — EvidenciaResource.subir() → 202 Accepted

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/evidencia/web/EvidenciaResource.java`

**Current:**
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
  
  // CHANGE: 202 Accepted instead of 201 Created
  return Response.status(Response.Status.ACCEPTED).entity(dto).build();
}
```

---

### T3.3 — New Permission + Revalidate Endpoint

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/evidencia/web/EvidenciaResource.java`

**New endpoint:**
```java
/**
 * Manually revalidate a photo (requires evidencia:validate permission).
 * Useful for COORDINATOR/MASTER to trigger OCR re-processing.
 */
@PATCH
@Path("/{id}/revalidate")
public Response revalidate(@PathParam("id") UUID evidenciaId) {
  autorizador.exigir("evidencia:validate"); // NEW permission (S2)
  
  rls.activar(contexto.usuarioId());
  
  Evidencia evidencia = consultarEvidenciaUseCase.get(evidenciaId);
  
  // Re-enqueue for validation
  ocrValidationQueue.enqueue(evidencia.getId(), evidencia.getS3Key());
  
  // Reset validation state
  evidencia.validationStatus = "validating";
  evidencia.ocrExtracted = null; // Clear previous result
  evidencia.validatedAt = null;
  evidencia.persist();
  
  return Response.ok(mapper.toDto(evidencia))
      .header("X-Action", "revalidate_queued")
      .build();
}
```

**Permission declaration (Autorizador.java or permissions config):**
```java
// New permission for OCR operations
"evidencia:validate"  // See OCR results, revalidate photos
```

**Permission mapping (by role):**
| Role | Permissions |
|------|------------|
| OBSERVER | evidencia:upload |
| COORDINATOR | evidencia:upload, evidencia:validate |
| MASTER | evidencia:upload, evidencia:validate |
| ADMIN | evidencia:upload, evidencia:validate |

---

### T3.4 — EvidenciaMapper: RLS-Aware Visibility

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/evidencia/web/dto/mapper/EvidenciaMapper.java`

**Current:**
```java
@ApplicationScoped
public class EvidenciaMapper {
  
  public EvidenciaResponse toDto(Evidencia evidencia) {
    return new EvidenciaResponse(
        evidencia.getPublicId(),
        evidencia.getS3Key(),
        evidencia.getEstado(),
        evidencia.getContentType(),
        evidencia.getSizeBytes(),
        evidencia.getCreadoEn(),
        evidencia.getActualizadoEn()
    );
  }
}
```

**Modified (with RLS visibility):**
```java
@ApplicationScoped
public class EvidenciaMapper {
  
  @Inject Autorizador autorizador;
  
  public EvidenciaResponse toDto(Evidencia evidencia) {
    // RLS: Check if user can see OCR results (S5)
    boolean canSeeOcr = autorizador.puede("evidencia:validate");
    
    String ocrExtracted = canSeeOcr ? evidencia.getOcrExtracted() : null;
    List<String> validationErrors = canSeeOcr ? parseErrors(evidencia) : null;
    Integer confidence = canSeeOcr ? parseConfidence(evidencia) : null;
    
    return new EvidenciaResponse(
        evidencia.getPublicId(),
        evidencia.getS3Key(),
        
        // Current fields
        evidencia.getEstado(),
        evidencia.getContentType(),
        evidencia.getSizeBytes(),
        
        // NEW: Validation fields (RLS-aware)
        evidencia.getValidationStatus(),
        ocrExtracted,                    // NULL for OBSERVER
        validationErrors,                // NULL for OBSERVER
        confidence,                      // NULL for OBSERVER
        evidencia.getValidatedAt(),
        
        // Current fields
        evidencia.getCreadoEn(),
        evidencia.getActualizadoEn()
    );
  }
  
  private List<String> parseErrors(Evidencia evidencia) {
    if (evidencia.getOcrResult() == null || evidencia.getOcrResult().getErrors() == null) {
      return List.of();
    }
    // Parse JSON errors from OcrResult
    // Example: {"brightness": -60, "contrast": 0.2} → ["brightness_too_low", "contrast_too_low"]
    Map<String, Object> errors = Json.decode(evidencia.getOcrResult().getErrors(), Map.class);
    List<String> result = new ArrayList<>();
    
    if ((Integer) errors.get("brightness") < -50) result.add("brightness_too_low");
    if ((Double) errors.get("contrast") < 0.3) result.add("contrast_too_low");
    if ((Double) errors.get("blur_variance") < 2.5) result.add("blur_detected");
    
    return result;
  }
  
  private Integer parseConfidence(Evidencia evidencia) {
    if (evidencia.getOcrResult() == null) return null;
    BigDecimal score = evidencia.getOcrResult().getConfidenceScore();
    return score != null ? score.intValue() : null;
  }
}
```

---

### T3.5 — Contract + Integration Tests

**File:** `backend/contract-tests/src/test/java/ec/twintailcs/electoral/contract/EvidenciaOcrContractIT.java`

```java
@QuarkusTest
public class EvidenciaOcrContractIT extends ContractTestSupport {
  
  /**
   * S1: POST /evidencias returns 202 Accepted (not 201 Created)
   */
  @Test
  void subirEvidencia_devuelve202Accepted() {
    var response = uploadEvidenceWithPhoto("good_photo.jpg");
    
    assertThat(response.statusCode()).isEqualTo(202);
  }
  
  /**
   * S3: Response includes validationStatus + ocrExtracted
   */
  @Test
  void subirEvidencia_devuelveValidationStatus() {
    var response = uploadEvidenceWithPhoto("good_photo.jpg");
    
    var body = as(response, EvidenciaResponse.class);
    assertThat(body.validationStatus()).isEqualTo("validating");
    assertThat(body.ocrExtracted()).isNull(); // Not yet processed
    assertThat(body.validationErrors()).isEmpty();
  }
  
  /**
   * S5: GET /evidencias/{id} includes validationStatus
   */
  @Test
  void obtenerEvidencia_devuelveValidationStatus() {
    var uploadResp = uploadEvidenceWithPhoto("good_photo.jpg");
    var body = as(uploadResp, EvidenciaResponse.class);
    
    var getResp = get("/api/v1/evidencias/" + body.id(), observerToken());
    
    assertThat(getResp.statusCode()).isEqualTo(200);
    var evidencia = as(getResp, EvidenciaResponse.class);
    assertThat(evidencia.validationStatus()).isNotNull();
  }
  
  /**
   * S2: OBSERVER cannot call revalidate (no evidencia:validate permission)
   */
  @Test
  void revalidarEvidencia_observerDevuelve403() {
    var uploadResp = uploadEvidenceWithPhoto("good_photo.jpg");
    var body = as(uploadResp, EvidenciaResponse.class);
    
    var revalidateResp = patch("/api/v1/evidencias/" + body.id() + "/revalidate", null, observerToken());
    
    assertThat(revalidateResp.statusCode()).isEqualTo(403);
  }
  
  /**
   * S4: COORDINATOR can call revalidate (has evidencia:validate)
   */
  @Test
  void revalidarEvidencia_coordinatorDevuelve200() {
    var uploadResp = uploadEvidenceWithPhoto("good_photo.jpg");
    var body = as(uploadResp, EvidenciaResponse.class);
    
    var revalidateResp = patch("/api/v1/evidencias/" + body.id() + "/revalidate", null, coordinatorToken());
    
    assertThat(revalidateResp.statusCode()).isEqualTo(200);
    var updated = as(revalidateResp, EvidenciaResponse.class);
    assertThat(updated.validationStatus()).isEqualTo("validating");
  }
  
  /**
   * S5: OBSERVER sees validationStatus but NOT ocrExtracted
   */
  @Test
  void obtenerEvidencia_observerNoVeOcrExtracted() {
    var uploadResp = uploadEvidenceWithPhoto("good_photo.jpg");
    var body = as(uploadResp, EvidenciaResponse.class);
    
    // Simulate OCR completed
    simulateOcrCompletion(body.id(), "OCR text here");
    
    var getResp = get("/api/v1/evidencias/" + body.id(), observerToken());
    var evidencia = as(getResp, EvidenciaResponse.class);
    
    assertThat(evidencia.validationStatus()).isEqualTo("success");
    assertThat(evidencia.ocrExtracted()).isNull(); // RLS: OBSERVER cannot see this
  }
  
  /**
   * S5: COORDINATOR sees validationStatus AND ocrExtracted
   */
  @Test
  void obtenerEvidencia_coordinatorVeOcrExtracted() {
    var uploadResp = uploadEvidenceWithPhoto("good_photo.jpg");
    var body = as(uploadResp, EvidenciaResponse.class);
    
    // Simulate OCR completed
    simulateOcrCompletion(body.id(), "OCR text here");
    
    var getResp = get("/api/v1/evidencias/" + body.id(), coordinatorToken());
    var evidencia = as(getResp, EvidenciaResponse.class);
    
    assertThat(evidencia.validationStatus()).isEqualTo("success");
    assertThat(evidencia.ocrExtracted()).isEqualTo("OCR text here");
    assertThat(evidencia.validationErrors()).isEmpty();
  }
  
  private Response uploadEvidenceWithPhoto(String photoFile) {
    // Upload multipart to POST /evidencias
    // Return 202 response
    return null; // Implementation
  }
  
  private void simulateOcrCompletion(UUID evidenciaId, String ocrText) {
    // Simulate what async worker would do (or wait for real worker)
    // For contract tests, may need to manually trigger or poll
  }
}
```

**Integration Test (local, with real async worker):**

```java
@QuarkusTest
public class EvidenciaOcrIT {
  
  @Inject OcrValidationQueue ocrQueue;
  @Inject EvidenciaRepository evidenciaRepo;
  
  @Test
  void endToEnd_uploadGoodPhoto_eventuallyReturnsOcr() throws Exception {
    // 1. Upload photo
    var uploadResp = uploadEvidenceWithPhoto("good_photo.jpg");
    assertThat(uploadResp.statusCode()).isEqualTo(202);
    
    var body = as(uploadResp, EvidenciaResponse.class);
    UUID evidenciaId = body.id();
    
    // 2. Poll for completion (with timeout)
    Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
    while (Instant.now().isBefore(deadline)) {
      var getResp = get("/api/v1/evidencias/" + evidenciaId, coordinatorToken());
      var evidencia = as(getResp, EvidenciaResponse.class);
      
      if ("success".equals(evidencia.validationStatus())) {
        // OCR completed
        assertThat(evidencia.ocrExtracted()).isNotEmpty();
        assertThat(evidencia.validationErrors()).isEmpty();
        return; // Success
      }
      
      if ("failed".equals(evidencia.validationStatus())) {
        fail("Photo validation failed: " + evidencia.validationErrors());
      }
      
      Thread.sleep(500); // Poll every 500ms
    }
    
    fail("Timeout waiting for OCR completion");
  }
}
```

---

## Verification

**All pass locally:**
```bash
rtk mvn test -Dtest='EvidenciaOcrContractIT,EvidenciaOcrIT'
# Expected: 8+ contract tests pass, integration tests pass
```

**API Response Format:**
```bash
# POST /evidencias?actaId=123 (upload)
curl -X POST http://localhost:8080/api/v1/evidencias \
  -F file=@photo.jpg \
  -F actaId=123

# Expected response (202):
{
  "id": "uuid-123",
  "s3Key": "evidencias/uuid-123.webp",
  "estado": "VALIDANDO",
  "validationStatus": "validating",
  "ocrExtracted": null,
  "validationErrors": [],
  "validatedAt": null,
  "contentType": "image/webp",
  "sizeBytes": 1000000
}
```

**GET after validation completes:**
```bash
curl http://localhost:8080/api/v1/evidencias/uuid-123 \
  -H "Authorization: Bearer coordinatorToken"

# Expected response (after ~5s):
{
  "id": "uuid-123",
  "validationStatus": "success",
  "ocrExtracted": "Acta Electoral, Total sufragantes: 234...",
  "validationErrors": [],
  "confidenceScore": 87,
  "validatedAt": "2026-11-25T21:35:00Z"
}
```

**Revalidate endpoint:**
```bash
curl -X PATCH http://localhost:8080/api/v1/evidencias/uuid-123/revalidate \
  -H "Authorization: Bearer coordinatorToken"

# Expected response (202):
{
  "validationStatus": "validating",
  "ocrExtracted": null,
  "validatedAt": null
}
```

---

## Notes

- T3 integrates T1 + T2 into the API
- T3 changes response status (201 → 202)
- T3 adds RLS visibility (OBSERVER vs COORDINATOR)
- T3 is the **user-facing layer**
- Contract tests verify new API contract (202, new fields, permissions)

---

## Risk

- API change (201 → 202) may break old clients
  - **Mitigation:** Document breaking change, support window for migration
- RLS visibility logic must be correct (OBSERVER shouldn't see OCR)
  - **Mitigation:** Contract tests verify permission checks

---

**Risk:** Medium (API contract change, permissions)

---

## Summary of All 3 ODDs

| Task | Scope | Effort | Risk |
|------|-------|--------|------|
| **T1** | OpenCV validation (brightness, contrast, blur) | 1-2h | None |
| **T2** | Async queue + Tesseract OCR + DB persistence | 2-3h | Medium |
| **T3** | API (202), permissions, RLS, end-to-end tests | 2-3h | Medium |
| **TOTAL** | Photo validation + OCR extraction + API | **5-8h** | **Medium** |

**After all 3 complete:**
- ✅ Photos uploaded → 202 Accepted
- ✅ Async worker validates (brightness/contrast/blur)
- ✅ If validation passes → Tesseract extracts text
- ✅ Results persisted in OcrResult table
- ✅ COORDINATOR can see OCR + revalidate
- ✅ OBSERVER sees validation status (not text)
- ✅ All results audited for compliance

---

**Ready for Builder to implement T1 → T2 → T3 sequentially**
