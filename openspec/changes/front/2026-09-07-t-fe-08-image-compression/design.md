# Design — T-FE-08: Captura y Compresión de Imagen (WebP & RAM Optimization)

---

## D1 — Blobs Binarios Puros (Base64 prohibido en heap JS)

**Decisión**: Manejo estricto de `Blob` y `File` binarios nativos. El uso de strings Base64 (`readAsDataURL`, `data:image/...`) queda terminantemente prohibido en el heap de JavaScript para evitar picos de asignación de hasta 60MB (un string dataURL cuadruplica el tamaño del buffer y satura el Garbage Collector de V8 en móviles de 2GB RAM). Para previsualizaciones locales efímeras se emplea exclusivamente `URL.createObjectURL(blob)` con revocación inmediata vía `URL.revokeObjectURL()`.

**Alternativa descartada — img.src = canvas.toDataURL('image/webp')**: Ineficiente; genera strings gigantescos que no se liberan rápidamente de memoria y disparan el LMK (Low Memory Killer) de Android.

---

## D2 — Ubicación de ImageCompressionService y Web Worker

**Decisión**: Servicio central en `core/services/image-compression.service.ts` exportable vía `@core/services`, coordinando la interfaz con un worker dedicado `core/workers/image-compression.worker.ts` para ejecutar el encodeo fuera del hilo principal.

**Alternativa descartada — En features/observer/**: No modular; la compresión es requerida también por contingencia de coordinador y otros módulos de captura.

---

## D3 — Recompresión y Control de Calidad Objetivo

**Decisión**: Pasada inicial a calidad 0.82 (objetivo 300-500KB). Si el blob resultante supera 500KB, se ejecuta una segunda pasada a calidad 0.65 sin loops infinitos.

**Alternativa descartada — Loop iterativo indefinido (0.82 -> 0.70 -> 0.55...)**: Overhead innecesario de CPU y batería en campo. Dos pasadas acotan el tiempo de procesamiento a < 300ms.

---

## D4 — Desescalado C++/GPU Directo con `createImageBitmap`

**Decisión**: Usar `createImageBitmap(blob, { resizeWidth: 1920, resizeQuality: 'medium' })` para decodificar y desescalar la imagen capturada directamente en el subsistema C++/GPU del navegador antes de tocar la memoria JavaScript. Esto reduce el buffer de imagen crudo de ~48MB (4000x3000 a 4 bytes/pixel RGBA) a solo ~8MB (1920x1080 a 4 bytes/pixel RGBA), evitando que el heap de Chrome se infle y colapse.

**Alternativa descartada — `HTMLImageElement` (`new Image()`) + `ctx.drawImage` clásico**: Asigna la imagen completa no comprimida (48MB+) en la memoria principal antes de poder dibujarla en el canvas, provocando out-of-memory en terminales con 2GB de RAM.

---

## D5 — Compresión WebP en Web Worker con `OffscreenCanvas` y Limpieza Inmediata

**Decisión**: La rasterización y compresión a formato `image/webp` se delega a un Web Worker que recibe el `ImageBitmap` transferible (o lo crea a partir del `Blob`) y dibuja sobre un `OffscreenCanvas`. Al finalizar la conversión con `canvas.convertToBlob({ type: 'image/webp', quality })`, se cierran los bitmaps con `bitmap.close()` y se invoca inmediatamente `URL.revokeObjectURL()` sobre cualquier URL efímera creada para la previsualización una vez cargada.

**Alternativa descartada — Compresión en hilo principal (main thread)**: Bloquea la UI de Ionic/Angular durante 400-800ms, congelando animaciones y dando sensación de cuelgue o provocando advertencias de ANR (Application Not Responding).

---

## D6 — Captura en PWA mediante `<input type="file" accept="image/*" capture="environment">`

**Decisión**: En el runtime PWA principal, la captura se dispara mediante un elemento `<input type="file" accept="image/*" capture="environment">` oculto. Esto invoca directamente la aplicación de cámara nativa del sistema operativo con la lente trasera (`environment`) sin requerir permisos persistentes de getUserMedia ni el overhead de streaming WebRTC. En builds nativos (Capacitor APK), se puede utilizar `@capacitor/camera` como contingencia.

**Alternativa descartada — getUserMedia / WebRTC stream en vivo**: Mantiene la cámara y el visor de video activos en memoria dentro del DOM de la página, consumiendo más de 80MB continuos de memoria gráfica y provocando cierre forzado de la pestaña por LMK en Android.

---

## D7 — Manejo de Error y Cancelación de Cámara

**Decisión**: Si el usuario cancela la captura o el sistema reporta error, el observable/promise retorna un estado controlado (`CAPTURE_CANCELLED`). El flujo UI mantiene el estado previo sin resetear datos digitados.

**Alternativa descartada — Fallback silencioso o reload**: Causaría frustración al veedor y pérdida de avance en la mesa.
