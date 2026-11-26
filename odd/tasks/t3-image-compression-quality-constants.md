# ODD T3 — Extract Quality Constants

**Reference:** T1 and T2 should be merged before this task  
**Depends on:** None (independent task, can run in parallel with T2)  
**Status:** Ready for Builder (after T1)  
**Effort:** 15 minutes  
**Criticality:** MEDIUM — Maintainability, not blocking

---

## Specs

### S1 — Replace Hardcoded JPEG Quality with Constant

**Current Bug:** Line 75 uses hardcoded `0.75` for JPEG second-pass quality.  
Inconsistency: WebP uses `QUALITY_LOW = 0.65` constant, JPEG doesn't.

**Acceptance:**
- All quality values use class constants (no magic numbers)
- JPEG second-pass quality uses `QUALITY_LOW` or separate `QUALITY_JPEG_LOW` constant
- Code is self-documenting and maintainable

**How to Test:**
```typescript
// Search source: no occurrences of hardcoded quality values (0.9, 0.8, 0.75, 0.65)
// All should reference static readonly fields
```

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T3.1** | S1 | Add QUALITY_JPEG_HIGH constant | Inline |
| **T3.2** | S1 | Replace hardcoded values in compress() | Inline |
| **T3.3** | S1 | Regression test: verify no behavior change | Inline |

---

## Implementation Checklist

### T3.1 — Add Constants

**File:** `frontend/app/src/app/core/services/image-compression.service.ts`

**Current (lines 17-27):**
```typescript
private static readonly MAX_WIDTH = 2048;
private static readonly TARGET_BYTES = 650 * 1024;
private static readonly QUALITY_HIGH = 0.8;      // WebP
private static readonly QUALITY_LOW = 0.65;      // WebP
```

**New (add after line 27):**
```typescript
/** JPEG quality for first pass. Slightly higher than WebP (less compression artifact perception). */
private static readonly QUALITY_JPEG_HIGH = 0.9;

/** JPEG quality for second pass (fallback if > TARGET_BYTES). */
private static readonly QUALITY_JPEG_LOW = 0.75;
```

**Deliverable:**
- ✅ Two new constants added
- ✅ Clear comments explaining JPEG vs WebP quality levels

---

### T3.2 — Replace Hardcoded Values

**File:** `frontend/app/src/app/core/services/image-compression.service.ts`

**Location 1 — Line 60 (JPEG first-pass quality):**

**Current:**
```typescript
const quality =
  mimeType === 'image/webp' ? ImageCompressionService.QUALITY_HIGH : 0.9;
```

**New:**
```typescript
const quality =
  mimeType === 'image/webp' ? ImageCompressionService.QUALITY_HIGH : ImageCompressionService.QUALITY_JPEG_HIGH;
```

**Location 2 — Lines 72-75 (JPEG second-pass quality):**

**Current:**
```typescript
quality:
  mimeType === 'image/webp'
    ? ImageCompressionService.QUALITY_LOW
    : 0.75,
```

**New:**
```typescript
quality:
  mimeType === 'image/webp'
    ? ImageCompressionService.QUALITY_LOW
    : ImageCompressionService.QUALITY_JPEG_LOW,
```

**Deliverable:**
- ✅ Two hardcoded values replaced
- ✅ All quality references now use constants

---

### T3.3 — Regression Test

**File:** `frontend/app/src/app/core/services/image-compression.service.spec.ts`

**New test to add:**

```typescript
describe('Constants usage', () => {
  
  it('should use constants for all quality values, no hardcoded magic numbers', async () => {
    const mockFile = new File(['data'], 'test.jpg', { type: 'image/jpeg' });
    
    // Mock createImageBitmap
    (global as any).createImageBitmap = jasmine.createSpy('createImageBitmap')
      .and.returnValue(Promise.resolve({
        width: 1024,
        height: 768,
        close: () => {},
      } as any));
    
    const mockCanvas = jasmine.createSpyObj('OffscreenCanvas', ['getContext', 'convertToBlob']);
    const mockCtx = jasmine.createSpyObj('CanvasRenderingContext2D', ['drawImage']);
    mockCanvas.getContext.and.returnValue(mockCtx);
    
    // Track calls to convertToBlob to verify correct quality used
    const qualitiesUsed: number[] = [];
    mockCanvas.convertToBlob.and.callFake((options: any) => {
      qualitiesUsed.push(options.quality);
      // Return blob that exceeds TARGET_BYTES to trigger second pass
      return Promise.resolve(new Blob(new Array(700 * 1024).fill('x'), { type: options.type }));
    });
    
    (global as any).OffscreenCanvas = jasmine.createSpy('OffscreenCanvas')
      .and.returnValue(mockCanvas);
    
    // Mock supportsWebP to return false (force JPEG path)
    spyOn<any>(service, 'supportsWebP').and.returnValue(false);
    
    await service.compress(mockFile);
    
    // First pass should use QUALITY_JPEG_HIGH (0.9)
    expect(qualitiesUsed[0]).toBe(0.9);
    
    // Second pass should use QUALITY_JPEG_LOW (0.75)
    expect(qualitiesUsed[1]).toBe(0.75);
  });
});
```

**Deliverable:**
- ✅ 1 new regression test verifying constant usage
- ✅ Existing tests still pass
- ✅ 0 behavioral changes

---

## Verification

**All pass locally:**
```bash
npm run test -- image-compression.service.spec.ts
# Expected: 10 tests (7 from T1 + 2 from T2 + 1 new), 0 failures
```

**No regressions:**
```bash
npm run test -- registrar-acta.page.spec.ts modificar-acta.page.spec.ts jrv-cargos.page.spec.ts
# Expected: all pass
```

**Code quality check:**
```bash
npm run lint -- frontend/app/src/app/core/services/image-compression.service.ts
# Expected: no hardcoded numbers (0.9, 0.8, 0.75, 0.65 search)
```

---

## Risk

- None. Pure refactoring, no behavior change.

---

## Notes

- T3 is **independent** and can run in parallel with T2 (no dependencies)
- After T1 + T2 + T3: all merged, single commit to feature branch
- Clear comments on quality levels help future maintainers understand JPEG vs WebP trade-offs

---

**Risk:** none

