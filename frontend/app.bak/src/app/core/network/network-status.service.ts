import { Injectable, computed, signal } from '@angular/core';

export type NetworkStatus = 'ONLINE' | 'RECONNECTING' | 'OFFLINE';

@Injectable({
  providedIn: 'root',
})
export class NetworkStatusService {
  private readonly _isOnline = signal<boolean>(
    typeof navigator !== 'undefined' ? navigator.onLine : true,
  );
  private readonly _isReconnecting = signal<boolean>(false);

  readonly isOnline = this._isOnline.asReadonly();
  readonly isReconnecting = this._isReconnecting.asReadonly();

  readonly status = computed<NetworkStatus>(() => {
    if (!this._isOnline()) {
      return 'OFFLINE';
    }
    if (this._isReconnecting()) {
      return 'RECONNECTING';
    }
    return 'ONLINE';
  });

  readonly statusLabel = computed<string>(() => {
    switch (this.status()) {
      case 'ONLINE':
        return 'En línea · Conectado';
      case 'RECONNECTING':
        return 'Reconectando…';
      case 'OFFLINE':
        return 'Sin conexión · Modo local';
    }
  });

  constructor() {
    if (typeof window !== 'undefined') {
      window.addEventListener('online', () => {
        this._isReconnecting.set(true);
        setTimeout(() => {
          this._isOnline.set(true);
          this._isReconnecting.set(false);
        }, 1500);
      });

      window.addEventListener('offline', () => {
        this._isOnline.set(false);
        this._isReconnecting.set(false);
      });
    }
  }

  setReconnecting(reconnecting: boolean): void {
    this._isReconnecting.set(reconnecting);
  }
}
