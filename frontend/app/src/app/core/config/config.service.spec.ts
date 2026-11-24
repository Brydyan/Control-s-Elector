import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { ConfigService } from './config.service';

const { envMock } = vi.hoisted(() => ({
  envMock: {
    production: false,
    apiUrl: '/api/v1',
    sentryDsn: '',
  },
}));

vi.mock('../../../environments/environment', () => ({
  environment: envMock,
}));

describe('ConfigService', () => {
  let service: ConfigService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    envMock.production = false;
    envMock.apiUrl = '/api/v1';
    envMock.sentryDsn = '';

    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ConfigService],
    });
    service = TestBed.inject(ConfigService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('deberia devolver la ruta relativa antes de cargar la configuracion', () => {
    expect(service.getApiUrlSafe()).toBe('/api/v1');
  });

  it('deberia lanzar si se pide la configuracion antes de cargarla', () => {
    expect(() => service.getConfig()).toThrow();
    expect(() => service.getApiUrl()).toThrow();
  });

  it('en desarrollo usa la URL del proxy y NO pide config.json', async () => {
    await service.load();

    // El config.json versionado apunta a produccion: en dev mandaria la app
    // contra el backend real. Sin peticion HTTP, y con la ruta relativa.
    expect(service.getApiUrl()).toBe('/api/v1');
    httpMock.expectNone(() => true);
  });

  it('en produccion lee el config.json en runtime', async () => {
    envMock.production = true;

    const carga = service.load();
    const req = httpMock.expectOne('/assets/config.json');
    req.flush({ apiUrl: 'https://api.brydyan.me/api/v1', sentryDsn: '' });
    await carga;

    expect(service.getApiUrl()).toBe('https://api.brydyan.me/api/v1');
  });

  it('en produccion cae a la URL de environment.prod si falla el config.json', async () => {
    envMock.production = true;
    envMock.apiUrl = 'https://api.brydyan.me/api/v1';

    const carga = service.load();
    const req = httpMock.expectOne('/assets/config.json');
    req.error(new ProgressEvent('error'));
    await carga;

    expect(service.getApiUrl()).toBe('https://api.brydyan.me/api/v1');
  });
});
