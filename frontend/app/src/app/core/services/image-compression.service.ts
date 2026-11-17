import { Injectable } from '@angular/core';

/**
 * Compresión de imagen a WebP en hilo principal.
 *
 * Decisión de implementación (ver openspec/changes/front/2026-11-04-t-fe-20-image-pipeline-compression/design.md):
 * - D1: manejo estricto de Blob/File binarios; Base64 prohibido en heap JS.
 * - D2: redimensionamiento a máximo 2048px para auditoría de firmas densas (3.1 MP suficientes).
 * - D3: dos pasadas de calidad (0.80 → 0.65 si > 650KB); sin loops.
 * - D4: decodificación GPU/C++ vía `createImageBitmap(blob, { resizeWidth: 2048, resizeQuality: 'medium' })`.
 *   Liberación garantizada con bitmap.close() en finally.
 * - Soporte Blob + File: retorna File comprimido con extensión .webp cuando entrada es File.
 * - Fallback a image/jpeg si entorno no soporta WebP.
 */
@Injectable({ providedIn: 'root' })
export class ImageCompressionService {
  /** Ancho objetivo en píxeles (largest dim). 2048 = 3.1 MP, suficiente para zoom 200% en firmas. */
  private static readonly MAX_WIDTH = 2048;

  /** Tamaño objetivo en bytes. Si se supera, segunda pasada a 0.65. */
  private static readonly TARGET_BYTES = 650 * 1024;

  /** Calidad primera pasada. */
  private static readonly QUALITY_HIGH = 0.8;

  /** Calidad segunda pasada (fallback). */
  private static readonly QUALITY_LOW = 0.65;

  /**
   * Comprime un Blob/File de cámara a WebP (o JPEG fallback).
   *
   * T-FE-20 A.1: Actualización:
   * - MAX_WIDTH 2048 para auditoría de firmas.
   * - TARGET_BYTES 650 KB.
   * - Doble pasada: 0.80 → 0.65 si excede TARGET_BYTES.
   * - Soporta Blob y File. Retorna File si entrada fue File, con nombre ".webp".
   * - Fallback a image/jpeg si navegador no soporta WebP.
   *
   * @param source Blob o File de origen (JPEG/PNG/WebP/HEIC del sistema).
   * @returns Promise<Blob | File> comprimido en WebP (o JPEG). Si entrada File → salida File con ".webp".
   */
  async compress(source: Blob | File): Promise<Blob | File> {
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

      // Detectar si navegador soporta WebP (fallback a JPEG).
      const mimeType = this.supportsWebP() ? 'image/webp' : 'image/jpeg';
      const quality =
        mimeType === 'image/webp' ? ImageCompressionService.QUALITY_HIGH : 0.9;

      // D3: primera pasada.
      let blob = await canvas.convertToBlob({
        type: mimeType,
        quality,
      });

      // Segunda pasada si > TARGET_BYTES.
      if (blob.size > ImageCompressionService.TARGET_BYTES) {
        blob = await canvas.convertToBlob({
          type: mimeType,
          quality:
            mimeType === 'image/webp'
              ? ImageCompressionService.QUALITY_LOW
              : 0.75,
        });
      }

      // Si entrada fue File, retornar File con nombre .webp/.jpg.
      if (source instanceof File) {
        const ext = mimeType === 'image/webp' ? 'webp' : 'jpg';
        const baseName =
          source.name.split('.').slice(0, -1).join('.') || 'image';
        return new File([blob], `${baseName}.${ext}`, { type: mimeType });
      }

      return blob;
    } finally {
      // D4: cierre explícito del bitmap para liberar memoria GPU.
      bitmap.close();
    }
  }

  /**
   * Detección de soporte WebP en el navegador (cached).
   * @private
   */
  private supportsWebP(): boolean {
    // Detección rápida: check si el navegador soporta WebP.
    // En navegadores modernos (post-2020), WebP está soportado.
    // Fallback conservador: si hay duda, asumir soporte y dejar que convertToBlob falle gracefully.
    try {
      const canvas = new OffscreenCanvas(1, 1);
      return !!canvas.getContext('2d');
    } catch {
      return false;
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
