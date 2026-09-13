import { provideHttpClient } from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { CoordinatorService } from './coordinator.service';

describe('CoordinatorService', () => {
  let service: CoordinatorService;
  let httpTesting: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CoordinatorService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });

    service = TestBed.inject(CoordinatorService);
    httpTesting = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should be created', () => {
    expect(service).toBeTruthy();
  });

  it('should fetch actas with filters as paged envelope', () => {
    service
      .getActas({ estado: 'ENVIADA', cargoId: 1, jrvId: 10 })
      .subscribe((paged) => {
        expect(paged.data.length).toBe(1);
        expect(paged.data[0].estado).toBe('ENVIADA');
        expect(paged.meta.totalElements).toBe(1);
        expect(paged.meta.totalPages).toBe(1);
      });

    const req = httpTesting.expectOne(
      (r) =>
        r.url === `${environment.apiUrl}/coordinator/actas` &&
        r.params.get('estado') === 'ENVIADA' &&
        r.params.get('cargoId') === '1' &&
        r.params.get('jrvId') === '10',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      data: [
        {
          id: 1,
          jrvId: 10,
          cargoId: 1,
          estado: 'ENVIADA',
          totalSufragantes: 100,
          votosBlancos: 2,
          votosNulos: 3,
          fechaRegistro: '2026-09-12T12:00:00Z',
        },
      ],
      meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
  });

  it('should map dignidad alias to cargoId param', () => {
    service.getActas({ dignidad: 5 }).subscribe();

    const req = httpTesting.expectOne(
      (r) =>
        r.url === `${environment.apiUrl}/coordinator/actas` &&
        r.params.get('cargoId') === '5',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      data: [],
      meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
    });
  });

  it('should send pagination, search and sort params', () => {
    service
      .getActas({
        search: 'JRV-12',
        page: 2,
        size: 10,
        sort: 'id',
        dir: 'asc',
      })
      .subscribe();

    const req = httpTesting.expectOne(
      (r) =>
        r.url === `${environment.apiUrl}/coordinator/actas` &&
        r.params.get('search') === 'JRV-12' &&
        r.params.get('page') === '2' &&
        r.params.get('size') === '10' &&
        r.params.get('sort') === 'id' &&
        r.params.get('dir') === 'asc',
    );
    expect(req.request.method).toBe('GET');
    req.flush({
      data: [],
      meta: { page: 2, size: 10, totalElements: 0, totalPages: 0 },
    });
  });

  it('should pass backend denominatives through', () => {
    service.getActas({ estado: 'ENVIADA' }).subscribe((paged) => {
      expect(paged.data[0].nombreCargo).toBe('Prefecto/a');
      expect(paged.data[0].numeroJrv).toBe('JRV-12');
      expect(paged.data[0].totalVotosCandidatos).toBe(95);
    });

    const req = httpTesting.expectOne(
      (r) =>
        r.url === `${environment.apiUrl}/coordinator/actas` &&
        r.params.get('estado') === 'ENVIADA',
    );
    req.flush({
      data: [
        {
          id: 1,
          jrvId: 10,
          cargoId: 1,
          estado: 'ENVIADA',
          totalSufragantes: 100,
          votosBlancos: 2,
          votosNulos: 3,
          fechaRegistro: '2026-09-12T12:00:00Z',
          nombreCargo: 'Prefecto/a',
          numeroJrv: 'JRV-12',
          totalVotosCandidatos: 95,
        },
      ],
      meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
    });
  });

  it('should use exact checksum when the subtotal is present', () => {
    service.getActaDetalleCompleto(7).subscribe((detalle) => {
      // 95 + 2 + 3 == 100 → exact checksum.
      expect(detalle.totalVotosCalculado).toBe(100);
      expect(detalle.diferenciaChecksum).toBe(0);
      expect(detalle.checksumValido).toBe(true);
    });

    const actaReq = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas/7`,
    );
    actaReq.flush({
      id: 7,
      jrvId: 10,
      cargoId: 1,
      estado: 'ENVIADA',
      totalSufragantes: 100,
      votosBlancos: 2,
      votosNulos: 3,
      fechaRegistro: '2026-09-12T12:00:00Z',
      nombreCargo: 'Prefecto/a',
      numeroJrv: 'JRV-12',
      totalVotosCandidatos: 95,
    });

    httpTesting
      .expectOne(`${environment.apiUrl}/configuracion/cargos`)
      .flush([{ id: 1, nombre: 'Prefecto/a', nivel: 'PROVINCIA' }]);
    httpTesting.expectOne(`${environment.apiUrl}/observer/jrvs`).flush([
      {
        jrvId: 10,
        numero: 'JRV-12',
        parroquia: 'Centro',
        cargosPendientes: 0,
        cargosRegistrados: 1,
      },
    ]);
    httpTesting
      .expectOne(`${environment.apiUrl}/configuracion/candidatos?cargoId=1`)
      .flush([]);
    httpTesting
      .expectOne(`${environment.apiUrl}/configuracion/partidos`)
      .flush([]);
  });

  it('should throw on a bare array response instead of wrapping it', () => {
    let caught: Error | null = null;
    service.getActas().subscribe({
      next: () => fail('expected an error for bare array response'),
      error: (err: Error) => (caught = err),
    });

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas`,
    );
    req.flush([
      {
        id: 1,
        jrvId: 10,
        cargoId: 1,
        estado: 'ENVIADA',
        totalSufragantes: 100,
        votosBlancos: 2,
        votosNulos: 3,
        fechaRegistro: '2026-09-12T12:00:00Z',
      },
    ]);

    expect(caught).toBeTruthy();
    expect((caught as unknown as Error).message).toContain('{ data, meta }');
  });

  it('should approve an acta', () => {
    service.aprobarActa(123).subscribe((acta) => {
      expect(acta.estado).toBe('APROBADA');
    });

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas/123/aprobar`,
    );
    expect(req.request.method).toBe('PUT');
    req.flush({
      id: 123,
      jrvId: 1,
      cargoId: 1,
      estado: 'APROBADA',
      totalSufragantes: 100,
      votosBlancos: 2,
      votosNulos: 3,
      fechaRegistro: '2026-09-12T12:00:00Z',
    });
  });

  it('should dispute an acta', () => {
    service
      .disputarActa(123, { motivo: 'Discrepancia en votos' })
      .subscribe((acta) => {
        expect(acta.estado).toBe('EN_DISPUTA');
      });

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas/123/disputar`,
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ motivo: 'Discrepancia en votos' });
    req.flush({
      id: 123,
      jrvId: 1,
      cargoId: 1,
      estado: 'EN_DISPUTA',
      totalSufragantes: 100,
      votosBlancos: 2,
      votosNulos: 3,
      fechaRegistro: '2026-09-12T12:00:00Z',
    });
  });

  it('should correct an acta', () => {
    const payload = {
      jrvId: 1,
      cargoId: 1,
      totalSufragantes: 100,
      votosBlancos: 1,
      votosNulos: 2,
      detalles: [{ candidatoId: 10, votos: 97 }],
    };

    service.corregirActa(123, payload).subscribe((acta) => {
      expect(acta.estado).toBe('CORREGIDA');
    });

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas/123/corregir`,
    );
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual(payload);
    req.flush({
      id: 123,
      jrvId: 1,
      cargoId: 1,
      estado: 'CORREGIDA',
      totalSufragantes: 100,
      votosBlancos: 1,
      votosNulos: 2,
      fechaRegistro: '2026-09-12T12:00:00Z',
    });
  });
});
