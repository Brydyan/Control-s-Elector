import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { DashboardApi } from './dashboard.api';

describe('DashboardApi (T-FE-11 A.3)', () => {
  let service: DashboardApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardApi,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(DashboardApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch resumen from canonical path', () => {
    service.getResumen().subscribe((r) => {
      expect(r.totalActivas).toBe(5);
    });
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/resumen`,
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      enviadas: 1,
      enDisputa: 1,
      corregidas: 1,
      aprobadas: 2,
      totalActivas: 5,
    });
  });

  it('should fetch resultados array', () => {
    service.getResultados().subscribe((r) => {
      expect(r.length).toBe(1);
      expect(r[0].cargoNombre).toBe('Prefecto/a');
    });
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/resultados`,
    );
    req.flush([
      {
        cargoId: 1,
        cargoNombre: 'Prefecto/a',
        candidatos: [
          {
            candidatoId: 10,
            candidatoNombre: 'C1',
            partidoNombre: 'PA',
            votos: 100,
          },
        ],
      },
    ]);
  });

  it('should fetch progreso array', () => {
    service.getProgreso().subscribe((p) => {
      expect(p[0].porcentaje).toBe(75);
    });
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/progreso`,
    );
    req.flush([
      {
        cantonId: 1,
        cantonNombre: 'Santa Elena',
        totalJrvs: 200,
        jrvsConActa: 150,
        porcentaje: 75,
      },
    ]);
  });

  it('should fetch novedades array', () => {
    service.getNovedades().subscribe((n) => {
      expect(n[0].tipo).toBe('ACTA_EN_DISPUTA');
      expect(n[0].minutosTranscurridos).toBe(35);
    });
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/novedades`,
    );
    req.flush([
      {
        actaPublicId: '11111111-1111-1111-1111-111111111111',
        tipo: 'ACTA_EN_DISPUTA',
        jrvId: 1,
        jrvNumero: '0023',
        cargoId: 1,
        cargoNombre: 'Alcalde/sa',
        observerPublicId: '22222222-2222-2222-2222-222222222222',
        observerNombre: 'Juan Pérez',
        motivo: null,
        minutosTranscurridos: 35,
      },
    ]);
  });

  it('should consolidate via forkJoin on getDashboardCompleto', () => {
    service.getDashboardCompleto().subscribe((d) => {
      expect(d.resumen.totalActivas).toBe(5);
      expect(d.resultados.length).toBe(1);
      expect(d.progreso.length).toBe(1);
      expect(d.novedades.length).toBe(1);
    });

    httpTesting.expectOne(`${environment.apiUrl}/dashboard/resumen`).flush({
      enviadas: 1,
      enDisputa: 1,
      corregidas: 1,
      aprobadas: 2,
      totalActivas: 5,
    });
    httpTesting
      .expectOne(`${environment.apiUrl}/dashboard/resultados`)
      .flush([{ cargoId: 1, cargoNombre: 'X', candidatos: [] }]);
    httpTesting.expectOne(`${environment.apiUrl}/dashboard/progreso`).flush([
      {
        cantonId: 1,
        cantonNombre: 'X',
        totalJrvs: 10,
        jrvsConActa: 5,
        porcentaje: 50,
      },
    ]);
    httpTesting.expectOne(`${environment.apiUrl}/dashboard/novedades`).flush([
      {
        actaPublicId: '11111111-1111-1111-1111-111111111111',
        tipo: 'ACTA_EN_DISPUTA',
        jrvId: 1,
        jrvNumero: '0023',
        cargoId: 1,
        cargoNombre: 'Alcalde/sa',
        observerPublicId: '22222222-2222-2222-2222-222222222222',
        observerNombre: 'Juan',
        motivo: null,
        minutosTranscurridos: 35,
      },
    ]);
  });

  it('should fall back to empty/zero when an endpoint fails (defensive UX)', () => {
    service.getDashboardCompleto().subscribe((d) => {
      // resumen falla → fallback zeros; resto OK
      expect(d.resumen.totalActivas).toBe(0);
      expect(d.resultados.length).toBe(1);
      expect(d.progreso.length).toBe(0);
      expect(d.novedades.length).toBe(0);
    });

    httpTesting
      .expectOne(`${environment.apiUrl}/dashboard/resumen`)
      .flush('boom', { status: 500, statusText: 'Server Error' });
    httpTesting
      .expectOne(`${environment.apiUrl}/dashboard/resultados`)
      .flush([{ cargoId: 1, cargoNombre: 'X', candidatos: [] }]);
    httpTesting.expectOne(`${environment.apiUrl}/dashboard/progreso`).flush([]);
    httpTesting
      .expectOne(`${environment.apiUrl}/dashboard/novedades`)
      .flush([]);
  });
});

/**
 * Regresion del cache de detalles (`resultados-detallados` / `progreso-detallado`).
 *
 * Esos dos endpoints NUNCA existieron en el backend: el resource expone
 * `/resultados` y `/progreso`. Pedirlos daba 404 y la pagina caia en el estado de
 * error ("No se pudieron cargar los resultados"). El fix (#175) fue consumir los
 * endpoints canonicos y enriquecer en el cliente.
 *
 * Este bloque fija ese contrato para que nadie vuelva a los endpoints inventados,
 * y comprueba que un fallo no queda cacheado (si queda, el boton "reintentar" de
 * la pagina no puede recuperar sin recargar el navegador).
 */
describe('DashboardApi — endpoints canonicos y cache sin errores', () => {
  let service: DashboardApi;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        DashboardApi,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = TestBed.inject(DashboardApi);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('getResultadosDetalles pide /dashboard/resultados (no /resultados-detallados)', () => {
    service.getResultadosDetalles().subscribe();
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/resultados`,
    );
    expect(req.request.method).toBe('GET');
    req.flush([{ cargoId: 1, cargoNombre: 'X', candidatos: [] }]);

    // Si volviera a pedir el endpoint inventado, queda una peticion sin consumir.
    expect(
      httpTesting.match(
        `${environment.apiUrl}/dashboard/resultados-detallados`,
      ),
    ).toEqual([]);
  });

  it('getProgresoDetalles pide /dashboard/progreso (no /progreso-detallado)', () => {
    service.getProgresoDetalles().subscribe();
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/progreso`,
    );
    expect(req.request.method).toBe('GET');
    req.flush([]);

    expect(
      httpTesting.match(`${environment.apiUrl}/dashboard/progreso-detallado`),
    ).toEqual([]);
  });

  it('un fallo NO queda cacheado: el reintento vuelve a pedir el endpoint', () => {
    // Primer intento: falla.
    let primerError: unknown = null;
    service.getResultadosDetalles().subscribe({
      error: (e) => (primerError = e),
    });
    httpTesting
      .expectOne(`${environment.apiUrl}/dashboard/resultados`)
      .flush('boom', { status: 500, statusText: 'Server Error' });
    expect(primerError).toBeTruthy();

    // Segundo intento: tiene que haber una peticion HTTP nueva. Si el error
    // quedara cacheado, `shareReplay` lo reemitiria sin volver a pedir nada y
    // el boton "reintentar" de la pagina no podria recuperar.
    let segundoError: unknown = null;
    service.getResultadosDetalles().subscribe({
      error: (e) => (segundoError = e),
    });
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/resultados`,
    );
    expect(req.request.method).toBe('GET');
    req.flush([{ cargoId: 1, cargoNombre: 'X', candidatos: [] }]);
    expect(segundoError).toBeNull();
  });

  it('un fallo de /progreso NO queda cacheado', () => {
    let primerError: unknown = null;
    service
      .getProgresoDetalles()
      .subscribe({ error: (e) => (primerError = e) });
    httpTesting
      .expectOne(`${environment.apiUrl}/dashboard/progreso`)
      .flush('boom', { status: 500, statusText: 'Server Error' });
    expect(primerError).toBeTruthy();

    let segundoError: unknown = null;
    service
      .getProgresoDetalles()
      .subscribe({ error: (e) => (segundoError = e) });
    const req = httpTesting.expectOne(
      `${environment.apiUrl}/dashboard/progreso`,
    );
    req.flush([]);
    expect(segundoError).toBeNull();
  });
});
