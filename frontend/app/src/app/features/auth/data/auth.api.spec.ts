import {
  HttpInterceptorFn,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { inject } from '@angular/core';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

import { ConfigService } from '../../../core/config/config.service';
import { AuthApi } from './auth.api';

const { envMock } = vi.hoisted(() => ({
  envMock: { production: true, apiUrl: '/api/v1', sentryDsn: '' },
}));

vi.mock('../../../../environments/environment', () => ({
  environment: envMock,
}));

/**
 * Regresion del 405 en produccion: el authInterceptor inyecta AuthService,
 * que construye AuthApi. Si eso ocurre antes de que ConfigService cargue
 * config.json y AuthApi congela su URL base, el login sale con la ruta
 * relativa `/api/v1` hacia Cloudflare Pages (estatico) y recibe 405.
 */
describe('AuthApi + ConfigService (arranque)', () => {
  let httpMock: HttpTestingController;
  const interceptadas: string[] = [];

  // Igual que el authInterceptor real: inyecta AuthApi en cada peticion.
  const interceptorQueInyectaAuthApi: HttpInterceptorFn = (req, next) => {
    inject(AuthApi);
    interceptadas.push(req.url);
    return next(req);
  };

  beforeEach(() => {
    interceptadas.length = 0;
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([interceptorQueInyectaAuthApi])),
        provideHttpClientTesting(),
      ],
    });
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('config.json no pasa por los interceptores', async () => {
    const config = TestBed.inject(ConfigService);
    const carga = config.load();
    httpMock
      .expectOne('/assets/config.json')
      .flush({ apiUrl: 'https://api.brydyan.me/api/v1' });
    await carga;

    expect(interceptadas).toEqual([]);
  });

  it('login usa la URL de config.json aunque AuthApi exista antes de cargarla', async () => {
    const config = TestBed.inject(ConfigService);
    const api = TestBed.inject(AuthApi); // construida ANTES de load()

    const carga = config.load();
    httpMock
      .expectOne('/assets/config.json')
      .flush({ apiUrl: 'https://api.brydyan.me/api/v1' });
    await carga;

    api.login({ email: 'a@b.c', password: 'x' }).subscribe();
    const req = httpMock.expectOne('https://api.brydyan.me/api/v1/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.withCredentials).toBe(true);
  });
});
