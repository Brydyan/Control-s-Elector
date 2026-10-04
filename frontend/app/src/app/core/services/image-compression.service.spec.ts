import { TestBed } from '@angular/core/testing';
import { ImageCompressionService } from './image-compression.service';

/**
 * Tests del servicio de compresión (T-FE-08, Opción B — main thread).
 *
 * Cubren:
 * - G1: tipo MIME exacto `image/webp`.
 * - D4: createImageBitmap con `{ resizeWidth: 1920, resizeQuality: 'medium' }`.
 * - D1: revocación explícita de URL.createObjectURL.
 * - D3: segunda pasada si > 500KB.
 * - V.3 (mutación): sustitución de createImageBitmap por `new Image()` rompe el test.
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

  it('should call createImageBitmap with resizeWidth 1920 and quality medium (D4)', async () => {
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
      resizeWidth: 1920,
      resizeQuality: 'medium',
    });
    expect(fakeBitmap.close).toHaveBeenCalled();
  });

  it('should return a Blob with image/webp MIME on first pass (G1)', async () => {
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
    expect(convertToBlob).toHaveBeenCalledTimes(1); // 1 sola pasada si <500KB
    expect(convertToBlob).toHaveBeenCalledWith({
      type: 'image/webp',
      quality: 0.82,
    });
  });

  it('should re-compress at 0.65 when first pass exceeds 500KB (D3)', async () => {
    const fakeBitmap = {
      width: 1920,
      height: 1080,
      close: vi.fn(),
    } as unknown as ImageBitmap;
    const bigBlob = new Blob([new ArrayBuffer(600 * 1024)], {
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
      width = 1920;
      height = 1080;
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
      quality: 0.82,
    });
    expect(convertToBlob).toHaveBeenNthCalledWith(2, {
      type: 'image/webp',
      quality: 0.65,
    });
    expect(result.size).toBeLessThan(500 * 1024);
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
});
