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
      // T-FE-10 E.2: votos reales vienen en `detalles` (no hardcode 0).
      const votosPorCandidato = detalle.candidatos.reduce(
        (acc, c) => acc + c.votos,
        0,
      );
      expect(votosPorCandidato).toBe(95);
      expect(detalle.candidatos[0].nombre).toBe('Candidato A');
      expect(detalle.candidatos[0].partidoSiglas).toBe('PA');
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
      detalles: [
        { candidatoId: 100, votos: 50 },
        { candidatoId: 101, votos: 45 },
      ],
      urlEvidencia: 'https://s3.example.com/acta-7.webp',
    });

    const candReq = httpTesting.expectOne(
      `${environment.apiUrl}/configuracion/candidatos?cargoId=1`,
    );
    candReq.flush([
      { id: 100, nombre: 'Candidato A', orden: 1, cargoId: 1, partidoId: 10 },
      { id: 101, nombre: 'Candidato B', orden: 2, cargoId: 1, partidoId: 20 },
    ]);

    const partidosReq = httpTesting.expectOne(
      `${environment.apiUrl}/configuracion/partidos`,
    );
    partidosReq.flush([
      { id: 10, nombre: 'Partido A', siglas: 'PA', numeroLista: 1 },
      { id: 20, nombre: 'Partido B', siglas: 'PB', numeroLista: 2 },
    ]);
  });

  /**
   * T-FE-10 E.3: el detalle expone `urlEvidencia` (URL prefirmada S3),
   * no `storageUrl`.
   */
  it('should expose urlEvidencia on acta detail (T-FE-10 E.3)', () => {
    service.getActaDetalleCompleto(8).subscribe((detalle) => {
      expect(detalle.acta.urlEvidencia).toBe(
        'https://s3.example.com/acta-8.webp',
      );
      // El modelo legacy storageUrl ya no existe.
      expect(
        (detalle.acta as unknown as { storageUrl?: string }).storageUrl,
      ).toBeUndefined();
    });

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas/8`,
    );
    req.flush({
      id: 8,
      jrvId: 1,
      cargoId: 1,
      estado: 'ENVIADA',
      totalSufragantes: 100,
      votosBlancos: 0,
      votosNulos: 0,
      fechaRegistro: '2026-09-12T12:00:00Z',
      urlEvidencia: 'https://s3.example.com/acta-8.webp',
    });
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

  /**
   * T-FE-10 E.1 / V.3: `anularActa` debe enviar `{ motivo }` en el body del
   * DELETE para evitar 400 `MissingRequestBody` del backend.
   */
  it('should send motivo in DELETE body when anularActa is called', () => {
    service.anularActa(99, 'Datos verificados como fraudulentos').subscribe();

    const req = httpTesting.expectOne(
      `${environment.apiUrl}/coordinator/actas/99`,
    );
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({
      motivo: 'Datos verificados como fraudulentos',
    });
    req.flush(null);
  });
});
