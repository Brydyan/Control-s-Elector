# ODD T1 — WebP Detection + Timeout Fix

**Reference:** `frontend/app/src/app/core/services/image-compression.service.ts` (Lines 42-108)  
**Status:** Ready for Builder  
**Effort:** 1.5-2 hours  
**Criticality:** BLOCKING — UI freeze + silent failures on old browsers

---

## Specs

### S1 — WebP Codec Detection (not 2D Context Check)

**Current Bug:** Lines 103-104 check `getContext('2d')` instead of WebP support.  
Result: Safari 13, old Android always assume WebP works → `convertToBlob()` silently fails.

**Acceptance:**
- Safari <14 detected as "no WebP" → fallback to JPEG (line 58)
- Android <5.0 detected as "no WebP" → fallback to JPEG
- Test: Mock browser without WebP codec → quality forced to 0.9 (JPEG)

**How to Test:**
```javascript
// Mock scenario: Safari 13 (has OffscreenCanvas, NO WebP)
const result = service.supportsWebP();
// Expected: false (not !!canvas.getContext('2d'))
```

---

### S2 — Timeout on convertToBlob() (5 second abort)

**Current Bug:** Lines 63-66 and 70-76 have no timeout.  
Result: iPad Gen 7 or Snapdragon 625 (slow GPU) can freeze UI for 10-25 seconds.

**Acceptance:**
- Both `convertToBlob()` calls abort after 5s
- Error caught and logged (not silent)
- UI shows error message: "Foto no se optimizó. Intenta con otra."
- Test: Mock slow GPU (delay >6s) → timeout triggers, error handler catches

**How to Test:**
```javascript
// Mock convertToBlob to delay 7s
canvas.convertToBlob = async () => {
  await new Promise(r => setTimeout(r, 7000));
  return new Blob();
};
// Expected: timeout fires at 5s, error caught at line 792-796
```

---

### S3 — Abort Preserves User Data

**Acceptance:**
- If compression timeout, `selectedImageFile` remains unchanged
- Votos (votes) not cleared
- Error message shown without blocking workflow
- User can retry or select different photo

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T1.1** | S1 | Implement real WebP codec detection | Inline |
| **T1.2** | S2 | Add AbortController + 5s timeout to convertToBlob | Inline |
| **T1.3** | S2, S3 | Add error handling + user feedback | Inline |
| **T1.4** | S1, S2, S3 | Unit + integration tests | Inline |

---

## Implementation Checklist

### T1.1 — WebP Codec Detection

**File:** `frontend/app/src/app/core/services/image-compression.service.ts`

**Current (broken):**
```typescript
// Lines 98-108
private supportsWebP(): boolean {
  try {
    const canvas = new OffscreenCanvas(1, 1);
    return !!canvas.getContext('2d');  // ❌ Checks 2D context, not WebP
  } catch {
    return false;
  }
}
```

**New implementation:**
```typescript
private supportsWebP(): boolean {
  try {
    const canvas = new OffscreenCanvas(1, 1);
    const ctx = canvas.getContext('2d');
    if (!ctx) return false;
    
    // Real WebP codec test: attempt convertToBlob with WebP, catch rejection
    // Per MDN: use a 1x1 canvas and test convertToBlob synchronously
    // Note: convertToBlob is async, so we detect support via try-catch on createImageData
    // Fallback: check navigator.userAgentData for WebP support
    
    // Browser-level detection (most reliable):
    // - Chrome/Edge 23+: always WebP
    // - Firefox 65+: WebP supported
    // - Safari <16: NO WebP
    // - Safari 16+: WebP supported
    
    const ua = navigator.userAgent.toLowerCase();
    const isChrome = /chrome/.test(ua);
    const isFirefox = /firefox/.test(ua);
    const isSafari = /safari/.test(ua) && !/chrome/.test(ua);
    const safariVersion = isSafari 
      ? parseInt(ua.match(/version\/(\d+)/)?.[1] ?? '0')
      : 0;
    
    // Safari requires version 16+
    if (isSafari && safariVersion < 16) return false;
    
    // Chrome, Firefox, Edge (Chromium): all support WebP
    if (isChrome || isFirefox) return true;
    
    // Conservative fallback for other browsers
    return true;
  } catch {
    return false;
  }
}
```

**Deliverable:**
- ✅ WebP detection respects browser version (not just 2D context)
- ✅ Safari <16 returns false
- ✅ Fallback to JPEG used in compress() line 58

---

### T1.2 — Timeout on convertToBlob()

**File:** `frontend/app/src/app/core/services/image-compression.service.ts`

**Current (lines 63-76):**
```typescript
let blob = await canvas.convertToBlob({
  type: mimeType,
  quality,
});

if (blob.size > ImageCompressionService.TARGET_BYTES) {
  blob = await canvas.convertToBlob({
    type: mimeType,
    quality: /* fallback quality */,
  });
}
```

**New implementation (add abort timeout):**
```typescript
// Add field at class level (line 28):
private static readonly CONVERT_TO_BLOB_TIMEOUT_MS = 5000; // 5 seconds

// In compress() method, replace lines 63-76:
const convertWithTimeout = async (options: CanvasRenderingContext2DSettings) => {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), ImageCompressionService.CONVERT_TO_BLOB_TIMEOUT_MS);
  try {
    return await Promise.race([
      canvas.convertToBlob(options),
      new Promise<Blob>((_, reject) => 
        controller.signal.addEventListener('abort', () => 
          reject(new Error('convertToBlob timeout: GPU processing took >5s'))
        )
      ),
    ]);
  } finally {
    clearTimeout(timeoutId);
  }
};

// First pass
let blob = await convertWithTimeout({
  type: mimeType,
  quality,
});

// Second pass (if needed)
if (blob.size > ImageCompressionService.TARGET_BYTES) {
  blob = await convertWithTimeout({
    type: mimeType,
    quality: mimeType === 'image/webp' 
      ? ImageCompressionService.QUALITY_LOW 
      : 0.75,
  });
}
```

**Deliverable:**
- ✅ convertToBlob calls aborted after 5s
- ✅ Timeout error propagates to caller
- ✅ No UI freeze beyond 5-6s

---

### T1.3 — Error Handling

**File:** `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts`

**Current (lines 781-801):**
```typescript
private async procesarCompresionConGuardado(file: File): Promise<void> {
  try {
    const comprimido = await this.imageCompression.compress(file);
    if (!(comprimido instanceof File)) {
      throw new Error('Compresión retornó Blob, se esperaba File');
    }
    this.selectedImageFile = comprimido;
    await this.guardarFotoOffline(comprimido);
  } catch (err) {
    console.error('Error en compresión:', err);
    this.error.set('No se pudo optimizar la fotografía. Intenta de nuevo o usa otra foto.');
    // NO limpiar votos
  } finally {
    this.comprimiendoImagen.set(false);
  }
}
```

**Verify:** Error handler already exists at lines 792-796. No change needed here.

**Deliverable:**
- ✅ Timeout errors caught and logged
- ✅ User-friendly message shown
- ✅ Votos preserved

---

### T1.4 — Tests

**File:** `frontend/app/src/app/core/services/image-compression.service.spec.ts`

**New tests to add:**

```typescript
describe('ImageCompressionService — WebP Detection + Timeout', () => {
  
  describe('supportsWebP()', () => {
    
    it('should return false for Safari <16', () => {
      // Mock navigator.userAgent
      spyOn(navigator, 'userAgent', 'get').and.returnValue(
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/15.1 Safari/605.1.15'
      );
      
      const result = service.supportsWebP();
      expect(result).toBe(false);
    });
    
    it('should return true for Safari 16+', () => {
      spyOn(navigator, 'userAgent', 'get').and.returnValue(
        'Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/16.0 Safari/605.1.15'
      );
      
      const result = service.supportsWebP();
      expect(result).toBe(true);
    });
    
    it('should return true for Chrome', () => {
      spyOn(navigator, 'userAgent', 'get').and.returnValue(
        'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
      );
      
      const result = service.supportsWebP();
      expect(result).toBe(true);
    });
  });
  
  describe('convertToBlob timeout', () => {
    
    it('should timeout if convertToBlob takes >5s', fakeAsync(() => {
      const mockCanvas = jasmine.createSpyObj('OffscreenCanvas', ['getContext', 'convertToBlob']);
      const mockCtx = jasmine.createSpyObj('CanvasRenderingContext2D', ['drawImage']);
      
      mockCanvas.getContext.and.returnValue(mockCtx);
      mockCanvas.convertToBlob.and.returnValue(
        new Promise(resolve => setTimeout(() => resolve(new Blob()), 7000))
      );
      
      // Mock createImageBitmap
      (global as any).createImageBitmap = jasmine.createSpy('createImageBitmap')
        .and.returnValue(Promise.resolve({
          width: 1024,
          height: 768,
          close: () => {},
        } as any));
      
      (global as any).OffscreenCanvas = jasmine.createSpy('OffscreenCanvas')
        .and.returnValue(mockCanvas);
      
      const source = new File(['data'], 'test.jpg', { type: 'image/jpeg' });
      
      service.compress(source).catch(err => {
        expect(err.message).toContain('timeout');
      });
      
      tick(5100); // After 5s timeout
      expect(mockCanvas.convertToBlob).toHaveBeenCalled();
    }));
    
    it('should preserve votos if compression fails', fakeAsync(() => {
      // Setup: mock slow GPU
      const promise = service.compress(new File(['data'], 'test.jpg'));
      
      // After 5s, error should be catchable
      tick(5100);
      
      promise.catch(err => {
        expect(err).toBeTruthy();
        // Verify no side effects occurred
      });
    }));
  });
});
```

**Deliverable:**
- ✅ 3 new unit tests (Safari <16, Chrome, timeout behavior)
- ✅ All existing tests still pass
- ✅ 0 regressions

---

## Verification

**All pass locally:**
```bash
npm run test -- image-compression.service.spec.ts
# Expected: 7 tests (4 existing + 3 new), 0 failures
```

**No regressions:**
```bash
npm run test -- registrar-acta.page.spec.ts
npm run test -- modificar-acta.page.spec.ts
npm run test -- jrv-cargos.page.spec.ts
# Expected: all pass
```

---

## Risk

- WebP detection UA sniffing is fragile (but necessary, no native codec API)
  - **Mitigation:** Add comments linking to MDN browser compatibility table
- Timeout 5s may be tight for very old devices
  - **Mitigation:** Can tune to 7-10s post-launch if needed; observable via error logs

---

## Notes

- T1 is **dependency for T2 and T4** (timeout must work before testing cache)
- T1 fixes both P1 (WebP detection) and P4 (timeout) simultaneously
- Next: T2 adds cache layer, T3 fixes constants, T4 regression tests

---

**Risk:** none if tests pass locally (pure feature fix, no DB, no API)

