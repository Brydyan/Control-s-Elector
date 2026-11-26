# ODD T2 — Cache supportsWebP() Result

**Reference:** T1 must be complete before starting  
**Depends on:** `t1-image-compression-webp-timeout-fix.md` (S1 implementation)  
**Status:** Ready for Builder (after T1)  
**Effort:** 30-45 minutes  
**Criticality:** HIGH — Performance optimization, not blocking

---

## Specs

### S1 — Cache WebP Support at Service Init

**Current Bug:** Lines 58 (every compress() call) → `this.supportsWebP()` recalculated.  
Result: 15 compressions = 15 OffscreenCanvas allocations for redundant detection.

**Acceptance:**
- `supportsWebP()` called once at service initialization
- Result cached in private field (never recalculated)
- Each compress() reads cached value (no method call)
- Test: Verify spy shows exactly 1 call to supportsWebP() per 10 compress() invocations

**How to Test:**
```typescript
it('should cache supportsWebP result', () => {
  spyOn(service, 'supportsWebP').and.callThrough();
  
  // Compress 5 images
  for (let i = 0; i < 5; i++) {
    service.compress(mockFile);
  }
  
  // supportsWebP() called once (at init), not 5 times
  expect(service['supportsWebP']).toHaveBeenCalledTimes(1);
});
```

---

### S2 — Lazy Init Optional (Nice-to-Have)

**Acceptance (optional):**
- If service not used, supportsWebP() not called (save CPU on startup)
- First compress() triggers cache init
- Subsequent compress() calls use cached value

**How to Test:**
```typescript
// Service created but not used
const service = new ImageCompressionService();
// supportsWebP NOT called yet

// First compress
await service.compress(mockFile);
// supportsWebP called once

// Second compress
await service.compress(mockFile);
// supportsWebP NOT called again
```

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T2.1** | S1 | Add private cache field | Inline |
| **T2.2** | S1 | Initialize cache on first compress() | Inline |
| **T2.3** | S2 | Unit test: cache verification | Inline |

---

## Implementation Checklist

### T2.1 — Add Cache Field

**File:** `frontend/app/src/app/core/services/image-compression.service.ts`

**Add at class level (after line 27, before line 29):**
```typescript
/** Cache WebP support result (computed once at first use). */
private cachedWebPSupport: boolean | null = null;
```

**Deliverable:**
- ✅ Private field added
- ✅ Initialized to null (lazy pattern)

---

### T2.2 — Use Cache in compress()

**File:** `frontend/app/src/app/core/services/image-compression.service.ts`

**Current (line 58):**
```typescript
const mimeType = this.supportsWebP() ? 'image/webp' : 'image/jpeg';
```

**New:**
```typescript
// Initialize cache on first use (lazy init)
if (this.cachedWebPSupport === null) {
  this.cachedWebPSupport = this.supportsWebP();
}

const mimeType = this.cachedWebPSupport ? 'image/webp' : 'image/jpeg';
```

**Deliverable:**
- ✅ Cache checked before method call
- ✅ Lazy initialization on first compress()
- ✅ Subsequent calls use cached value

---

### T2.3 — Tests

**File:** `frontend/app/src/app/core/services/image-compression.service.spec.ts`

**New test to add:**

```typescript
describe('Cache behavior', () => {
  
  it('should cache supportsWebP() result across multiple compress() calls', async () => {
    // Create a spy on the private method via prototype
    spyOn<any>(service, 'supportsWebP').and.callThrough();
    
    const mockFile = new File(['data'], 'test.jpg', { type: 'image/jpeg' });
    
    // Mock createImageBitmap and OffscreenCanvas for all calls
    (global as any).createImageBitmap = jasmine.createSpy('createImageBitmap')
      .and.returnValue(Promise.resolve({
        width: 1024,
        height: 768,
        close: () => {},
      } as any));
    
    const mockCanvas = jasmine.createSpyObj('OffscreenCanvas', ['getContext', 'convertToBlob']);
    const mockCtx = jasmine.createSpyObj('CanvasRenderingContext2D', ['drawImage']);
    mockCanvas.getContext.and.returnValue(mockCtx);
    mockCanvas.convertToBlob.and.returnValue(Promise.resolve(new Blob([], { type: 'image/webp' })));
    
    (global as any).OffscreenCanvas = jasmine.createSpy('OffscreenCanvas')
      .and.returnValue(mockCanvas);
    
    // First compress: supportsWebP() called (cache init)
    await service.compress(mockFile);
    expect((service as any)['supportsWebP']).toHaveBeenCalledTimes(1);
    
    // Second compress: supportsWebP() NOT called (cache hit)
    await service.compress(mockFile);
    expect((service as any)['supportsWebP']).toHaveBeenCalledTimes(1); // Still 1
    
    // Third compress: still cached
    await service.compress(mockFile);
    expect((service as any)['supportsWebP']).toHaveBeenCalledTimes(1); // Still 1
  });
  
  it('should lazy-init cache on first compress()', async () => {
    // Service created but not used
    expect((service as any)['cachedWebPSupport']).toBeNull();
    
    // First compress
    const mockFile = new File(['data'], 'test.jpg', { type: 'image/jpeg' });
    await service.compress(mockFile);
    
    // Cache now populated
    expect((service as any)['cachedWebPSupport']).not.toBeNull();
    expect(typeof (service as any)['cachedWebPSupport']).toBe('boolean');
  });
});
```

**Deliverable:**
- ✅ 2 new tests (cache hit, lazy init)
- ✅ Existing tests still pass
- ✅ 0 regressions

---

## Verification

**All pass locally:**
```bash
npm run test -- image-compression.service.spec.ts
# Expected: 9 tests (7 from T1 + 2 new), 0 failures
```

**No regressions:**
```bash
npm run test -- registrar-acta.page.spec.ts modificar-acta.page.spec.ts jrv-cargos.page.spec.ts
# Expected: all pass
```

---

## Risk

- None. Pure performance optimization, no behavior change.

---

## Notes

- T2 depends on T1 (S1 implementation must be complete)
- After T1 + T2: combine into one feature branch before T3
- Observer pages (registrar-acta, etc.) should see no behavioral change, only faster startup

---

**Risk:** none

