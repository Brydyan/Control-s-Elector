# Tasks — T-FE-08: Captura y Compresión de Imagen (WebP & Optimización RAM)

## Fase A: Servicio de Compresión, Web Worker y Captura PWA

- [ ] **A.1** — Implementar `core/services/image-compression.service.ts`:
  - Captura PWA mediante `<input type="file" accept="image/*" capture="environment">` con detección y fallback secundario a Capacitor Camera si aplica.
  - Decodificación y desescalado nativo directo en buffer GPU/C++ usando `createImageBitmap(blob, { resizeWidth: 1920, resizeQuality: 'medium' })`.
  - Prohibición estricta de Base64 (`readAsDataURL`): manejo exclusivo de `Blob` / `File` binario y URLs efímeras con `URL.createObjectURL()`.
  - Liberación inmediata de memoria con `URL.revokeObjectURL(url)` una vez proyectada la vista previa.
- [ ] **A.2** — Implementar Web Worker `core/workers/image-compression.worker.ts`:
  - Recepción de `ImageBitmap` o `Blob` transferible.
  - Dibujo y renderizado offscreen usando `OffscreenCanvas`.
  - Compresión a `image/webp` con calidad inicial 0.82; reintento a 0.65 si el resultado supera 500 KB.
  - Cierre explícito de bitmaps con `bitmap.close()`.
- [ ] **A.3** — Tests unitarios de compresión y memoria en `image-compression.service.spec.ts`:
  - Verificar llamada a `createImageBitmap` con `{ resizeWidth: 1920, resizeQuality: 'medium' }`.
  - Verificar generación de `Blob` con MIME type exacto `image/webp`.
  - Verificar ausencia total de strings Base64 (`data:image/`) en el heap / pipeline de procesamiento.
  - Verificar invocación de `URL.revokeObjectURL()` tras generación de vistas previas.

## Fase B: Contrato Multipart con Backend

- [x] **B.1** — Integrar en `ObserverService.subirImagenActa(actaId, file)`:
  - Creación de `FormData` con clave obligatoria `'imagen'` conteniendo el `Blob` WebP.
  - Petición `POST /api/v1/observer/actas/${actaId}/imagen`.
- [x] **B.2** — Test unitario en `observer.service.spec.ts` verificando la clave del `FormData`.

## Fase C: Integración con Wizard de Registro

- [x] **C.1** — Conectar paso `IMAGEN` de `RegistrarActaPage` con el archivo seleccionado y vista previa mediante `URL.createObjectURL()` (sin Base64).
- [x] **C.2** — Enlace tras `guardarComoBorrador()` o `enviarActaFinal()` para disparar la subida fotográfica comprimida.

## Verificación por Mutación

- **V.1** — Cambiar el formato Canvas/Worker de `'image/webp'` a `'image/jpeg'` -> la comprobación de tipo MIME de archivo debe fallar.
- **V.2** — Cambiar `formData.append('imagen', file)` a `formData.append('file', file)` -> el test de servicio o backend debe fallar con 400.
- **V.3** — Sustituir `createImageBitmap` por decodificación clásica en DOM (`new Image()` o `ctx.drawImage` directo) -> test de mutación de desescalado GPU/C++ debe fallar.
- **V.4** — Introducir conversión a Base64 (`readAsDataURL` o string `data:image`) en el pipeline de previsualización o compresión -> test de detección de Base64 / heap memory debe fallar.
