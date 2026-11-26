# ODD T1 — Photo Quality Validation (OpenCV)

**Reference:** `/openspec/changes/back/2026-11-25-ocr-photo-validation/proposal.md` (SDD D1, D5)  
**Status:** Ready for Builder  
**Effort:** 1-2 hours  

---

## Specs

### S1 — Brightness Detection
User uploads dark photo → system detects brightness < threshold → logs validation error  
**Acceptance:** 
- OpenCV reads image → calculates average brightness (-100 to 100)
- If brightness < -50 → error "brightness_too_low"
- Test: dark_photo.jpg (-60 brightness) → returns ValidationResult(false, ["brightness_too_low"])

### S2 — Contrast Detection
User uploads low-contrast photo → system detects contrast below threshold  
**Acceptance:**
- OpenCV calculates standard deviation of pixel values (0 to 1)
- If contrast < 0.3 → error "contrast_too_low"
- Test: low_contrast_photo.jpg (0.2 contrast) → returns ValidationResult(false, ["contrast_too_low"])

### S3 — Blur Detection
User uploads blurry photo → system detects blur via Laplacian variance  
**Acceptance:**
- OpenCV applies Laplacian kernel, calculates variance
- If blur_variance < 2.5 → error "blur_detected"
- Test: blurry_photo.jpg (1.8 variance) → returns ValidationResult(false, ["blur_detected"])

### S4 — Good Photo Passes
User uploads clear, well-lit photo → validation passes  
**Acceptance:**
- Photo has brightness > -50 AND contrast > 0.3 AND blur_variance > 2.5
- Returns ValidationResult(true, [])
- Test: good_photo.jpg → returns ValidationResult(true, [])

### S5 — Metrics Captured
Validation captures raw metrics for debugging  
**Acceptance:**
- ValidationResult includes Map<String, Object> with {brightness, contrast, blur_variance}
- Stored in OcrResult.errors (JSONB) for audit
- Test: metrics contain exact float values

### S6 — OcrValidator Injectable Service
OcrValidatorImpl is @ApplicationScoped bean, auto-injected in tests  
**Acceptance:**
- OcrValidatorImpl has @ApplicationScoped annotation
- Methods: validateQuality(BufferedImage) → ValidationResult
- Can be injected via @Inject in tests/services
- No static methods, testable

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T1.1** | S1-S4, S6 | Create OcrValidatorImpl + validation methods | Inline |
| **T1.2** | S1-S5 | Unit tests (brightness, contrast, blur, good photo, metrics) | Inline |
| **T1.3** | S6 | Integration test: validateQuality() with real images | Inline |

---

## Implementation Checklist

### T1.1 — OcrValidatorImpl Service

**File:** `backend/electoral-api-quarkus/src/main/java/ec/twintailcs/electoral/evidencia/application/OcrValidatorImpl.java`

**Code structure (pseudocode):**
```java
@ApplicationScoped
public class OcrValidatorImpl {
  
  private static final int BRIGHTNESS_THRESHOLD = -50;
  private static final double CONTRAST_THRESHOLD = 0.3;
  private static final double BLUR_VARIANCE_THRESHOLD = 2.5;
  
  public ValidationResult validateQuality(BufferedImage image) {
    var errors = new ArrayList<String>();
    var metrics = new HashMap<String, Object>();
    
    // Brightness
    int brightness = calculateAverageBrightness(image);
    metrics.put("brightness", brightness);
    if (brightness < BRIGHTNESS_THRESHOLD) {
      errors.add("brightness_too_low");
    }
    
    // Contrast
    double contrast = calculateContrast(image);
    metrics.put("contrast", Math.round(contrast * 1000.0) / 1000.0);
    if (contrast < CONTRAST_THRESHOLD) {
      errors.add("contrast_too_low");
    }
    
    // Blur
    double blurVariance = calculateBlurVariance(image);
    metrics.put("blur_variance", Math.round(blurVariance * 1000.0) / 1000.0);
    if (blurVariance < BLUR_VARIANCE_THRESHOLD) {
      errors.add("blur_detected");
    }
    
    return new ValidationResult(errors.isEmpty(), errors, metrics);
  }
  
  private int calculateAverageBrightness(BufferedImage image) {
    // Convert to grayscale, calculate mean pixel value
    // Return -100 (black) to 100 (white)
  }
  
  private double calculateContrast(BufferedImage image) {
    // Calculate standard deviation of pixel values
    // Return 0 (all same) to 1 (max variance)
  }
  
  private double calculateBlurVariance(BufferedImage image) {
    // Apply Laplacian kernel, calculate variance
    // Return 0 (very blurry) to 100+ (sharp)
  }
}

public record ValidationResult(
    boolean passed,
    List<String> errors,
    Map<String, Object> metrics
) {}
```

**Deliverable:**
- ✅ OcrValidatorImpl.java (complete)
- ✅ ValidationResult.java (record)
- ✅ Maven deps added (org.opencv)
- ✅ Thresholds configurable (or hardcoded for MVP)

---

### T1.2 — Unit Tests

**File:** `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/evidencia/application/OcrValidatorTest.java`

**Tests:**
```
✅ brightnessTooLow_returnsFAILED()
   Input: -60 brightness
   Expected: ValidationResult(false, ["brightness_too_low"])

✅ contrastTooLow_returnsFAILED()
   Input: 0.2 contrast
   Expected: ValidationResult(false, ["contrast_too_low"])

✅ blurTooHigh_returnsFAILED()
   Input: 1.8 blur_variance
   Expected: ValidationResult(false, ["blur_detected"])

✅ goodPhoto_returnsSUCCESS()
   Input: Good photo (brightness=-20, contrast=0.6, blur=4.0)
   Expected: ValidationResult(true, [])

✅ multipleErrors_allCaptured()
   Input: Dark + blurry photo
   Expected: ValidationResult(false, ["brightness_too_low", "blur_detected"])

✅ metricsIncluded()
   Input: Any photo
   Expected: metrics contains {brightness, contrast, blur_variance}
```

**Test resources:** Create `src/test/resources/photos/`:
- `dark.jpg` (brightness -60)
- `low_contrast.jpg` (contrast 0.2)
- `blurry.jpg` (blur_variance 1.8)
- `good.jpg` (all metrics OK)

---

### T1.3 — Integration Test

**File:** `backend/electoral-api-quarkus/src/test/java/ec/twintailcs/electoral/evidencia/application/OcrValidatorIT.java`

**Test:**
```java
@QuarkusTest
public class OcrValidatorIT {
  
  @Inject OcrValidatorImpl validator;
  
  @Test
  void validateQuality_darkPhoto_returnsFailed() throws IOException {
    BufferedImage image = ImageIO.read(
        new File("src/test/resources/photos/dark.jpg"));
    
    ValidationResult result = validator.validateQuality(image);
    
    assertThat(result.passed()).isFalse();
    assertThat(result.errors()).contains("brightness_too_low");
    assertThat(result.metrics()).containsKeys("brightness", "contrast", "blur_variance");
  }
  
  @Test
  void validateQuality_goodPhoto_returnsSuccess() throws IOException {
    BufferedImage image = ImageIO.read(
        new File("src/test/resources/photos/good.jpg"));
    
    ValidationResult result = validator.validateQuality(image);
    
    assertThat(result.passed()).isTrue();
    assertThat(result.errors()).isEmpty();
  }
}
```

---

## Verification

**All pass locally:**
```bash
rtk mvn test -Dtest='OcrValidatorTest,OcrValidatorIT' 
# Expected: 8 tests, 0 failures
```

**No regressions:**
- Existing evidence tests still pass
- No new DB migrations (T1 only validates in-memory)

---

## Risk

- OpenCV installation might fail on CI (requires libopencv-dev)
  - **Mitigation:** Add to Dockerfile.native + CI setup
- Tesseract not installed yet (but OK, T1 doesn't use it)
- Thresholds might need tuning
  - **Mitigation:** Make configurable, can tune in T2/T3

---

## Notes

- T1 is **standalone + testable** (no S3, no DB, no async)
- T1 is **reusable** (OcrValidatorImpl can be called from T2/T3)
- T1 creates **no side effects** (pure validation)
- Next: T2 integrates T1 into async queue

---

**Risk:** none (validation only, no mutations)
