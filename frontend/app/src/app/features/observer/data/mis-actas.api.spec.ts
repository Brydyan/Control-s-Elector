import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { MisActasService, ActaDTO, PageResult } from './mis-actas.api';
import { environment } from '../../../../environments/environment';

describe('MisActasService', () => {
  /** Acta con la forma de `ActaResponseDto`, el DTO que devuelve el backend. */
  const ACTA_STUB: ActaDTO = {
    id: 1,
    jrvId: 1,
    cargoId: 1,
    registradaPorId: 4,
    estado: 'ENVIADA',
    totalSufragantes: 300,
    votosBlancos: 20,
    votosNulos: 10,
    fechaRegistro: '2026-11-08T15:30:00Z',
    motivoAnulacion: null,
    nombreCargo: 'Alcalde/sa',
    numeroJrv: 'JRV-SE-001',
    totalVotosCandidatos: 270,
  };

  let service: MisActasService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/observer/mis-actas`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [MisActasService],
    });

    service = TestBed.inject(MisActasService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getMisActas', () => {
    it('should fetch actas with pagination', () => {
      const mockResponse: PageResult<ActaDTO> = {
        data: [ACTA_STUB],
        meta: { page: 0, size: 10, totalElements: 10, totalPages: 1 },
      };

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: null,
          jrvId: null,
        })
        .subscribe((result) => {
          expect(result.data.length).toBe(1);
          expect(result.meta.totalElements).toBe(10);
        });

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl &&
          request.params.has('page') &&
          request.params.get('page') === '0' &&
          request.params.get('size') === '10'
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should include estado filter when provided', () => {
      const mockResponse: PageResult<ActaDTO> = {
        data: [],
        meta: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
      };

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: 'ENVIADA',
          jrvId: null,
        })
        .subscribe();

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl && request.params.get('estado') === 'ENVIADA'
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should include jrvId filter when provided', () => {
      const mockResponse: PageResult<ActaDTO> = {
        data: [],
        meta: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
      };

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: null,
          jrvId: 'jrv-123',
        })
        .subscribe();

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl && request.params.get('jrvId') === 'jrv-123'
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should not include null filters in query params', () => {
      const mockResponse: PageResult<ActaDTO> = {
        data: [],
        meta: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
      };

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: null,
          jrvId: null,
        })
        .subscribe();

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl &&
          !request.params.has('estado') &&
          !request.params.has('jrvId')
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should handle multiple filters simultaneously', () => {
      const mockResponse: PageResult<ActaDTO> = {
        data: [],
        meta: { page: 0, size: 10, totalElements: 0, totalPages: 0 },
      };

      service
        .getMisActas({
          page: 1,
          size: 20,
          estado: 'EN_DISPUTA',
          jrvId: 'jrv-456',
        })
        .subscribe();

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl &&
          request.params.get('page') === '1' &&
          request.params.get('size') === '20' &&
          request.params.get('estado') === 'EN_DISPUTA' &&
          request.params.get('jrvId') === 'jrv-456'
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });
  });

  describe('HTTP error handling', () => {
    it('should propagate HTTP errors', () => {
      let errorOccurred = false;

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: null,
          jrvId: null,
        })
        .subscribe({
          error: () => {
            errorOccurred = true;
          },
        });

      const req = httpMock.expectOne((request) => request.url === baseUrl);
      req.error(new ProgressEvent('error'), {
        status: 500,
        statusText: 'Server Error',
      });

      expect(errorOccurred).toBeTruthy();
    });

    it('should propagate 403 Forbidden error', () => {
      return new Promise<void>((resolve, reject) => {
        service
          .getMisActas({
            page: 0,
            size: 10,
            estado: null,
            jrvId: null,
          })
          .subscribe({
            error: (err) => {
              if (err.status === 403) {
                resolve();
              } else {
                reject(new Error('Expected 403 error'));
              }
            },
          });

        const req = httpMock.expectOne((request) => request.url === baseUrl);
        req.error(new ProgressEvent('error'), {
          status: 403,
          statusText: 'Forbidden',
        });
      });
    });
  });
});
