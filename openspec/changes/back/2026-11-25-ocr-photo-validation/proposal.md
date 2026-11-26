# SDD — OCR Photo Validation for Evidence Upload

**Date:** 2026-11-25  
**Status:** Design Phase (ready for Architect feedback)  
**Branch:** feature/ocr-evidence-validation  

---

## 1. Propuesta Ejecutiva

### Qué
Agregar validación automática de fotos de actas antes de aceptarlas en el sistema:
- **Validación local** (OpenCV): detectar fotos ilegibles (brillo, contraste, ángulo)
- **Extracción OCR** (Tesseract): leer texto de acta → auditar para compliance

### Por Qué
- **Problema actual**: Fotos ilegibles o oscuras se aceptan → luego imposible leerlas en auditoría
- **Negocio**: CNE requiere fotos legibles. OCR detecta defectos antes de que sea tarde
- **Operación**: Users reciben feedback inmediato si foto es mala, pueden retomar

### Alcance
- POST /evidencias/{id}/upload-url → presigned URL (SIN cambio)
- PUT directo a S3 (SIN cambio)
- POST /evidencias?actaId=X → **202 Accepted (cambio)** + encolamos validación
- Worker async valida foto + OCR → audita resultado
- Frontend notificado cuando foto está lista

### Users
- **OBSERVER**: sube foto acta → recibe feedback si mala
- **COORDINATOR/MASTER**: ve estado validación, puede reexaminar OCR extraído
- **ADMIN**: audita resultados OCR para compliance CNE

### No en Scope
- Rechazo automático de fotos (siempre guardar, validar después)
- Bloqueo de acta hasta validación (usuario sigue trabajando)
- Integración Cloud (OpenCV local only)

---

## 2. Cambios Visibles

### API Contract Changes

**ANTES:**
```
POST /evidencias?actaId=123
Content-Type: multipart/form-data

→ 201 Created + EvidenciaResponse
  {id, s3Key, estado: PENDIENTE, ...}
```

**DESPUÉS:**
```
POST /evidencias?actaId=123
Content-Type: multipart/form-data

→ 202 Accepted + EvidenciaResponse
  {id, s3Key, estado: VALIDANDO, validationStatus: "in_progress", ...}
```

### Response DTO Changes

**EvidenciaResponse + 3 campos nuevos:**
```json
{
  "id": "uuid",
  "s3Key": "evidencias/uuid.webp",
  "estado": "VALIDANDO",          // NUEVO
  "validationStatus": "pending",  // NUEVO: pending | validating | success | failed
  "ocrExtracted": null,           // NUEVO: texto extraído (si success)
  "validationErrors": [],         // NUEVO: [brillo_bajo, contraste_bajo, ...]
  "validatedAt": null,            // NUEVO: timestamp
  "contentType": "image/webp",
  "sizeBytes": 1048576
}
```

---

## 3. Decisiones de Diseño

### D1 — Upload Workflow (Sync/Async split)

**Propuesta:**
```
REQUEST (Sync):
  1. Frontend GET /evidencias/{id}/upload-url
     → Presigned URL (15 min validity)
  
  2. Frontend PUT to S3 directly
     → S3 stores file
  
  3. Frontend POST /evidencias?actaId=X
     ↓ Backend:
     a) Read file from S3 (200ms)
     b) Quick format check (mime-type, size)
     c) Create Evidencia record (estado=VALIDANDO)
     d) Enqueue OcrValidationJob
     e) Return 202 Accepted + EvidenciaResponse

BACKGROUND (Async, worker thread):
  4. OcrValidationQueue.process(evidenciaId)
     a) Download from S3 (500ms)
     b) OpenCV validation (200ms)
        - Brightness threshold
        - Contrast ratio
        - Blur detection
     c) If validation fails → mark VALIDACION_FALLIDA + auditar
     d) If OK → Tesseract OCR (2-5s per photo)
     e) Store OcrResult in BD
     f) Update Evidencia (estado=PENDIENTE, ocrExtracted=texto)
```

**Why 202?**
- Request succeeds (file stored in S3)
- Validation happens later (not failure of upload)
- Idempotent: retrying doesn't duplicate work

**Why Async?**
- OCR = 2-5s per photo → blocking users is bad UX
- Worker queue pattern already exists (InvitationEmailQueueImpl)
- Multiple photos in batch should validate in parallel

---

### D2 — Storage of OCR Results

**Propuesta:**

Create new table `ocr_result`:
```sql
CREATE TABLE ocr_result (
  id BIGINT PRIMARY KEY AUTO_INCREMENT,
  evidencia_id BIGINT NOT NULL UNIQUE,
  raw_text TEXT,                        -- Full OCR extraction
  confidence_score DECIMAL(5,2),        -- Tesseract confidence
  validation_status VARCHAR(50),        -- success | brightness_low | contrast_low | blur | ...
  errors JSONB,                         -- {brightness: -15, contrast: 0.4, blur_variance: 2.1}
  extracted_at TIMESTAMP NOT NULL,
  FOREIGN KEY (evidencia_id) REFERENCES evidencia(id) ON DELETE CASCADE
);
```

**Why JSONB for errors:**
- Errors are heterogeneous (brillo, contraste, blur, angle)
- Easier to query later ("find all fotos with brillo < X")
- Extensible if add more validations

**Why keep raw_text:**
- CNE compliance: audit trail of what was read
- Future: compare OCR extraction vs manual entry
- Not used now, but stored for compliance

---

### D3 — Evidencia State Machine

**Current states:** PENDIENTE → APROBADA / RECHAZADA

**New states:**
```
PENDIENTE
  ↓ (user uploads)
VALIDANDO
  ↓ (OCR completes)
  → PENDIENTE (validation passed, ready for review)
  → VALIDACION_FALLIDA (brillo/contraste too bad, user retakes)
  
VALIDACION_FALLIDA
  ↓ (user uploads new photo)
  → VALIDANDO (try again)
```

**After PENDIENTE:**
```
PENDIENTE → APROBADA (reviewer approves)
         → RECHAZADA (reviewer rejects)
         → VALIDACION_FALLIDA (batch re-validation finds issue)
```

**Why not auto-reject?**
- Users should decide (not system)
- Some "dark" photos might be legible with contrast boost
- Conservative: validate, flag, let human decide

---

### D4 — Permissions & Access Control

**New permission:**
```
evidencia:validate    -- see/audit OCR results (COORDINATOR, MASTER, ADMIN)
```

**Who sees what:**
| Role | See OcrExtracted? | See ValidationStatus? | Can Re-validate? |
|------|-------------------|----------------------|------------------|
| OBSERVER | ❌ No | ✅ Yes (own photo) | ❌ No |
| COORDINATOR | ✅ Yes | ✅ Yes | ✅ Yes (re-queue) |
| MASTER | ✅ Yes | ✅ Yes | ✅ Yes (re-queue) |
| ADMIN | ✅ Yes | ✅ Yes | ✅ Yes (re-queue) |

**Why COORDINATOR can re-validate:**
- Might want to process again if Tesseract updated
- Batch re-validation for compliance audits
- Endpoint: `POST /evidencias/{id}/revalidate` (requires evidencia:validate perm)

---

### D5 — Storage Stack (Local vs Cloud)

**Propuesta: Local (OpenCV + Tesseract Java)**

| Aspect | Local | Cloud (Google Vision) | Decision |
|--------|-------|----------------------|----------|
| Cost | €0 | €0.6 per 1000 images | Local (free) |
| Latency | 2-5s | 500ms + network | Local (backend control) |
| Privacy | On-prem | Google stores | Local (compliance) |
| Accuracy | 85-90% | 95%+ | Local (acceptable for flagging) |
| Setup | Add deps | API key management | Local (simpler) |
| Scaling | CPU bound | No scaling | Local (async workers) |

**Stack:**
- `org.opencv:opencv-java` — image analysis
- `net.sourceforge.tess4j:tess4j` — OCR (wraps Tesseract C++)
- Async worker pool (like email queue)

---

### D6 — Error Handling & Retries

**Validation failures (non-retriable):**
- Brillo muy bajo → user retakes photo (D3: VALIDACION_FALLIDA)
- Contraste bajo → user retakes
- Blur detected → user retakes

**Processing failures (retriable with backoff):**
- Tesseract crash → retry (exponential backoff)
- S3 download timeout → retry
- Max retries = 3, then mark VALIDACION_FALLIDA

**User experience:**
1. Upload → 202 Accepted (shown immediately)
2. After 5-10s → "Validating..." (polling endpoint)
3. After validation → "✅ Photo OK" or "❌ Retake (too dark)"
4. If retake → user replaces file, restarts at D1

---

## 4. Arquitectura

### Component Diagram

```
Frontend                          Backend                     Workers
  │                                │                            │
  ├─→ GET /upload-url             │                            │
  │   ← presigned URL              │                            │
  │                                │                            │
  ├─→ PUT to S3                   │                            │
  │   (direct)                     │                            │
  │                                │                            │
  ├─→ POST /evidencias            │                            │
  │   multipart {file}        ┌────┴────┐                      │
  │                           │Resource │                      │
  │   ← 202 Accepted          └────┬────┘                      │
  │   {estado: VALIDANDO}          │                           │
  │                                │ enqueue()                 │
  │                           ┌────▼────────────┐              │
  │                           │OcrValidationQ   │◄─────────────┤
  │                           │(InMemory queue) │              │
  │                           └────┬────────────┘              │
  │                                │                           │
  │   ┌─ Polling (GET /)           │   process()       ┌──────▼──────┐
  │   │   /evidencias/{id}         ├──────────────────►│OcrValidator │
  │   └─► {validationStatus}       │                  │(worker)     │
  │       {ocrExtracted}           │                  └──────┬──────┘
  │                                │                         │
  │   ◄─ { estado: PENDIENTE,      │                   ┌─────▼──────┐
  │       ocrExtracted: "..." }    │                   │S3Storage   │
  │                                │                   │BD (Orm)    │
  │                                │                   └────────────┘
```

### Class Diagram (Backend)

```
Evidencia (entity)
  + id, publicId, s3Key
  + estado: PENDIENTE|VALIDANDO|VALIDACION_FALLIDA|APROBADA|RECHAZADA
  + validationStatus: pending|validating|success|failed
  + ocrExtracted: String
  + validatedAt: Instant

OcrResult (entity)
  + id, evidenciaId
  + rawText: String
  + confidenceScore: Decimal
  + validationStatus: success|brightness_low|...
  + errors: JSONB {brightness, contrast, blur, angle}

OcrValidationJob (record)
  + evidenciaId: Long
  + s3Key: String
  + retryCount: int
  + createdAt: Instant

OcrValidatorImpl (service)
  + validateBrightness(BufferedImage): ValidationResult
  + validateContrast(BufferedImage): ValidationResult
  + runOcr(BufferedImage): String
  + process(OcrValidationJob)

OcrValidationQueueImpl (port impl)
  + enqueue(job)
  + pendientes(): int
  + (async worker thread polls & processes)
```

---

## 5. Test Strategy

### Unit Tests (OpenCV validators)
```
✅ brightnessTooLow_returnsFAILED()
✅ contrastAcceptable_returnsSUCCESS()
✅ blurDetected_returnsFAILED()
✅ angleSkewed_returnsWARNING()
```

### Integration Tests (End-to-end)
```
✅ uploadPhoto_enqueuesValidation_returns202()
✅ ocrProcesses_extractsText_updatesEvidencia()
✅ darkPhoto_markedVALIDACION_FALLIDA()
✅ observerCanSeeValidationStatus()
✅ coordinatorCanRevalidate()
```

### Contract Tests
```
✅ POST /evidencias returns 202 (not 201)
✅ GET /evidencias/{id} includes validationStatus
✅ POST /evidencias/{id}/revalidate requires evidencia:validate
```

---

## 6. Próximos Pasos

**→ Documento listo en:** `/openspec/changes/back/2026-11-25-ocr-photo-validation/`

**Decisiones aprobadas (D1-D6):**
- ✅ Async 202 workflow (no bloqueo)
- ✅ OcrResult table (compliance audit trail)
- ✅ New estado VALIDANDO
- ✅ Permission evidencia:validate
- ✅ Local stack (OpenCV + Tesseract)
- ✅ Retry strategy

**Ahora:**
1. Arquitecto revisa D1-D6, sugiere cambios
2. Una vez aprobado → 3 ODDs enfocados
3. Builder implementa T1, T2, T3

**Feedback esperado:**
- ¿Storage de OcrResult es excesivo? ¿Auditamos solo estados?
- ¿Tesseract demasiado lento? ¿Aumentar timeout?
- ¿New permission evidencia:validate es necesaria?

---

**Risk:** Medium (new async worker + storage, pero patrón existe)  
**Effort:** 3-4 days (T1-T3 + tests)  
**Value:** High (compliance + UX)
