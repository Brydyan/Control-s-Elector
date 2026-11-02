import { TestBed } from '@angular/core/testing';
import { PinAuthService } from './pin-auth.service';

/**
 * Tests del servicio de PIN (T-FE-09, A.1).
 *
 * Cubren: G3 (PIN local 4 dígitos), V.1 (PIN incorrecto rechazado).
 * Storage: localStorage real (jsdom lo provee).
 */
describe('PinAuthService', () => {
  let service: PinAuthService;

  beforeEach(() => {
    localStorage.clear();
    TestBed.configureTestingModule({ providers: [PinAuthService] });
    service = TestBed.inject(PinAuthService);
  });

  afterEach(() => {
    localStorage.clear();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should report no PIN configured initially', () => {
    expect(service.tienePinConfigurado()).toBe(false);
  });

  it('should configure a 4-digit PIN and report it as configured', async () => {
    await service.configurarPin('1234');
    expect(service.tienePinConfigurado()).toBe(true);
  });

  it('should validate the correct PIN (G3)', async () => {
    await service.configurarPin('1234');
    expect(await service.validarPin('1234')).toBe(true);
  });

  it('should reject an incorrect PIN (V.1)', async () => {
    await service.configurarPin('1234');
    expect(await service.validarPin('5678')).toBe(false);
  });

  it('should reject PIN validation when no PIN is configured', async () => {
    expect(service.tienePinConfigurado()).toBe(false);
    expect(await service.validarPin('1234')).toBe(false);
  });

  it('should reject malformed PIN on configure (not 4 digits)', async () => {
    await expect(service.configurarPin('12')).rejects.toThrow();
    await expect(service.configurarPin('abcd')).rejects.toThrow();
    await expect(service.configurarPin('12345')).rejects.toThrow();
  });

  it('should store salt + hash, never plain PIN (defense in depth)', async () => {
    await service.configurarPin('1234');
    const raw = localStorage.getItem('electoral_pin_v1');
    expect(raw).toBeTruthy();
    expect(raw).not.toContain('1234');
    const parsed = JSON.parse(raw!);
    expect(parsed.salt).toMatch(/^[0-9a-f]{32}$/);
    expect(parsed.hash).toMatch(/^[0-9a-f]{64}$/);
  });

  it('should clear PIN on limpiarPin()', async () => {
    await service.configurarPin('1234');
    expect(service.tienePinConfigurado()).toBe(true);
    service.limpiarPin();
    expect(service.tienePinConfigurado()).toBe(false);
  });

  it('should use different salts for different PINs (no determinism)', async () => {
    await service.configurarPin('1234');
    const first = JSON.parse(localStorage.getItem('electoral_pin_v1')!);
    service.limpiarPin();
    await service.configurarPin('1234');
    const second = JSON.parse(localStorage.getItem('electoral_pin_v1')!);
    expect(first.salt).not.toBe(second.salt);
    expect(first.hash).not.toBe(second.hash);
  });
});
