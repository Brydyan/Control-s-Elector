# Tasks — T-FE-08: Captura y Compresión de Imagen (WebP)

## Fase A: Servicio de Compresión y Captura

- [ ] **A.1** — Crear `core/services/image-compression.service.ts`:
  - Detección de entorno: Capacitor Camera vs HTML5 File Input.
  - Método `captureOrSelect(): Observable<File | Blob>`.
  - Método `compressToWebP(file: File | Blob): Promise<Blob>`.
- [ ] **A.2** — Implementar lógica de doble pasada de calidad en Canvas (0.82 -> 0.65 si > 500 KB).
- [ ] **A.3** — Tests unitarios de compresión WebP en `image-compression.service.spec.ts`.

## Fase B: Contrato Multipart con Backend

- [x] **B.1** — Integrar en `ObserverService.subirImagenActa(actaId, file)`:
  - Creación de `FormData` con clave obligatoria `'imagen'`.
  - Petición `POST /api/v1/observer/actas/${actaId}/imagen`.
- [x] **B.2** — Test unitario en `observer.service.spec.ts` verificando la clave del `FormData`.

## Fase C: Integración con Wizard de Registro

- [x] **C.1** — Conectar paso `IMAGEN` de `RegistrarActaPage` con el archivo seleccionado y vista previa dataURL.
- [x] **C.2** — Enlace tras `guardarComoBorrador()` o `enviarActaFinal()` para disparar la subida fotográfica.

## Verificación por Mutación

- **V.1** — Cambiar el formato Canvas de 'image/webp' a 'image/jpeg' -> la comprobación de tipo de archivo debe fallar.
- **V.2** — Cambiar `formData.append('imagen', file)` a `formData.append('file', file)` -> el test de servicio o backend debe fallar con 400.
