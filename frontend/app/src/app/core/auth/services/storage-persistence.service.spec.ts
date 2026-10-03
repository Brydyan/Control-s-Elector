import { TestBed } from '@angular/core/testing';
import { StoragePersistenceService } from './storage-persistence.service';

/**
 * Tests del blindaje de persistencia (T-FE-09, B.2, V.3).
 */
describe('StoragePersistenceService', () => {
  let service: StoragePersistenceService;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [StoragePersistenceService],
    });
    service = TestBed.inject(StoragePersistenceService);
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should call navigator.storage.persist() when API exists (V.3)', async () => {
    const persistSpy = vi.fn().mockResolvedValue(true);
    const persistedSpy = vi.fn().mockResolvedValue(true);
    Object.defineProperty(navigator, 'storage', {
      value: { persist: persistSpy, persisted: persistedSpy },
      configurable: true,
      writable: true,
    });

    const granted = await service.requestPersistence();
    expect(persistSpy).toHaveBeenCalled();
    expect(granted).toBe(true);
  });

  it('should return false gracefully when persist() rejects', async () => {
    Object.defineProperty(navigator, 'storage', {
      value: {
        persist: () => Promise.reject(new Error('User denied')),
        persisted: () => Promise.resolve(false),
      },
      configurable: true,
      writable: true,
    });
    const granted = await service.requestPersistence();
    expect(granted).toBe(false);
  });

  it('should return false when navigator.storage is unavailable', async () => {
    Object.defineProperty(navigator, 'storage', {
      value: undefined,
      configurable: true,
      writable: true,
    });
    expect(await service.requestPersistence()).toBe(false);
    expect(await service.isPersisted()).toBe(false);
  });

  it('should return current persisted() state', async () => {
    Object.defineProperty(navigator, 'storage', {
      value: {
        persist: () => Promise.resolve(false),
        persisted: () => Promise.resolve(true),
      },
      configurable: true,
      writable: true,
    });
    expect(await service.isPersisted()).toBe(true);
  });
});