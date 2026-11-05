# Flujo 08 — Pipeline de Compresión de Imagen en el Cliente

Escenario: captura de fotografía de alta resolución (celulares modernos 48-108 MP) y compresión adaptativa a WebP 2048px antes de almacenar o transmitir.

```mermaid
sequenceDiagram
    actor OBS as Observador Electoral
    participant CAM as Cámara / Galería (Móvil)
    participant UI as Vista Angular (Registrar/Corregir)
    participant SVC as ImageCompressionService
    participant GPU as createImageBitmap (GPU/C++)
    participant CANVAS as OffscreenCanvas (2D Context)
    participant IDB as IndexedDB (Offline Storage)

    OBS->>CAM: Captura fotografía del acta física
    CAM-->>UI: File {type: image/jpeg, size: ~18MB, 6000x4000}

    UI->>UI: URL.createObjectURL(file) -> Preview visual instantáneo (0ms lag)
    UI->>UI: comprimiendoImagen.set(true) -> Bloquea envío temporal

    UI->>SVC: compress(file)
    SVC->>GPU: createImageBitmap(blob, { resizeWidth: 2048, resizeQuality: 'medium' })
    GPU-->>SVC: ImageBitmap decodificado y escalado (buffer GPU)

    SVC->>CANVAS: drawImage(bitmap, 0, 0)
    Note over SVC,GPU: bitmap.close() liberando memoria GPU de inmediato

    SVC->>CANVAS: convertToBlob({ type: 'image/webp', quality: 0.80 })
    CANVAS-->>SVC: Blob WebP (~520 KB)

    alt Tamaño > 650 KB (foto con mucho ruido o sombra)
        SVC->>CANVAS: convertToBlob({ type: 'image/webp', quality: 0.65 })
        CANVAS-->>SVC: Blob WebP recomprimido (~450 KB)
    end

    SVC-->>UI: File {name: "acta.webp", type: "image/webp", size: ~500KB}
    UI->>UI: selectedImageFile = compressedFile
    UI->>UI: comprimiendoImagen.set(false) -> Habilita envío

    UI->>IDB: guardarFotoOffline(compressedFile)
    IDB-->>UI: ✅ Foto WebP segura en disco local

    alt Transmisión Online
        UI->>UI: enviarActaDirecta() con WebP ligero (~500 KB)
    else Sin Conexión
        UI->>IDB: encolarActa(payload, compressedFile)
    end
```
