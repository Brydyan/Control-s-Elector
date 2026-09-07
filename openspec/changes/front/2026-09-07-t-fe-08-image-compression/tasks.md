# Tasks — T-FE-08: Captura y Compresión de Imagen

## Fase A: ImageService Setup

- [ ] **A.1** — Instalar Capacitor Camera (si no está):
  ```bash
  npm install @capacitor/camera
  npx cap sync
  ```

- [ ] **A.2** — Crear `core/services/image.service.ts`.
  - Inyectar: Camera (from @capacitor/camera)
  - Método: `captureAndCompress(): Observable<Blob>`
  - Llama Camera.getPhoto({quality: 90, format: 'jpeg'})
  - Retorna Observable

- [ ] **A.3** — En captureAndCompress:
  ```typescript
  async captureAndCompress(): Promise<Blob> {
    const photo = await Camera.getPhoto({
      quality: 90,
      format: 'jpeg',
      resultType: 'base64'
    });
    
    // Convertir base64 a blob, comprimir a WebP
    const blob = await this.compressToWebP(photo.base64String);
    return blob;
  }
  ```

**Compuerta A:** `ng build`. ImageService importable.

## Fase B: Compresión WebP

- [ ] **B.1** — Agregar método `private compressToWebP(base64: string): Promise<Blob>`:
  - Crear Image
  - Dibujar en Canvas
  - Canvas.toBlob('image/webp', 0.82) → Blob
  - Si Blob.size > 500KB, recomprimir a 0.65
  - Retornar Promise<Blob>

- [ ] **B.2** — Test: mock Camera, verificar que Canvas.toBlob se ejecuta.

- [ ] **B.3** — Test: Blob result tiene type 'image/webp'.

**Compuerta B:** `ng test` pasa. toBlob() es llamado, Blob es WebP.

## Fase C: Integración con Observable

- [ ] **C.1** — Cambiar `captureAndCompress()` a retornar `Observable<Blob>`:
  ```typescript
  captureAndCompress(): Observable<Blob> {
    return from(this.captureAndCompressAsync());
  }
  ```

- [ ] **C.2** — Crear barrel: `core/services/index.ts` que exporta ImageService.

- [ ] **C.3** — Test: llamar captureAndCompress().subscribe((blob) => { ... })

**Compuerta C:** Observable funciona, Blob emitido correctamente.

## Qué NO hacer

- **No agregar galería** — solo cámara
- **No agregar crop** — imagen tal cual
- **No guardar en IndexedDB** — eso es T-FE-09
- **No hacer upload** — servicio retorna Blob, el form lo sube

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | exit 0 |
| B | `ng test` | toBlob called, type === 'image/webp' |
| C | `ng test` | Observable emits Blob |

## Verificación por Mutación

- **V.1** — Cambiar 'image/webp' a 'image/jpeg' → type debe ser 'image/jpeg' → restaurar
- **V.2** — Comentar recompresión en 500KB → Blob puede ser >500KB → restaurar
