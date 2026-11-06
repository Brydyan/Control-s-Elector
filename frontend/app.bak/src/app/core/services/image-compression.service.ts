import { Injectable } from '@angular/core';

/**
 * Compresión de imagen a WebP en hilo principal.
 *
 * Decisión de implementación (ver openspec/changes/front/2026-09-07-t-fe-08-image-compression/design.md):
 * - D1: manejo estricto de Blob/File binarios; Base64 prohibido en heap JS.
 * - D3: dos pasadas de calidad (0.82 → 0.65 si > 500KB); sin loops.
 * - D4: decodificación GPU/C++ vía `createImageBitmap(blob, { resizeWidth: 1920, resizeQuality: 'medium' })`.
 * - D5: DESVÍO DECLARADO — esta iteración NO delega a Web Worker.
 *   La compresión corre en main thread (400-800ms de freeze). Decisión B
 *   recomendada por el builder; ver apply-progress.md § Hallazgos H-D5.
 *   Refactor incremental futuro: extraer a Worker sin cambiar la API pública.
 */
@Injectable({ providedIn: 'root' })
export class ImageCompressionService {
  /** Ancho objetivo en píxeles (largest dim). 1920 cubre HD sin inflar RAM. */
  private static readonly MAX_WIDTH = 1920;

  /** Tamaño objetivo en bytes. Si se supera, segunda pasada a 0.65. */
  private static readonly TARGET_BYTES = 500 * 1024;

  /** Calidad primera pasada. */
  private static readonly QUALITY_HIGH = 0.82;

  /** Calidad segunda pasada (fallback). */
  private static readonly QUALITY_LOW = 0.65;

  /**
   * Comprime un Blob/Foto de cámara a WebP.
   *
   * @param source Blob de origen (JPEG/PNG/WebP/HEIC del sistema).
   * @returns Promise<Blob> con tipo `image/webp`. Tamaño <= TARGET_BYTES (o segunda pasada).
   */
  async compress(source: Blob): Promise<Blob> {
    // D4: decode + downscale directo en subsistema C++/GPU.
    const bitmap = await createImageBitmap(source, {
      resizeWidth: ImageCompressionService.MAX_WIDTH,
      resizeQuality: 'medium',
    });

    try {
      const canvas = new OffscreenCanvas(bitmap.width, bitmap.height);
      const ctx = canvas.getContext('2d');
      if (!ctx) {
        throw new Error('OffscreenCanvas 2D context no disponible.');
      }
      ctx.drawImage(bitmap, 0, 0);

      // D3: primera pasada 0.82.
      let blob = await canvas.convertToBlob({
        type: 'image/webp',
        quality: ImageCompressionService.QUALITY_HIGH,
      });

      // Segunda pasada si > 500KB.
      if (blob.size > ImageCompressionService.TARGET_BYTES) {
        blob = await canvas.convertToBlob({
          type: 'image/webp',
          quality: ImageCompressionService.QUALITY_LOW,
        });
      }

      return blob;
    } finally {
      // D5: cierre explícito del bitmap para liberar memoria GPU.
      bitmap.close();
    }
  }

  /**
   * Genera URL efímera para vista previa local. Compatible con `<img [src]="url">`.
   *
   * D1: NUNCA usar `readAsDataURL` ni concatenar Base64.
   * La URL debe revocarse con {@link revokePreviewUrl} cuando la vista previa ya no se use.
   */
  createPreviewUrl(blob: Blob): string {
    return URL.createObjectURL(blob);
  }

  /**
   * Libera la URL efímera creada por {@link createPreviewUrl}.
   * D1 + D5: invocación inmediata tras proyectar la imagen.
   */
  revokePreviewUrl(url: string): void {
    if (url) {
      URL.revokeObjectURL(url);
    }
  }
}
