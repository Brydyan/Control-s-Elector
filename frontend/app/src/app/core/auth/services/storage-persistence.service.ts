import { Injectable } from '@angular/core';

/**
 * Blindaje de persistencia de almacenamiento (T-FE-09, B.2, design D4).
 *
 * Solicita al navegador que marque el origen como "persistente" para que
 * IndexedDB y CacheStorage no sean evictados automáticamente por presión
 * de cuota en Android / Chromium.
 *
 * Decisión: se invoca tras login exitoso. Es idempotente — el navegador
 * decide según heurísticas si concede persistencia.
 */
@Injectable({ providedIn: 'root' })
export class StoragePersistenceService {
  /**
   * Solicita persistencia al navegador. No-op si la API no existe.
   * No lanza si el navegador rechaza la solicitud (best-effort).
   */
  async requestPersistence(): Promise<boolean> {
    if (
      typeof navigator === 'undefined' ||
      !('storage' in navigator) ||
      typeof navigator.storage?.persist !== 'function'
    ) {
      return false;
    }
    try {
      return await navigator.storage.persist();
    } catch {
      return false;
    }
  }

  /**
   * Consulta si el origen ya es persistente.
   */
  async isPersisted(): Promise<boolean> {
    if (
      typeof navigator === 'undefined' ||
      !('storage' in navigator) ||
      typeof navigator.storage?.persisted !== 'function'
    ) {
      return false;
    }
    try {
      return await navigator.storage.persisted();
    } catch {
      return false;
    }
  }
}