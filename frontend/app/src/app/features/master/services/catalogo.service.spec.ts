import { TestBed } from '@angular/core/testing';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { provideHttpClient } from '@angular/common/http';
import { describe, it, expect, beforeEach, afterEach } from 'vitest';
import { CatalogoService } from './catalogo.service';
import { environment } from '../../../../environments/environment';
import {
  CargoRequestDto,
  CargoResponseDto,
  PartidoRequestDto,
  PartidoResponseDto,
  CandidatoRequestDto,
  CandidatoResponseDto,
} from '../models/catalogo.model';

describe('CatalogoService', () => {
  let service: CatalogoService;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}/configuracion`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        CatalogoService,
        provideHttpClient(),
        provideHttpClientTesting(),
      ],
    });
    service = injectCatalogoService();
    httpMock = TestBed.inject(HttpTestingController);
  });

  function injectCatalogoService(): CatalogoService {
    return TestBed.inject(CatalogoService);
  }

  afterEach(() => {
    httpMock.verify();
  });

  describe('Cargos', () => {
    it('debe listar cargos', () => {
      const mockCargos: CargoResponseDto[] = [
        { id: 1, nombre: 'Presidente', nivel: 'NACIONAL', puestos: 1 },
      ];

      service.listarCargos().subscribe((data) => {
        expect(data).toEqual(mockCargos);
      });

      const req = httpMock.expectOne(`${baseUrl}/cargos`);
      expect(req.request.method).toBe('GET');
      req.flush(mockCargos);
    });

    it('debe crear un cargo', () => {
      const payload: CargoRequestDto = {
        nombre: 'Asambleísta',
        nivel: 'PROVINCIA',
        puestos: 4,
      };
      const response: CargoResponseDto = { id: 2, ...payload };

      service.crearCargo(payload).subscribe((data) => {
        expect(data).toEqual(response);
      });

      const req = httpMock.expectOne(`${baseUrl}/cargos`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(payload);
      req.flush(response);
    });

    it('debe actualizar un cargo', () => {
      const payload: CargoRequestDto = {
        nombre: 'Alcalde',
        nivel: 'CANTON',
        puestos: 1,
      };
      const response: CargoResponseDto = { id: 3, ...payload };

      service.actualizarCargo(3, payload).subscribe((data) => {
        expect(data).toEqual(response);
      });

      const req = httpMock.expectOne(`${baseUrl}/cargos/3`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(payload);
      req.flush(response);
    });

    it('debe eliminar un cargo', () => {
      service.eliminarCargo(3).subscribe((res) => {
        expect(res).toBeUndefined();
      });

      const req = httpMock.expectOne(`${baseUrl}/cargos/3`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('Partidos', () => {
    it('debe listar partidos', () => {
      const mockPartidos: PartidoResponseDto[] = [
        { id: 1, nombre: 'Movimiento Verde', siglas: 'MV', numeroLista: 5 },
      ];

      service.listarPartidos().subscribe((data) => {
        expect(data).toEqual(mockPartidos);
      });

      const req = httpMock.expectOne(`${baseUrl}/partidos`);
      expect(req.request.method).toBe('GET');
      req.flush(mockPartidos);
    });

    it('debe crear un partido', () => {
      const payload: PartidoRequestDto = {
        nombre: 'Partido Azul',
        siglas: 'PA',
        numeroLista: 10,
      };
      const response: PartidoResponseDto = { id: 2, ...payload };

      service.crearPartido(payload).subscribe((data) => {
        expect(data).toEqual(response);
      });

      const req = httpMock.expectOne(`${baseUrl}/partidos`);
      expect(req.request.method).toBe('POST');
      req.flush(response);
    });

    it('debe actualizar un partido', () => {
      const payload: PartidoRequestDto = {
        nombre: 'Partido Rojo',
        siglas: 'PR',
        numeroLista: 12,
      };
      const response: PartidoResponseDto = { id: 2, ...payload };

      service.actualizarPartido(2, payload).subscribe((data) => {
        expect(data).toEqual(response);
      });

      const req = httpMock.expectOne(`${baseUrl}/partidos/2`);
      expect(req.request.method).toBe('PUT');
      req.flush(response);
    });

    it('debe eliminar un partido', () => {
      service.eliminarPartido(2).subscribe((res) => {
        expect(res).toBeUndefined();
      });

      const req = httpMock.expectOne(`${baseUrl}/partidos/2`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });

  describe('Candidatos', () => {
    it('debe listar candidatos con filtros', () => {
      const mockCandidatos: CandidatoResponseDto[] = [
        { id: 1, nombre: 'Juan Pérez', orden: 1, cargoId: 1, partidoId: 2 },
      ];

      service
        .listarCandidatos({ cargoId: 1, partidoId: 2 })
        .subscribe((data) => {
          expect(data).toEqual(mockCandidatos);
        });

      const req = httpMock.expectOne(
        `${baseUrl}/candidatos?cargoId=1&partidoId=2`,
      );
      expect(req.request.method).toBe('GET');
      req.flush(mockCandidatos);
    });

    it('debe crear un candidato', () => {
      const payload: CandidatoRequestDto = {
        nombre: 'Ana Gómez',
        orden: 1,
        cargoId: 1,
        partidoId: 2,
      };
      const response: CandidatoResponseDto = { id: 5, ...payload };

      service.crearCandidato(payload).subscribe((data) => {
        expect(data).toEqual(response);
      });

      const req = httpMock.expectOne(`${baseUrl}/candidatos`);
      expect(req.request.method).toBe('POST');
      req.flush(response);
    });

    it('debe actualizar un candidato', () => {
      const payload: CandidatoRequestDto = {
        nombre: 'Ana Gómez Actualizada',
        orden: 2,
        cargoId: 1,
        partidoId: 2,
      };
      const response: CandidatoResponseDto = { id: 5, ...payload };

      service.actualizarCandidato(5, payload).subscribe((data) => {
        expect(data).toEqual(response);
      });

      const req = httpMock.expectOne(`${baseUrl}/candidatos/5`);
      expect(req.request.method).toBe('PUT');
      req.flush(response);
    });

    it('debe eliminar un candidato', () => {
      service.eliminarCandidato(5).subscribe((res) => {
        expect(res).toBeUndefined();
      });

      const req = httpMock.expectOne(`${baseUrl}/candidatos/5`);
      expect(req.request.method).toBe('DELETE');
      req.flush(null, { status: 204, statusText: 'No Content' });
    });
  });
});
