import { Injectable } from '@angular/core';

/**
 * Autenticación local por PIN de 4 dígitos (T-FE-09, A.1, design D1).
 *
 * Permite al veedor desbloquear la app en campo cuando el JWT expira y no
 * hay conexión. El PIN nunca sale del dispositivo; se almacena como
 * `salt + SHA-256(pin+salt)` en `localStorage`.
 *
 * Decisión de storage: `localStorage` (no `IndexedDB`) porque la verificación
 * es sincrónica y se ejecuta en `authGuard` antes del primer render offline.
 */
const STORAGE_KEY = 'electoral_pin_v1';
const PBKDF_PREFIX = 'pbkdf1:sha256';

interface StoredPin {
  salt: string; // hex
  hash: string; // hex
}

@Injectable({ providedIn: 'root' })
export class PinAuthService {
  /**
   * Genera salt aleatorio (16 bytes).
   */
  private randomSalt(): string {
    const bytes = new Uint8Array(16);
    crypto.getRandomValues(bytes);
    return this.toHex(bytes);
  }

  /**
   * Calcula SHA-256 de `pin + salt`, devuelve hex.
   */
  private async sha256Hex(input: string): Promise<string> {
    const data = new TextEncoder().encode(input);
    const digest = await crypto.subtle.digest('SHA-256', data);
    return this.toHex(new Uint8Array(digest));
  }

  private toHex(bytes: Uint8Array): string {
    return Array.from(bytes)
      .map((b) => b.toString(16).padStart(2, '0'))
      .join('');
  }

  private read(): StoredPin | null {
    if (typeof localStorage === 'undefined') return null;
    const raw = localStorage.getItem(STORAGE_KEY);
    if (!raw) return null;
    try {
      const parsed = JSON.parse(raw) as StoredPin;
      if (parsed.salt && parsed.hash) return parsed;
      return null;
    } catch {
      return null;
    }
  }

  private write(stored: StoredPin): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.setItem(STORAGE_KEY, JSON.stringify(stored));
  }

  /**
   * Verifica si el dispositivo tiene un PIN configurado.
   */
  tienePinConfigurado(): boolean {
    return this.read() !== null;
  }

  /**
   * Configura (o reemplaza) el PIN. Almacena salt + hash.
   */
  async configurarPin(pin: string): Promise<void> {
    this.assertPinFormat(pin);
    const salt = this.randomSalt();
    const hash = await this.sha256Hex(`${PBKDF_PREFIX}:${salt}:${pin}`);
    this.write({ salt, hash });
  }

  /**
   * Valida el PIN contra el hash almacenado.
   * Retorna `false` si no hay PIN configurado.
   */
  async validarPin(pin: string): Promise<boolean> {
    const stored = this.read();
    if (!stored) return false;
    const candidate = await this.sha256Hex(
      `${PBKDF_PREFIX}:${stored.salt}:${pin}`,
    );
    return candidate === stored.hash;
  }

  /**
   * Limpia el PIN configurado. Útil para tests y para logout con borrado total.
   */
  limpiarPin(): void {
    if (typeof localStorage === 'undefined') return;
    localStorage.removeItem(STORAGE_KEY);
  }

  private assertPinFormat(pin: string): void {
    if (typeof pin !== 'string' || pin.length !== 4 || !/^\d{4}$/.test(pin)) {
      throw new Error('PIN debe ser exactamente 4 dígitos numéricos.');
    }
  }
}