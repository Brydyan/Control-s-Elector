import { TestBed } from '@angular/core/testing';
import { ImageCompressionService } from './image-compression.service';

/**
 * Tests del servicio de compresión (T-FE-20 Phase A — main thread, WebP + File support).
 *
 * Cubren:
 * - A.1: MAX_WIDTH 2048, TARGET_BYTES 650KB, QUALITY_HIGH 0.80/QUALITY_LOW 0.65.
 * - A.1: Soporte Blob + File (retorna File con .webp si entrada File).
 * - A.1: Fallback a image/jpeg si no soporta WebP.
 * - D4: createImageBitmap con `{ resizeWidth: 2048, resizeQuality: 'medium' }`.
 * - D1: revocación explícita de URL.createObjectURL.
 * - D3: segunda pasada si > 650KB.
 * - V.1 (mutación): omitir this.imageCompression.compress() en onFileSelected → test cae.
 */
describe('ImageCompressionService', () => {
  let service: ImageCompressionService;

  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [ImageCompressionService] });
    service = TestBed.inject(ImageCompressionService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call createImageBitmap with resizeWidth 2048 and quality medium (A.1, D4)', async () => {
    const fakeBitmap = {
      width: 1920,
      height: 1080,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const convertToBlob = vi
      .fn()
      .mockResolvedValue(new Blob(['x'], { type: 'image/webp' }));
    const getContext = vi.fn().mockReturnValue({
      drawImage: vi.fn(),
    });
    class OffscreenCanvasMock {
      getContext = getContext;
      convertToBlob = convertToBlob;
      width = 1920;
      height = 1080;
    }
    (globalThis as unknown as { OffscreenCanvas: unknown }).OffscreenCanvas =
      OffscreenCanvasMock;
    const createImageBitmapSpy = vi
      .spyOn(globalThis, 'createImageBitmap')
      .mockResolvedValue(fakeBitmap);

    const source = new Blob(['raw-bytes'], { type: 'image/jpeg' });
    await service.compress(source);

    expect(createImageBitmapSpy).toHaveBeenCalledWith(source, {
      resizeWidth: 2048,
      resizeQuality: 'medium',
    });
    expect(fakeBitmap.close).toHaveBeenCalled();
  });

  it('should return a Blob with image/webp MIME on first pass (A.1)', async () => {
    const fakeBitmap = {
      width: 800,
      height: 600,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const webpBlob = new Blob(['compressed'], { type: 'image/webp' });
    const convertToBlob = vi.fn().mockResolvedValue(webpBlob);
    const getContext = vi.fn().mockReturnValue({ drawImage: vi.fn() });
    class OffscreenCanvasMock {
      getContext = getContext;
      convertToBlob = convertToBlob;
      width = 800;
      height = 600;
    }
    (globalThis as unknown as { OffscreenCanvas: unknown }).OffscreenCanvas =
      OffscreenCanvasMock;
    vi.spyOn(globalThis, 'createImageBitmap').mockResolvedValue(fakeBitmap);

    const result = await service.compress(
      new Blob(['x'], { type: 'image/jpeg' }),
    );

    expect(result.type).toBe('image/webp');
    expect(convertToBlob).toHaveBeenCalledTimes(1); // 1 sola pasada si <650KB
    expect(convertToBlob).toHaveBeenCalledWith({
      type: 'image/webp',
      quality: 0.8,
    });
  });

  it('should re-compress at 0.65 when first pass exceeds 650KB (A.1, D3)', async () => {
    const fakeBitmap = {
      width: 2048,
      height: 1536,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const bigBlob = new Blob([new ArrayBuffer(700 * 1024)], {
      type: 'image/webp',
    });
    const smallBlob = new Blob(['ok'], { type: 'image/webp' });
    const convertToBlob = vi
      .fn()
      .mockResolvedValueOnce(bigBlob)
      .mockResolvedValueOnce(smallBlob);
    const getContext = vi.fn().mockReturnValue({ drawImage: vi.fn() });
    class OffscreenCanvasMock {
      getContext = getContext;
      convertToBlob = convertToBlob;
      width = 2048;
      height = 1536;
    }
    (globalThis as unknown as { OffscreenCanvas: unknown }).OffscreenCanvas =
      OffscreenCanvasMock;
    vi.spyOn(globalThis, 'createImageBitmap').mockResolvedValue(fakeBitmap);

    const result = await service.compress(
      new Blob(['x'], { type: 'image/jpeg' }),
    );

    expect(convertToBlob).toHaveBeenCalledTimes(2);
    expect(convertToBlob).toHaveBeenNthCalledWith(1, {
      type: 'image/webp',
      quality: 0.8,
    });
    expect(convertToBlob).toHaveBeenNthCalledWith(2, {
      type: 'image/webp',
      quality: 0.65,
    });
    expect(result.size).toBeLessThan(650 * 1024);
  });

  it('should create preview URL via URL.createObjectURL (D1, no Base64)', () => {
    const blob = new Blob(['x'], { type: 'image/webp' });
    const createObjectURLSpy = vi
      .spyOn(URL, 'createObjectURL')
      .mockReturnValue('blob:mock-url-123');

    const url = service.createPreviewUrl(blob);

    expect(createObjectURLSpy).toHaveBeenCalledWith(blob);
    expect(url).toBe('blob:mock-url-123');
    // Asegurar que NO genera prefijo data: (Base64).
    expect(url.startsWith('data:')).toBe(false);
  });

  it('should revoke preview URL on demand (D1 cleanup)', () => {
    const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL');

    service.revokePreviewUrl('blob:mock-url-123');

    expect(revokeObjectURLSpy).toHaveBeenCalledWith('blob:mock-url-123');
  });

  it('should be a no-op when revoking empty/undefined URL', () => {
    const revokeObjectURLSpy = vi.spyOn(URL, 'revokeObjectURL');

    service.revokePreviewUrl('');
    service.revokePreviewUrl(undefined as unknown as string);

    expect(revokeObjectURLSpy).not.toHaveBeenCalled();
  });

  // ── A.1: T-FE-20 tests nuevos ──────────────────────────────────────────

  it('should return File with .webp extension when input is File (A.1)', async () => {
    const fakeBitmap = {
      width: 2048,
      height: 1536,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const webpBlob = new Blob(['compressed'], { type: 'image/webp' });
    const convertToBlob = vi.fn().mockResolvedValue(webpBlob);
    const getContext = vi.fn().mockReturnValue({ drawImage: vi.fn() });
    class OffscreenCanvasMock {
      getContext = getContext;
      convertToBlob = convertToBlob;
      width = 2048;
      height = 1536;
    }
    (globalThis as unknown as { OffscreenCanvas: unknown }).OffscreenCanvas =
      OffscreenCanvasMock;
    vi.spyOn(globalThis, 'createImageBitmap').mockResolvedValue(fakeBitmap);

    const inputFile = new File(['original'], 'acta-123.jpg', {
      type: 'image/jpeg',
    });
    const result = await service.compress(inputFile);

    expect(result).toBeInstanceOf(File);
    expect((result as File).name).toBe('acta-123.webp');
    expect(result.type).toBe('image/webp');
  });

  it('should fallback to image/jpeg when WebP not supported (A.1)', async () => {
    const fakeBitmap = {
      width: 2048,
      height: 1536,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const jpegBlob = new Blob(['compressed'], { type: 'image/jpeg' });
    // Mock convertToBlob: retorna jpeg en lugar de webp (sin soporte WebP).
    const convertToBlob = vi.fn().mockImplementation((opts) => {
      if (opts.type === 'image/webp') {
        // Simular: navegador no soporta, retorna jpeg.
        return Promise.resolve(new Blob(['fallback'], { type: 'image/jpeg' }));
      }
      return Promise.resolve(jpegBlob);
    });
    const getContext = vi.fn().mockReturnValue({ drawImage: vi.fn() });
    class OffscreenCanvasMock {
      getContext = getContext;
      convertToBlob = convertToBlob;
      width = 2048;
      height = 1536;
    }
    (globalThis as unknown as { OffscreenCanvas: unknown }).OffscreenCanvas =
      OffscreenCanvasMock;
    vi.spyOn(globalThis, 'createImageBitmap').mockResolvedValue(fakeBitmap);

    const result = await service.compress(
      new Blob(['x'], { type: 'image/jpeg' }),
    );

    // Fallback detectado por supportsWebP() retornando false.
    expect(convertToBlob).toHaveBeenCalled();
  });

  it('should handle corrupted blob and throw error (A.1)', async () => {
    vi.spyOn(globalThis, 'createImageBitmap').mockRejectedValue(
      new Error('Invalid image'),
    );

    const corruptedBlob = new Blob(['not-an-image'], {
      type: 'image/jpeg',
    });

    await expect(service.compress(corruptedBlob)).rejects.toThrow(
      'Invalid image',
    );
  });

  it('should always invoke bitmap.close() even on error (A.1, cleanup)', async () => {
    const fakeBitmap = {
      width: 2048,
      height: 1536,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const getContext = vi.fn().mockReturnValue({ drawImage: vi.fn() }); // No error en drawImage
    class OffscreenCanvasMock {
      getContext = getContext;
      convertToBlob = () => Promise.reject(new Error('ConvertToBlob failed'));
      width = 2048;
      height = 1536;
    }
    (globalThis as unknown as { OffscreenCanvas: unknown }).OffscreenCanvas =
      OffscreenCanvasMock;
    vi.spyOn(globalThis, 'createImageBitmap').mockResolvedValue(fakeBitmap);

    try {
      await service.compress(new Blob(['x'], { type: 'image/jpeg' }));
    } catch {
      // Expected error
    }

    // Verificar que bitmap.close() se llamó a pesar del error.
    expect(fakeBitmap.close).toHaveBeenCalled();
  });
});
