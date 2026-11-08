import { TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { firstValueFrom, of, throwError } from 'rxjs';

import { AuthStore } from '../../auth/services/auth.store';
import { CurrentUser } from '../../auth/domain/models/user.model';
import { LegalService } from './legal.service';
import { TermsAcceptanceService } from './terms-acceptance.service';

/**
 * Tests de la regla que decide si el usuario tiene que aceptar los terminos (T-LEGAL-01.8).
 *
 * <p>La regla tiene tres partes y las tres tienen consecuencias distintas:
 *
 * <ul>
 *   <li>Si el usuario ya acepto la version vigente, no se le molesta.
 *   <li>Si la version cambio, se le vuelve a pedir aunque haya aceptado antes.
 *   <li>Si el backend no responde, no se bloquea a nadie: perder el acceso al sistema electoral
 *       entero porque el endpoint de la version fallo seria peor que dejar pasar a alguien que
 *       aun no ha aceptado.
 * </ul>
 */
describe('TermsAcceptanceService', () => {
  const usuario: CurrentUser = {
    id: 'u-1',
    email: 'obs@test.local',
    role: 'OBSERVER',
    permissions: [],
    acceptedTermsVersion: null,
  };

  let store: AuthStore;
  let legal: {
    versionVigente: ReturnType<typeof vi.fn>;
    aceptarTerminos: ReturnType<typeof vi.fn>;
  };
  let legalReal: LegalService;
  let service: TermsAcceptanceService;

  beforeEach(() => {
    legal = { versionVigente: vi.fn(), aceptarTerminos: vi.fn() };
    legalReal = legal as unknown as LegalService;

    TestBed.configureTestingModule({
      providers: [AuthStore, { provide: LegalService, useValue: legalReal }],
    });

    store = TestBed.inject(AuthStore);
    store.clear();
    sessionStorage.clear();

    service = TestBed.inject(TermsAcceptanceService);
  });

  it('un usuario que nunca acepto tiene pendiente la aceptacion', async () => {
    store.setSession('token', usuario);
    legal.versionVigente.mockReturnValue(of('1.0'));

    const pendiente = await firstValueFrom(service.requiereAceptacion());

    expect(pendiente).toBe(true);
  });

  it('un usuario que ya acepto la version vigente no tiene nada pendiente', async () => {
    store.setSession('token', { ...usuario, acceptedTermsVersion: '1.0' });
    legal.versionVigente.mockReturnValue(of('1.0'));

    const pendiente = await firstValueFrom(service.requiereAceptacion());

    expect(pendiente).toBe(false);
  });

  it('una version nueva vuelve a pedir la aceptacion', async () => {
    store.setSession('token', { ...usuario, acceptedTermsVersion: '1.0' });
    legal.versionVigente.mockReturnValue(of('1.1'));

    const pendiente = await firstValueFrom(service.requiereAceptacion());

    expect(pendiente).toBe(true);
  });

  it('la aceptacion queda en sessionStorage y no vuelve a pedirla en la sesion', async () => {
    store.setSession('token', usuario);
    legal.versionVigente.mockReturnValue(of('1.0'));
    service.marcarAceptadaEnSesion('1.0');

    // El usuario en memoria todavia no lo sabe (simula un store reconstruido).
    const pendiente = await firstValueFrom(service.requiereAceptacion());

    expect(pendiente).toBe(false);
    expect(sessionStorage.getItem(TermsAcceptanceService.CLAVE)).toBe('1.0');
  });

  it('si el backend no responde, no se bloquea a nadie', async () => {
    store.setSession('token', usuario);
    legal.versionVigente.mockReturnValue(
      throwError(() => new Error('sin red')),
    );

    const pendiente = await firstValueFrom(service.requiereAceptacion());

    expect(pendiente).toBe(false);
  });

  it('aceptar guarda la version en el store y en la sesion', async () => {
    store.setSession('token', usuario);
    legal.aceptarTerminos.mockReturnValue(of('1.0'));

    await firstValueFrom(service.aceptar());

    expect(legal.aceptarTerminos).toHaveBeenCalledOnce();
    expect(store.user()?.acceptedTermsVersion).toBe('1.0');
    expect(sessionStorage.getItem(TermsAcceptanceService.CLAVE)).toBe('1.0');
  });

  it('la version vigente se pide una sola vez mientras no cambie', async () => {
    legal.versionVigente.mockReturnValue(of('1.0'));

    await firstValueFrom(service.versionVigente());
    await firstValueFrom(service.versionVigente());

    expect(legal.versionVigente).toHaveBeenCalledOnce();
  });
});
