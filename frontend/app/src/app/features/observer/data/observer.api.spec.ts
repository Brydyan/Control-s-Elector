import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { firstValueFrom } from 'rxjs';
import { environment } from '../../../../environments/environment';
import { ObserverApi } from './observer.api';

describe('ObserverApi', () => {
  let service: ObserverApi;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ObserverApi],
    });
    service = TestBed.inject(ObserverApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should get mis jrvs', () => {
    const mockJrvs = [
      {
        jrvId: 1,
        numero: '101',
        parroquia: 'Colonche',
        cargosPendientes: 2,
        cargosRegistrados: 1,
      },
    ];

    service.getMisJrvs().subscribe((data) => {
      expect(data).toEqual(mockJrvs);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/observer/jrvs`);
    expect(req.request.method).toBe('GET');
    req.flush(mockJrvs);
  });

  it('should get cargos de jrv', () => {
    const mockCargos = [
      {
        cargoId: 1,
        nombre: 'Prefecto',
        nivel: 'PROVINCIAL',
        registrado: false,
      },
    ];

    service.getCargosDeJrv(10).subscribe((data) => {
      expect(data).toEqual(mockCargos);
    });

    const req = httpMock.expectOne(
      `${environment.apiUrl}/observer/jrv/10/cargos`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockCargos);
  });

  it('should post direct acta', () => {
    const payload = {
      jrvId: 1,
      cargoId: 2,
      totalSufragantes: 100,
      votosBlancos: 5,
      votosNulos: 5,
      detalles: [{ candidatoId: 1, votos: 90 }],
    };

    service.enviarActaDirecta(payload).subscribe((res) => {
      expect(res.id).toBe(123);
    });

    const req = httpMock.expectOne(`${environment.apiUrl}/observer/actas`);
    expect(req.request.method).toBe('POST');
    req.flush({ id: 123, ...payload, estado: 'ENVIADA' });
  });

  it('should forward the idempotency header on the acta POST', () => {
    const payload = {
      jrvId: 1,
      cargoId: 2,
      totalSufragantes: 100,
      votosBlancos: 5,
      votosNulos: 5,
      detalles: [{ candidatoId: 1, votos: 90 }],
    };

    service
      .enviarActaDirecta(payload, {
        headers: { 'Idempotency-Key': 'acta-1-2' },
      })
      .subscribe();

    const req = httpMock.expectOne(`${environment.apiUrl}/observer/actas`);
    expect(req.request.method).toBe('POST');
    expect(req.request.headers.get('Idempotency-Key')).toBe('acta-1-2');
    req.flush({ id: 123, ...payload, estado: 'ENVIADA' });
  });

  it('should post image with multipart key "imagen" (V.2 contract)', () => {
    const file = new File(['x'], 'acta.webp', { type: 'image/webp' });

    service.subirImagenActa(7, file).subscribe();

    const req = httpMock.expectOne(
      `${environment.apiUrl}/observer/actas/7/imagen`,
    );
    expect(req.request.method).toBe('POST');
    expect(req.request.body instanceof FormData).toBe(true);
    const formData = req.request.body as FormData;
    const keys = Array.from(formData.keys());
    expect(keys).toContain('imagen');
    expect(keys).not.toContain('file');
    req.flush(null);
  });

  it('should corregir acta en disputa', () => {
    const payload = {
      jrvId: 1,
      cargoId: 2,
      totalSufragantes: 100,
      votosBlancos: 5,
      votosNulos: 5,
      detalles: [{ candidatoId: 10, votos: 90 }],
    };

    service.corregirActa(123, payload).subscribe((res) => {
      expect(res.id).toBe(123);
      expect(res.estado).toBe('CORREGIDA');
    });

    const req = httpMock.expectOne(
      `${environment.apiUrl}/observer/actas/123/corregir`,
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({ id: 123, estado: 'CORREGIDA' });
  });

  it('should cache partidos and reuse on second call', async () => {
    const mockPartidos = [
      { id: 1, nombre: 'PAIS', siglas: 'P', numeroLista: 1 },
    ];
    const mockCandidatos1 = [
      {
        id: 1,
        nombre: 'Candidato 1',
        orden: 1,
        cargoId: 1,
        partidoId: 1,
      },
    ];
    const mockCandidatos2 = [
      {
        id: 2,
        nombre: 'Candidato 2',
        orden: 1,
        cargoId: 2,
        partidoId: 1,
      },
    ];

    const primera = firstValueFrom(service.getCandidatosPorCargo(1));
    httpMock
      .expectOne((req) => req.url.includes('/candidatos?cargoId=1'))
      .flush(mockCandidatos1);
    httpMock
      .expectOne((req) => req.url.includes('/partidos'))
      .flush(mockPartidos);
    await primera;

    // Segundo cargo: los candidatos se piden otra vez, los partidos no.
    const segunda = firstValueFrom(service.getCandidatosPorCargo(2));
    httpMock
      .expectOne((req) => req.url.includes('/candidatos?cargoId=2'))
      .flush(mockCandidatos2);
    await segunda;

    expect(httpMock.match((req) => req.url.includes('/partidos'))).toHaveLength(
      0,
    );
  });

  it('invalidateCache should drop the partidos cache', async () => {
    const mockPartidos = [
      { id: 1, nombre: 'PAIS', siglas: 'P', numeroLista: 1 },
    ];
    const mockCandidatos = [
      { id: 1, nombre: 'Candidato 1', orden: 1, cargoId: 1, partidoId: 1 },
    ];

    const primera = firstValueFrom(service.getCandidatosPorCargo(1));
    httpMock
      .expectOne((req) => req.url.includes('/candidatos?cargoId=1'))
      .flush(mockCandidatos);
    httpMock
      .expectOne((req) => req.url.includes('/partidos'))
      .flush(mockPartidos);
    await primera;

    service.invalidateCache();

    const segunda = firstValueFrom(service.getCandidatosPorCargo(2));
    httpMock
      .expectOne((req) => req.url.includes('/candidatos?cargoId=2'))
      .flush(mockCandidatos);
    httpMock
      .expectOne((req) => req.url.includes('/partidos'))
      .flush(mockPartidos);
    await segunda;

    expect(httpMock.match((req) => req.url.includes('/partidos'))).toHaveLength(
      0,
    );
  });

  it('getCatalogoDeJrv should fetch the full JRV catalog in one request and enrich candidatos', async () => {
    const mockResponse = {
      cargos: [
        {
          id: 10,
          nombre: 'Presidente',
          candidatos: [
            {
              id: 1,
              nombre: 'Candidato A',
              orden: 1,
              cargoId: 10,
              partidoId: 5,
            },
          ],
          partidos: [
            { id: 5, nombre: 'Partido Uno', siglas: 'P1', numeroLista: 7 },
          ],
        },
        {
          id: 20,
          nombre: 'Asambleísta',
          candidatos: [],
          partidos: [
            { id: 5, nombre: 'Partido Uno', siglas: 'P1', numeroLista: 7 },
          ],
        },
      ],
    };

    const dataPromise = firstValueFrom(service.getCatalogoDeJrv(99));

    const req = httpMock.expectOne(
      `${environment.apiUrl}/observer/cargos-with-candidatos?jrvId=99`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(mockResponse);

    const data = await dataPromise;
    expect(data).toHaveLength(2);
    expect(data[0].id).toBe(10);
    expect(data[0].candidatos[0]).toEqual({
      id: 1,
      nombre: 'Candidato A',
      orden: 1,
      cargoId: 10,
      partidoId: 5,
      partidoNombre: 'Partido Uno',
      partidoSiglas: 'P1',
      partidoNumeroLista: 7,
    });
    expect(data[1].candidatos).toEqual([]);
  });
});
