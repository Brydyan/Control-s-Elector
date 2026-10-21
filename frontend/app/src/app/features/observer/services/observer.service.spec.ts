import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { ObserverService } from './observer.service';

describe('ObserverService', () => {
  let service: ObserverService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [ObserverService],
    });
    service = TestBed.inject(ObserverService);
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
});
