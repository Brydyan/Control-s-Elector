# Proposal — T-FE-20: Pipeline de Compresión de Imágenes en Cliente (Web Worker + WebP) y Optimización de Almacenamiento

## Qué se construye
Implementación y cableado completo del pipeline de compresión y redimensionamiento de imágenes en el cliente web/móvil para la captura y subida de actas electorales (`RegistrarActaPage`, `CorreccionActaPage`, `JrvCargosPage`):
1. **Pipeline de optimización para celulares modernos**:
   - Redimensionamiento acelerado por hardware a un ancho máximo de **2048px** mediante `createImageBitmap` con `resizeQuality: 'medium'` (evita OOM en imágenes de 48-108 MP).
   - Conversión a formato **WebP** con doble pasada adaptativa: primera pasada a calidad `0.80`, y si el tamaño resultante excede **650 KB**, segunda pasada a calidad `0.65`.
   - Delegación de la compresión a un **Web Worker** dedicado (o ejecución asíncrona no bloqueante) para mantener la interfaz a 60 FPS sin congelar el hilo principal de Angular mientras se procesan fotos de 15-40 MB.
2. **Cableado en flujos de captura**:
   - Conectar la compresión en `RegistrarActaPage.onFileSelected` y `guardarFotoOffline`.
   - Conectar la compresión en `CorreccionActaPage.onFileSelected`.
   - Conectar la compresión en `JrvCargosPage` (captura rápida por foto).
   - Indicador visual no intrusivo de optimización (`Procesando imagen...`) mientras la compresión se completa.
3. **Persistencia y transmisión ligera**:
   - Guardar en IndexedDB y transmitir a Cloudflare R2 / Backend exclusivamente el binario comprimido WebP (`~450-650 KB`), reduciendo en más del 95% el consumo de red y almacenamiento.
4. **Validaciones de seguridad**:
   - Límite duro: rechazo de imágenes que tras compresión superen 2.5 MB.
   - Verificación de mime-type válido (`image/webp`, `image/jpeg`, `image/png`).

## Por qué
- La auditoría arquitectónica evidenció que aunque existe `ImageCompressionService`, **nunca es invocado** en `RegistrarActaPage` ni en los flujos de captura; se estaban pasando archivos crudos de 10-25 MB directo a IndexedDB y al backend.
- En recintos electorales rurales o con congestión de red móvil, subir fotos de 15 MB causa timeouts, saturación de la cola offline y bloqueo de la UI.
- Garantiza que toda la elección provincial (aprox. 3,200 a 4,000 actas) ocupe menos de **2.5 GB en Cloudflare R2**, operando 100% dentro del tier gratuito de 10 GB/mes sin costo alguno para el cliente.

## Alcance

| Elemento | Incluye | No incluye |
|---|---|---|
| **Vistas** | `RegistrarActaPage`, `CorreccionActaPage`, `JrvCargosPage` | Vistas de auditoría de solo lectura |
| **Servicios** | `ImageCompressionService` actualizado con soporte de pipeline robusto para fotos gigantes (>20MB) | Modificación del almacenamiento backend / R2 |
| **Worker / Proceso** | OffscreenCanvas / Worker para compresión sin congelar UI | Modelos de OCR o visión artificial |
| **Almacenamiento** | Guardado de Blobs WebP optimizados en IndexedDB (`offlineStorage`) | Scripts externos de sincronización `rclone` (se documentan como guía operativa) |

## Criterios de Aceptación
- [ ] Cualquier fotografía capturada por el usuario (incluso fotos crudas >15 MB) es comprimida a WebP con ancho máx 2048px antes de almacenarse en IndexedDB o enviarse por red.
- [ ] El tamaño de salida de cada imagen comprimida se sitúa entre 300 KB y 700 KB (promedio ≤ 500 KB), preservando la nitidez de firmas, sellos y números manuscritos.
- [ ] La interfaz de usuario no se congela de forma apreciable durante la compresión.
- [ ] Si la compresión falla o el archivo no es una imagen válida, se notifica un error amigable sin perder los votos digitados.
- [ ] La suite completa de pruebas unitarias verifica la reducción de peso, los límites y el cableado en los componentes.
