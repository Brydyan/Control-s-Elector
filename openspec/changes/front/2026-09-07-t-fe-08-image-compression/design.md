# Design — T-FE-08: Captura y Compresión de Imagen

## D1 — Canvas.toBlob vs data:url

**Decisión:** Canvas.toBlob(). Controla quality, retorna Blob (uploadable).

**Alternativa descartada — img.src = canvas.toDataURL('image/webp').** Data URL es string (ineficiente), difícil de controlar quality.

## D2 — Ubicación de ImageService

**Decisión:** `core/services/image.service.ts`. Exportable vía `@core/services`.

**Alternativa descartada — en features/observer/.** Reutilizable en coordinator, master si también suben fotos.

## D3 — Recompresión: iterativa o de una pasada

**Decisión:** De una pasada. Si >500KB, recomprimir a 0.65. Sin loop.

**Alternativa descartada — loop: 0.82 → check → 0.65 → check → 0.50 → ...** Overkill. Dos pasos es suficiente.

## D4 — Manejo de error de cámara

**Decisión:** Si usuario cancela o no tiene permiso, retorna error Observable. El llamador decide qué hacer.

**Alternativa descartada — fallback a image upload.** MVP: solo cámara. Si falla, falla.
