# Design — T-FE-20: Pipeline de Compresión de Imágenes en Cliente (Web Worker + WebP)

---

## D1 — Delegación de Compresión: Web Worker vs Main Thread Asíncrono

**Decisión**:
Usar compresión asíncrona mediante `OffscreenCanvas` encapsulado en `ImageCompressionService` con soporte de `Web Worker` (o fallback nativo en microtarea) para no bloquear el hilo de renderizado de Angular.
Al seleccionar un archivo:
1. Se genera un `createPreviewUrl(file)` inmediato con el archivo original para que el veedor vea la foto al instante (0ms de latencia percibida).
2. Se inicia la tarea de compresión en background con un signal reactivo `isCompressing = signal(false)`.
3. Al finalizar la compresión, se reemplaza `selectedImageFile` por el `File` comprimido WebP y se persiste en IndexedDB vía `guardarFotoOffline`.

**Alternativa descartada — Comprimir de forma síncrona en el Main Thread**:
Descartada porque procesar un archivo de 48 MP (12-25 MB) en el hilo principal causa un freeze de 1.5 a 3.5 segundos en dispositivos móviles de gama media/baja, provocando que el navegador muestre advertencias de "Página no responde".

---

## D2 — Resolución Máxima: 2048px vs 1200px vs Resolución Original

**Decisión**:
Fijar el límite de escala en **2048px en la dimensión mayor** (ratio preservado) con `createImageBitmap(blob, { resizeWidth: 2048, resizeQuality: 'medium' })`:
- 2048px ofrece suficiente densidad de píxeles (~3.1 Megapíxeles) para que un auditor electoral pueda hacer zoom al 200% y leer tachaduras, firmas de presidente de mesa y cifras escritas a lápiz o bolígrafo fino.
- Evita que el navegador decodifique la matriz completa de 8000x6000 píxeles en memoria RAM (lo cual consume ~190 MB de RAM sin comprimir).

**Alternativa descartada — 1200px**:
Descartada porque en actas de concejales con más de 15 candidatos y tablas densas, los números pequeños quedan borrosos o pixelados al ampliar.

**Alternativa descartada — Mantener resolución nativa**:
Descartada porque sube fotos de 20 MB, saturando la red móvil y agotando el almacenamiento innecesariamente.

---

## D3 — Formato y Estrategia de Calidad: WebP con Doble Pasada Adaptativa

**Decisión**:
- Formato prioritario: `image/webp`. Si el navegador no lo soporta (muy raro en navegadores post-2020), fallback automático a `image/jpeg`.
- **Doble pasada**:
  - Pasada 1: Calidad `0.80`. Si el Blob resultante es $\le 650\text{ KB}$, se acepta de inmediato.
  - Pasada 2: Si el Blob excede $650\text{ KB}$, se recomprime a calidad `0.65`.
- Se conserva el nombre de archivo con extensión `.webp` para coherencia en el bucket de Cloudflare R2 y el Content-Type HTTP.

**Alternativa descartada — Calidad fija 0.90**:
Descartada porque en fotos con mucho ruido visual o sombras del aula electoral, el archivo final supera fácilmente 1.5 MB.

---

## D4 — Manejo de Memoria y Prevención de Fugas

**Decisión**:
- Prohibición estricta de `FileReader.readAsDataURL` o strings Base64 para el almacenamiento o transmisión (regla D1 de T-FE-08). Todo el flujo opera con `Blob`, `File` binario y `URL.createObjectURL`.
- Invocación garantizada de `bitmap.close()` en bloque `finally` para liberar buffers GPU/C++.
- Revocación de URLs efímeras con `URL.revokeObjectURL` al destruir componentes o sustituir la foto.

---

## D5 — Estrategia de Almacenamiento Electoral: Cloudflare R2 + Backup Asíncrono en Proxmox

**Decisión**:
1. **En tiempo de elección**: El cliente envía el acta comprimida (~500 KB) a través de la infraestructura Cloud (R2) para máxima disponibilidad (99.99%) y rapidez para los veedores.
2. **Capacidad proyectada**: 4,000 actas × 0.5 MB = ~2.0 GB totales. Queda holgadamente dentro de los 10 GB gratuitos mensuales de R2.
3. **Resguardo / Backup local en Proxmox**:
   - Se ejecuta una sincronización unidireccional desacoplada (script cron con `rclone copy`) hacia el HDD de la VM Proxmox.
   - De esta forma, el HDD casero no forma parte del camino crítico de escritura de la jornada electoral, pero almacena la copia histórica definitiva a costo $0.
