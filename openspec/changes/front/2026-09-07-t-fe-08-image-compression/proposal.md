# Proposal — T-FE-08: Captura y Compresión de Imagen (WebP)

## Qué se construye
Servicio que captura foto del acta desde la cámara, la comprime a WebP, y la sube al backend.

**Flujo:**
1. Observer toca "Tomar foto"
2. Se abre cámara (Capacitor Camera)
3. Imagen se comprime a WebP (calidad 0.82)
4. Si >500KB, recomprimir a calidad 0.65
5. Subir al backend (que la sube a Supabase)

## Por qué
- WebP es ~25% más pequeño que JPEG.
- Capacitor accede a cámara nativa.
- Recompresión previene imágenes gigantes.
- Necesario para T-FE-07 (foto del acta).

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Captura | Camera plugin (Capacitor) | galería |
| Compresión | Canvas.toBlob(WebP) con calidad | JPEG, PNG |
| Recompresión | Si >500KB, bajar a 0.65 | múltiples pasadas |
| Upload | POST /api/evidencia con blob | chunked upload |
| Service | ImageService centralizador | integration en form |

## Criterio de aceptación

- [ ] ImageService.captureAndCompress() abre cámara
- [ ] Retorna Observable<Blob> (WebP)
- [ ] Tamaño <500KB (recomprimir si necesario)
- [ ] Test: mock Camera, verificar que Canvas.toBlob() se ejecuta
- [ ] Test: Blob generado es formato WebP

## Fuera de alcance

- Galería (solo cámara)
- Crop de imagen
- Historial de imágenes
- Fallback a foto web (Ionic/web no tiene cámara)

## Dependencias

- T-FE-01 (estructura)
- Capacitor: `npm install @capacitor/camera`

## Decisiones a tomar

**D1 — Canvas.toBlob vs img.src data:url:** Usar Canvas.toBlob (controla calidad explícitamente).

**D2 — dónde vive ImageService:** En `core/services/image.service.ts`.
