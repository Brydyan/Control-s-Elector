import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { MisActasService, ActaDTO, PageResult } from './mis-actas.api';
import { environment } from '../../../../environments/environment';

describe('MisActasService', () => {
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
        content: [
          {
            id: '1',
            numeroActa: '001',
            jrvNombre: 'J-1',
            fechaTransmision: '2026-11-08T15:30:00Z',
            estado: 'TRANSMITIDO',
            accionLabel: 'Ver detalles',
          },
        ],
        totalElements: 10,
        totalPages: 1,
        currentPage: 0,
        size: 10,
      };

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: null,
          jrvId: null,
        })
        .subscribe((result) => {
          expect(result.content.length).toBe(1);
          expect(result.totalElements).toBe(10);
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
        content: [],
        totalElements: 0,
        totalPages: 0,
        currentPage: 0,
        size: 10,
      };

      service
        .getMisActas({
          page: 0,
          size: 10,
          estado: 'TRANSMITIDO',
          jrvId: null,
        })
        .subscribe();

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl &&
          request.params.get('estado') === 'TRANSMITIDO'
        );
      });

      expect(req.request.method).toBe('GET');
      req.flush(mockResponse);
    });

    it('should include jrvId filter when provided', () => {
      const mockResponse: PageResult<ActaDTO> = {
        content: [],
        totalElements: 0,
        totalPages: 0,
        currentPage: 0,
        size: 10,
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
        content: [],
        totalElements: 0,
        totalPages: 0,
        currentPage: 0,
        size: 10,
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
        content: [],
        totalElements: 0,
        totalPages: 0,
        currentPage: 0,
        size: 10,
      };

      service
        .getMisActas({
          page: 1,
          size: 20,
          estado: 'REVISION',
          jrvId: 'jrv-456',
        })
        .subscribe();

      const req = httpMock.expectOne((request) => {
        return (
          request.url === baseUrl &&
          request.params.get('page') === '1' &&
          request.params.get('size') === '20' &&
          request.params.get('estado') === 'REVISION' &&
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
