# ODD T4 — Regression Tests (Page Integration)

**Reference:** T1, T2, T3 must be complete before this task  
**Depends on:** All prior tasks (T1, T2, T3)  
**Status:** Ready for Builder (after T1-T3 merged)  
**Effort:** 1 hour  
**Criticality:** HIGH — Verify no breakage in dependent pages

---

## Specs

### S1 — Registrar Acta Photo Flow (Full)

**Current flow:** User selects photo → compress → save to IndexedDB → preview shown  
**Risk:** Timeout in T1, cache in T2, constants in T3 might affect flow.

**Acceptance:**
- Photo selection triggers compress (no error)
- Compressed photo saved to IndexedDB (borrador)
- Preview displays (URL created + revoked correctly)
- Votos preserved if compress fails
- Test: Simulate photo selection → verify IndexedDB contains compressed file

**How to Test:**
```typescript
// 1. Select file
fileInput.files = [mockJpegFile];
fileInput.dispatchEvent(new Event('change'));

// 2. Verify compress called
expect(imageCompressionService.compress).toHaveBeenCalledWith(mockJpegFile);

// 3. Verify saved to IndexedDB
const saved = await offlineStorage.obtenerBorrador(draftId);
expect(saved.imageName).toContain('.webp');
expect(saved.imageBlob).toBeTruthy();

// 4. Verify preview URL created
expect(element.querySelector('img[src]')).toBeTruthy();
```

---

### S2 — Modificar Acta Photo Update (Full)

**Current flow:** User edits acta → updates photo → compress → persist  
**Risk:** Timeout might block UI while user editing other fields.

**Acceptance:**
- Photo update does not block vote entry
- Error message shown if compress fails
- Previous photo preview revoked
- Test: Update photo while form has unsaved votos → votos preserved

**How to Test:**
```typescript
// 1. Enter some votos
votoForm.votosPorCandidato.set(1, 100);

// 2. Try to update photo (slow GPU simulation)
mockCompressionService.compress.and.returnValue(
  delayedPromise(10000) // 10s delay
);
fileInput.dispatchEvent(new Event('change'));

// 3. Verify votos not cleared
tick(5100); // After timeout
expect(votoForm.votosPorCandidato.get(1)).toBe(100);

// 4. Error shown
expect(component.error.value).toContain('no se pudo optimizar');
```

---

### S3 — JRV Cargos Quick Capture (Full)

**Current flow:** Camera capture → auto-compress → preview in grid  
**Risk:** Timeout might make grid loading stall.

**Acceptance:**
- Quick capture completes within 6s timeout
- Photo added to grid even if compress takes 5s
- Multiple captures don't block each other
- Test: Simulate 5 quick captures → all appear in grid

**How to Test:**
```typescript
// 1. Start 5 captures
for (let i = 0; i < 5; i++) {
  component.onFotoCapturada(mockCameraFile);
}

// 2. Wait for all to timeout/complete
tick(10000);

// 3. Verify all 5 in grid (or error shown for each)
expect(component.fotosCapturadas.length).toBe(5);
expect(mockCompressionService.compress).toHaveBeenCalledTimes(5);
```

---

## Tasks

| ID | Linked Spec | Description | Route |
|-----|-------------|-------------|-------|
| **T4.1** | S1 | registrar-acta integration test | Inline |
| **T4.2** | S2 | modificar-acta integration test | Inline |
| **T4.3** | S3 | jrv-cargos quick capture test | Inline |
| **T4.4** | S1-S3 | Full suite pass + coverage | Inline |

---

## Implementation Checklist

### T4.1 — Registrar Acta Integration Test

**File:** `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts`

**New test to add:**

```typescript
describe('Photo compression integration (T1-T3)', () => {
  
  it('should compress and save photo to borrador without breaking votos', fakeAsync(() => {
    // Setup: component has votos entered
    component.votosPorCandidato.set(1, 100);
    component.votosPorCandidato.set(2, 50);
    
    const mockJpegFile = new File(
      [new ArrayBuffer(100 * 1024)],
      'photo.jpg',
      { type: 'image/jpeg' }
    );
    
    // Mock compression service to simulate T1 timeout behavior
    const mockCompressed = new File(
      [new ArrayBuffer(50 * 1024)],
      'photo.webp',
      { type: 'image/webp' }
    );
    
    imageCompressionService.compress.and.returnValue(
      Promise.resolve(mockCompressed)
    );
    
    // Simulate file selection (from T-FE-20 flow)
    const event = new Event('change');
    Object.defineProperty(event.target, 'files', {
      value: [mockJpegFile],
      writable: false,
    });
    
    component.onFileSelected(event as any);
    
    tick(100); // Let async compress run
    
    // Verify compress called
    expect(imageCompressionService.compress).toHaveBeenCalledWith(mockJpegFile);
    
    // Verify votos preserved
    expect(component.votosPorCandidato.get(1)).toBe(100);
    expect(component.votosPorCandidato.get(2)).toBe(50);
    
    // Verify preview URL created
    expect(imageCompressionService.createPreviewUrl).toHaveBeenCalled();
    
    // Verify no error shown (happy path)
    expect(component.error.value).toBeNull();
  }));
  
  it('should handle compression timeout gracefully', fakeAsync(() => {
    component.votosPorCandidato.set(1, 100);
    
    const mockJpegFile = new File([new ArrayBuffer(100 * 1024)], 'photo.jpg');
    
    // Mock timeout scenario (from T1)
    imageCompressionService.compress.and.returnValue(
      new Promise((_, reject) =>
        setTimeout(
          () => reject(new Error('convertToBlob timeout: GPU processing took >5s')),
          5100
        )
      )
    );
    
    const event = new Event('change');
    Object.defineProperty(event.target, 'files', {
      value: [mockJpegFile],
      writable: false,
    });
    
    component.onFileSelected(event as any);
    tick(5200); // After timeout
    
    // Verify error shown
    expect(component.error.value).toContain('no se pudo optimizar');
    
    // Verify votos NOT cleared (resilience)
    expect(component.votosPorCandidato.get(1)).toBe(100);
    
    // Verify no file saved
    expect(offlineStorageService.guardarFoto).not.toHaveBeenCalled();
  }));
});
```

**Deliverable:**
- ✅ 2 new integration tests (happy path + timeout)
- ✅ Existing registrar-acta tests still pass

---

### T4.2 — Modificar Acta Integration Test

**File:** `frontend/app/src/app/features/observer/pages/modificar-acta/modificar-acta.page.spec.ts`

**New test to add:**

```typescript
describe('Photo compression integration (T1-T3)', () => {
  
  it('should not block form while compression is in progress', fakeAsync(() => {
    // Setup: form with votes already entered
    component.votosPorCandidato.set(1, 100);
    
    const mockFile = new File([new ArrayBuffer(100 * 1024)], 'photo.jpg');
    
    // Mock slow compression (simulates weak GPU from T1)
    imageCompressionService.compress.and.returnValue(
      new Promise(resolve =>
        setTimeout(
          () => resolve(new File([new ArrayBuffer(50 * 1024)], 'photo.webp')),
          4000
        )
      )
    );
    
    // User selects photo
    const event = new Event('change');
    Object.defineProperty(event.target, 'files', { value: [mockFile], writable: false });
    component.onFileSelected(event as any);
    
    // User is still able to modify form while compress in progress
    tick(500); // After 500ms (before 4s compression done)
    component.votosPorCandidato.set(2, 50); // Form still editable
    expect(component.votosPorCandidato.get(2)).toBe(50);
    
    // After compression done
    tick(3600);
    
    // Photo should be updated
    expect(component.selectedImageFile).toBeTruthy();
    
    // Votos preserved
    expect(component.votosPorCandidato.get(1)).toBe(100);
    expect(component.votosPorCandidato.get(2)).toBe(50);
  }));
});
```

**Deliverable:**
- ✅ 1 new integration test (non-blocking compression)
- ✅ Existing modificar-acta tests still pass

---

### T4.3 — JRV Cargos Quick Capture Test

**File:** `frontend/app/src/app/features/observer/pages/jrv-cargos/jrv-cargos.page.spec.ts`

**New test to add:**

```typescript
describe('Photo compression integration (T1-T3)', () => {
  
  it('should handle multiple quick captures without blocking', fakeAsync(() => {
    const mockFiles = Array.from({ length: 5 }, (_, i) =>
      new File([new ArrayBuffer(100 * 1024)], `photo-${i}.jpg`)
    );
    
    imageCompressionService.compress.and.returnValue(
      new Promise(resolve =>
        setTimeout(
          () => resolve(new File([new ArrayBuffer(50 * 1024)], 'compressed.webp')),
          3000
        )
      )
    );
    
    // Simulate 5 quick captures
    mockFiles.forEach(file => {
      component.onFotoCapturada(file);
    });
    
    // All should be queued
    expect(imageCompressionService.compress).toHaveBeenCalledTimes(5);
    
    // After all complete
    tick(15000);
    
    // All photos should be in grid (or showing errors)
    expect(component.fotosCapturadas.length).toBeGreaterThanOrEqual(5);
  }));
});
```

**Deliverable:**
- ✅ 1 new integration test (parallel captures)
- ✅ Existing jrv-cargos tests still pass

---

### T4.4 — Full Test Suite

**File:** All page .spec.ts files

**Verification steps:**

```bash
# Run all observer page tests
npm run test -- \
  registrar-acta.page.spec.ts \
  modificar-acta.page.spec.ts \
  jrv-cargos.page.spec.ts

# Expected: 
# - registrar-acta: 2 new tests ✓
# - modificar-acta: 1 new test ✓
# - jrv-cargos: 1 new test ✓
# - All prior tests still pass ✓
```

**Coverage check:**

```bash
npm run test -- image-compression.service.spec.ts --coverage
# Expected: >90% coverage on ImageCompressionService
# Lines covered:
# - compress() method: both passes (happy + fallback)
# - supportsWebP(): both paths (true + false)
# - Timeout: abort signal fired
# - Cache: hit + miss scenarios
```

**Deliverable:**
- ✅ 4 new integration tests total (2 + 1 + 1)
- ✅ All prior tests pass (0 regressions)
- ✅ >90% coverage on ImageCompressionService

---

## Verification

**All pass locally:**
```bash
npm run test -- image-compression.service.spec.ts
npm run test -- registrar-acta.page.spec.ts
npm run test -- modificar-acta.page.spec.ts
npm run test -- jrv-cargos.page.spec.ts

# Expected: 15+ tests, 0 failures, >90% coverage
```

**No regressions in dependent services:**
```bash
npm run test -- offline-storage.service.spec.ts
npm run test -- analytics.service.spec.ts

# Expected: all pass
```

---

## Risk

- T4 depends on T1-T3 being implemented correctly
  - **Mitigation:** Each test validates specific T1-T3 behaviors (timeout, cache, constants)
- Timeout tests might be flaky on slow CI
  - **Mitigation:** Use fakeAsync/tick (deterministic), not real timers

---

## Notes

- T4 is final QA gate before merging feature branch
- Tests verify no behavioral changes from T1-T3 fixes
- After T4 passes: prepare PR with all 4 commits (T1 → T2 → T3 → T4)

---

**Risk:** none if mocks are set up correctly (all use fakeAsync/tick for determinism)

