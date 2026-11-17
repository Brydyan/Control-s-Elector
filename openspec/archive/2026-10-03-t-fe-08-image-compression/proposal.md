# Proposal — T-FE-08: Captura y Compresión de Imagen (WebP)

## Qué se construye
Servicio centralizado de captura y procesamiento fotográfico de actas electorales:
1. **Captura Multiplataforma**: Uso prioritario de `@capacitor/camera` en dispositivos móviles nativos, con fallback transparente a `<input type="file" accept="image/*" capture="environment">` en navegadores web de escritorio y PWA.
2. **Compresión WebP**: Conversión client-side usando `HTMLCanvasElement.toBlob('image/webp', 0.82)`. Si el tamaño resultante excede 500 KB, se realiza una segunda pasada automática reduciendo la calidad a 0.65 para garantizar una transmisión ágil en redes 2G/3G de recintos rurales.
3. **Contrato Multipart Estricto**: Empaquetado en `FormData` utilizando obligatoriamente la clave `'imagen'` requerida por el backend Spring Boot (`ObserverController: @RequestPart("imagen") MultipartFile imagen`) hacia la ruta `POST /api/v1/observer/actas/{id}/imagen`.

## Por qué
- Las cámaras modernas de smartphones generan archivos JPEG de 4 MB a 12 MB que colapsarían la subida concurrente y saturarían el ancho de banda del centro de cómputo.
- WebP ofrece una reducción de hasta el 70% sin perder legibilidad en los números manuscritos del acta.
- La estandarización de la clave `'imagen'` evita errores `MissingServletRequestPartException (HTTP 400)` observados al usar nombres genéricos como `'file'` o `'foto'`.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Captura | Capacitor Camera + Fallback HTML5 file input | Edición manual de recorte (crop libre) |
| Formato | Conversión estricta a WebP (`image/webp`) | Formatos pesados (BMP, TIFF, PNG sin comprimir) |
| Límite | Recompresión en dos niveles (<500 KB) | Chunked upload resumable |
| Upload | Endpoint oficial `POST /api/v1/observer/actas/{id}/imagen` con clave `'imagen'` | Modificación de actas ajenas |
| Integración | `ObserverService.subirImagenActa(actaId, file)` | Almacenamiento local de fotos pesadas en IndexedDB |

## Criterio de aceptación

- [ ] Captura de imagen disponible tanto en app compilada (Capacitor) como en navegador web.
- [ ] Compresión client-side a WebP con límite objetivo de 500 KB.
- [ ] Retorna un `Blob`/`File` tipado como `image/webp`.
- [ ] Envía la evidencia fotográfica usando `FormData.append('imagen', blob, 'acta.webp')`.
- [ ] Maneja rechazos de permisos de cámara con feedback claro al usuario.

## Fuera de alcance

- Reconocimiento OCR de caracteres manuscritos (planificado para fases posteriores).
- Detección automática de esquinas de papel.

## Dependencias

- T-FE-07 (Wizard paso 2 de fotos y llamada post-creación).
- Backend T-BE-14 (S3/RustFS endpoint `POST /api/v1/observer/actas/{id}/imagen`).
