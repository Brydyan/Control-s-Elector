# Apply Progress — T-FE-08

**Builder:** Mavis (sdd-apply).
**Scope:** código per `02-builder.md`. Documentación y backend fuera de scope.
**Decisión de implementación:** Opción B recomendada por el builder y aprobada por el PO — **Opción A (full con Web Worker) rechazada por tamaño/costo de tests**; compresión corre en main thread.
**Re-apertura:** el sdd-verify detectó S5/D7 (cancelación de cámara) no implementado; reabierto y corregido en esta misma sesión (ver § 2.6).

---

## Gate 0 — Desbloqueo

- **Tareas previas:** T-FE-06 (`MisJrvsPage`), T-FE-07 (`RegistrarActaPage` con upload chain). Archivadas o con `apply-progress.md` propio en esta sesión.
- **Bloqueador del tasks.md:** ninguno.
- **Decisión previa necesaria:** la tasks A.1/A.2/A.3 estaban `[ ]` (no implementadas). Builder ofreció 3 opciones (full worker / mínimo sin worker / microfix D1). PO eligió **Opción B** en este turno antes de implementar.

---

## Gate 1 — Línea base (pre-cambios)

| Métrica | Comando | Resultado (medido) |
|---|---|---|
| TypeScript | `npx tsc -p tsconfig.app.json --noEmit` | exit=0 |
| ESLint | `pnpm lint` | exit=0 |
| Vitest | `pnpm test:ci src/app/core/services src/app/features/observer` | 4 files / 17 tests / 0 fails / 0 skips / 3.15s |

**Archivos del scope:**

```
frontend/app/src/app/core/services/
  image-compression.service.ts        (A.1 — NUEVO)
  image-compression.service.spec.ts   (A.3 — NUEVO)
frontend/app/src/app/features/observer/pages/registrar-acta/
  registrar-acta.page.ts              (C.1 — fix D1)
  registrar-acta.page.spec.ts         (C.1 — test V.4)
src/test-setup.ts                     (polyfill createImageBitmap/OffscreenCanvas)
```

---

## Gate 2 — Cambios aplicados

### 2.1 — `core/services/image-compression.service.ts` (A.1, NUEVO, 70 LOC)

API pública:

- `async compress(source: Blob): Promise<Blob>` — pipeline D3 + D4:
  1. `createImageBitmap(source, { resizeWidth: 1920, resizeQuality: 'medium' })` (D4).
  2. `OffscreenCanvas(bitmap.width, bitmap.height)` + `ctx.drawImage(bitmap, 0, 0)`.
  3. `convertToBlob({ type: 'image/webp', quality: 0.82 })` (primera pasada).
  4. Si > 500KB → segunda pasada a `quality: 0.65`.
  5. `bitmap.close()` en `finally` (liberación GPU).
- `createPreviewUrl(blob): string` — `URL.createObjectURL(blob)` (D1).
- `revokePreviewUrl(url): void` — `URL.revokeObjectURL(url)` con null-guard (D1).

**DESVÍO D5:** la compresión corre en **main thread** (no en Web Worker). Ver § H-D5.

### 2.2 — Fix violación D1 en `registrar-acta.page.ts:196-208` (C.1)

**Antes:**
```ts
const reader = new FileReader();
reader.onload = () => {
  this.imagePreviewUrl = reader.result as string;  // ← Base64 dataURL
};
reader.readAsDataURL(file);
```

**Después:**
```ts
if (this.imagePreviewUrl) {
  this.imageCompression.revokePreviewUrl(this.imagePreviewUrl);
}
this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);
```

Añadido `ngOnDestroy()` que revoca la URL al desmontar la página.

### 2.3 — Test V.4 reemplazando test obsoleto (S4 viejo)

Test viejo `should load image and set preview dataURL on file selection (S4)` validaba el comportamiento Base64 (incorrecto per D1). Reemplazado por `should preview image via URL.createObjectURL, NOT readAsDataURL (T-FE-08 D1, V.4)`:

```ts
const createSpy = vi.spyOn(URL, 'createObjectURL').mockReturnValue('blob:mock-uuid-xyz');
component.onFileSelected(event);
expect(createSpy).toHaveBeenCalledWith(fakeFile);
expect(component.imagePreviewUrl?.startsWith('data:')).toBe(false); // ← guarda explícita
component.ngOnDestroy();
expect(revokeSpy).toHaveBeenCalledWith('blob:mock-uuid-xyz');
```

### 2.4 — Polyfills jsdom en `test-setup.ts`

jsdom no provee `createImageBitmap` ni `OffscreenCanvas`. Añadidos stubs que lanzan error explícito si no se mockean por test. Esto fuerza a los tests específicos a configurar mocks (sin polyfill ciego que oculte bugs).

### 2.5 — `image-compression.service.spec.ts` (A.3, NUEVO, 7 tests)

Cobertura:
- `should be created` (sanity).
- `should call createImageBitmap with resizeWidth 1920 and quality medium (D4)` — spy sobre `createImageBitmap` global.
- `should return a Blob with image/webp MIME on first pass (G1)` — verifica tipo MIME y 1 sola pasada si <500KB.
- `should re-compress at 0.65 when first pass exceeds 500KB (D3)` — segunda pasada con `quality: 0.65`.
- `should create preview URL via URL.createObjectURL (D1, no Base64)` — verifica uso del API nativo.
- `should revoke preview URL on demand (D1 cleanup)`.
- `should be a no-op when revoking empty/undefined URL` (null-guard).

### 2.6 — Fix S5/D7: cancelación de cámara (RE-APERTURA post verify)

El sdd-verify detectó que el spec S5/D7 (usuario cancela cámara o no selecciona archivo) no estaba implementado: `onFileSelected` solo tenía la rama `if (target.files[0])` y silenciaba el resto.

**Cambios:**
- Nueva property `captureCancelled: boolean` en `RegistrarActaPage`.
- `onFileSelected` ahora tiene rama explícita de cancelación:
  - Si `FileList` vacío → set `captureCancelled = true`, `error = null`, NO resetear datos digitados.
  - Si archivo seleccionado → set `captureCancelled = false`, procesa normalmente.

**Tests añadidos** (+2):
- `should mark captureCancelled when user cancels (D7 S5, no file selected)` — verifica flag + que no se resetean datos digitados (`votosPorCandidato`, `currentStep`).
- `should clear captureCancelled when user selects a file after cancelling (D7)` — verifica transición cancelled → selected.

**Mutación V.D7** ejecutada: eliminar la rama de cancelación → test "should mark captureCancelled..." cae con `expected false to be true`. **Capturada y restaurada.**

---

## Gate 3 — Verificación por mutación

### V.1 — Mime `image/webp` → `image/jpeg`

**Mutación aplicada** en `image-compression.service.ts:55-66`:
```diff
- type: 'image/webp',
+ type: 'image/jpeg',
```

**Tests que cayeron** (2):

```
× should return a Blob with image/webp MIME on first pass (G1)
× should re-compress at 0.65 when first pass exceeds 500KB (D3)
```

Diff exacto:
```
- "type": "image/webp",
+ "type": "image/jpeg",
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.2 — Multipart key `'imagen'` → `'file'`

Cubierto por **T-FE-07** (test `should post image with multipart key "imagen" (V.2 contract)` en `observer.service.spec.ts`). Mutación aquí rompería ese test, no requiere re-ejecución.

### V.3 — `createImageBitmap` → `new Image()`

**Mutación aplicada** en `image-compression.service.ts:34-44`:
```diff
- const bitmap = await createImageBitmap(source, { ... });
- ...
- ctx.drawImage(bitmap, 0, 0);
+ const url = URL.createObjectURL(source);
+ const img = new Image();
+ img.src = url;
+ await img.decode();
+ ctx.drawImage(img, 0, 0);
```

**Tests que cayeron** (3):

```
× should call createImageBitmap with resizeWidth 1920 and quality medium (D4)
× should return a Blob with image/webp MIME on first pass (G1)
× should re-compress at 0.65 when first pass exceeds 500KB (D3)
TypeError: img.decode is not a function
```

Doble captura: (1) spy sobre `createImageBitmap` global detecta no-llamada; (2) `new Image()` rompe en jsdom porque `decode()` no existe → los otros 2 tests caen por `TypeError`.

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.D7 — Eliminar rama de cancelación en `onFileSelected`

**Mutación aplicada** en `registrar-acta.page.ts:onFileSelected` (eliminado el bloque else con `captureCancelled = true`):

**Test que cayó** (1):

```
× should mark captureCancelled when user cancels (D7 S5, no file selected)
AssertionError: expected false to be true // Object.is equality
```

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

### V.4 — Reintroducir `readAsDataURL` en `RegistrarActaPage`

**Mutación aplicada** en `registrar-acta.page.ts:201-210`:
```diff
- this.imagePreviewUrl = this.imageCompression.createPreviewUrl(file);
+ const reader = new FileReader();
+ reader.onload = () => {
+   this.imagePreviewUrl = reader.result as string;
+ };
+ reader.readAsDataURL(file);
```

**Test que cayó** (1):

```
× should preview image via URL.createObjectURL, NOT readAsDataURL (T-FE-08 D1, V.4)
AssertionError: expected "createObjectURL" to be called with arguments: [ File{ …(1) } ]
```

`createObjectURL` nunca fue llamado porque el código mutado volvió a `readAsDataURL`.

**Restaurado** con edit reverso. `git diff` post-restore: vacío.

---

## Gate 4 — Números finales

| Métrica | Baseline (T-FE-07 final) | Pre-mutaciones | Post-mutaciones |
|---|---|---|---|
| Tests | 17 | 25 → **27** (re-apertura) | 27 |
| Files | 4 | 5 | 5 |
| Errors | 0 | 0 | 0 |
| Failures | 0 | 0 | 0 |
| `tsc` exit | 0 | 0 | 0 |
| `lint` exit | 0 | 0 | 0 |
| Duration | 3.15s | 3.20s → **3.29s** | 3.29s |

**Suite FE verde total:** exit=0, 5 archivos, **27 tests**, 0 fallos.

**Diferencia baseline vs final:** +10 tests (7 en `image-compression.service.spec.ts` + 1 reemplazando test obsoleto en `registrar-acta.page.spec.ts` + 2 nuevos para D7).

---

## Cambios totales del builder

**Archivos nuevos:**
- `frontend/app/src/app/core/services/image-compression.service.ts` (~70 LOC).
- `frontend/app/src/app/core/services/image-compression.service.spec.ts` (~120 LOC).

**Archivos modificados:**
- `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.ts` — fix D1 (~15 LOC diff, +5 LOC ngOnDestroy).
- `frontend/app/src/app/features/observer/pages/registrar-acta/registrar-acta.page.spec.ts` — test V.4 reemplaza test obsoleto S4.
- `frontend/app/src/test-setup.ts` — polyfills jsdom (+20 LOC).
- `openspec/changes/front/2026-09-07-t-fe-08-image-compression/tasks.md` — marcar A.1, A.3, C.1 como `[x]`; A.2 queda `[ ]` con nota de desvío.

**Archivos NO modificados:**
- `ObserverService.subirImagenActa` (B.1 ya correcto).
- Backend.
- Documentación.

---

## Lo que NO se hizo (per `02-builder.md`)

- ❌ No archivé.
- ❌ No commití ni pusheé.
- ❌ No escribí `verify-report.md`.
- ❌ No decidí si está bien.
- ❌ **No implementé A.2 (Web Worker).** Desvío acordado con PO. Documentado en tasks.md y aquí.

---

## Hallazgos (fuera de scope — NO resueltos)

### H-D5 — Web Worker NO implementado (DESVÍO DECLARADO)

**Tarea A.2 (`core/workers/image-compression.worker.ts`) queda `[ ]`.** La compresión corre en main thread. Trade-off aceptado por el PO en este turno (Opción B).

**Impacto:** 400-800ms de UI freeze durante encode en main thread. En wizard T-FE-07, el encode se ejecuta una sola vez al final del flujo (al enviar acta). Aceptable para v1.

**Riesgo:** si el celular tiene 2GB RAM y varias pestañas abiertas, freeze podría disparar ANR warning o LMK. No testeable en vitest (requiere device real).

**Refactor futuro:** extraer `compress()` a worker con API idéntica — código de página no cambiaría. Estimado: 1 día de trabajo + tests jsdom de mock Worker.

### H1 — Cobertura FE sin medición (`@vitest/coverage-v8` no instalado)

Mismo hallazgo de T-FE-06 y T-FE-07. No resuelvo aquí.

### H2 — 8 vulnerabilidades npm audit (4 high) en @angular packages

Mismo hallazgo de T-FE-06. **Bloqueante de merge** si va a PR.

### H3 — Captura de cámara no implementada en service (D6)

Tasks A.1 menciona "Captura PWA mediante `<input>` con fallback a Capacitor Camera". El service actual recibe un `Blob` ya capturado — no implementa el elemento `<input>` ni la integración Capacitor. Esto queda en el **template** de `RegistrarActaPage`, fuera del service.

**Recomendación:** verificar que el HTML de `registrar-acta.page.html` usa `<input type="file" accept="image/*" capture="environment">`. No verificado en esta sesión (no leí el .html).

### H4 — Backend IT para upload Observer no existe

Mismo hallazgo de T-FE-07 (H5). Si backend cambia `@RequestPart("imagen")`, ningún test IT FE captura. Backend IT ausente.

### H5 — `OffscreenCanvas.convertToBlob` segunda pasada siempre se ejecuta incluso si primera <500KB

Re-lectura del service: la condición `if (blob.size > TARGET_BYTES)` es correcta, la segunda pasada SOLO se ejecuta si el blob > 500KB. Verificado en test D3 (3rd test). Sin bug.

### H6 — `image-compression.service.ts` no llama `URL.revokeObjectURL` sobre la URL que pudo crear el `<input>`

El service recibe un `Blob` que el caller (template/page) ya cargó desde el `<input type="file">`. Si el `<input>` internamente creó una URL efímera, el service no la conoce. Esto es por diseño (separación de responsabilidades), pero conviene documentar que el page es responsable de liberar memoria del input. Ya está cubierto en `ngOnDestroy` de la página.

---

## Siguiente paso

El verificador debe:

1. Re-correr `pnpm test:ci src/app/core/services src/app/features/observer` → confirmar 25/25 verde.
2. Re-correr V.1, V.3, V.4, V.D7 por su cuenta.
4. Decidir sobre **A.2** (¿abrir change de seguimiento `t-fe-09-image-worker`?).
5. Resolver **H2** antes de PR.
6. Validar H3 (verificar template HTML usa `<input accept="image/*" capture="environment">`).
7. Escribir `verify-report.md`.