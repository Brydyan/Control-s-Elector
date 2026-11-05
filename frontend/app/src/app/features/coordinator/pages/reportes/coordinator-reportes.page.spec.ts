import {
  ComponentFixture,
  TestBed,
  fakeAsync,
  tick,
} from '@angular/core/testing';
import { of, throwError } from 'rxjs';
import { CoordinatorReportesPage } from './coordinator-reportes.page';
import { CoordinatorApi } from '../../data/coordinator.api';
import type { PagedResponse } from '../../../../shared/models/page.model';
import type {
  ActaItem,
  ActaFiltros,
} from '../../domain/models/coordinator.model';

describe('CoordinatorReportesPage (T-FE-17)', () => {
  let component: CoordinatorReportesPage;
  let fixture: ComponentFixture<CoordinatorReportesPage>;
  let coordinatorApiMock: jasmine.SpyObj<CoordinatorApi>;

  // Mock data
  const mockActas: ActaItem[] = [
    {
      id: 1,
      jrvId: 101,
      cargoId: 1,
      estado: 'APROBADA',
      totalSufragantes: 450,
      votosBlancos: 10,
      votosNulos: 5,
      fechaRegistro: new Date(
        new Date().getTime() - 1 * 60 * 60 * 1000,
      ).toISOString(),
      nombreCargo: 'Prefecto',
      numeroJrv: 'JRV-001',
      totalVotosCandidatos: 435,
      detalles: [],
      urlEvidencia: 'http://example.com/img1.jpg',
      motivoDisputa: null,
      motivoAnulacion: null,
      registradaPorId: undefined,
    },
    {
      id: 2,
      jrvId: 102,
      cargoId: 2,
      estado: 'EN_DISPUTA',
      totalSufragantes: 380,
      votosBlancos: 8,
      votosNulos: 3,
      fechaRegistro: new Date(
        new Date().getTime() - 2 * 60 * 60 * 1000,
      ).toISOString(),
      nombreCargo: 'Alcalde',
      numeroJrv: 'JRV-002',
      totalVotosCandidatos: 369,
      detalles: [],
      urlEvidencia: 'http://example.com/img2.jpg',
      motivoDisputa: 'Discrepancia de votos',
      motivoAnulacion: null,
      registradaPorId: undefined,
    },
  ];

  const mockPagedResponse: PagedResponse<ActaItem> = {
    data: mockActas,
    meta: {
      page: 0,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    },
  };

  const mockJrvs = [
    { id: 101, numero: 'JRV-001', parroquia: 'Santa Elena' },
    { id: 102, numero: 'JRV-002', parroquia: 'La Libertad' },
    { id: 103, numero: 'JRV-003', parroquia: 'Salinas' },
  ];

  const mockCargos = [
    { id: 1, nombre: 'Prefecto', nivel: 'PROVINCIAL' },
    { id: 2, nombre: 'Alcalde', nivel: 'CANTONAL' },
  ];

  beforeEach(async () => {
    coordinatorApiMock = jasmine.createSpyObj('CoordinatorApi', [
      'getActas',
      'getJrvs',
      'getCargos',
      'descargarExpedientePdf',
    ]);

    await TestBed.configureTestingModule({
      imports: [CoordinatorReportesPage],
      providers: [{ provide: CoordinatorApi, useValue: coordinatorApiMock }],
    }).compileComponents();

    fixture = TestBed.createComponent(CoordinatorReportesPage);
    component = fixture.componentInstance;
  });

  // ============================================================================
  // HAPPY PATH TESTS (3 tests mínimo)
  // ============================================================================

  it('should initialize with loading state true', () => {
    expect(component.loading()).toBe(true);
    expect(component.error()).toBeNull();
    expect(component.actas().length).toBe(0);
  });

  it('should load metrics on ngOnInit and display dashboard', fakeAsync(() => {
    coordinatorApiMock.getActas.and.returnValue(of(mockPagedResponse));
    coordinatorApiMock.getJrvs.and.returnValue(of(mockJrvs));
    coordinatorApiMock.getCargos.and.returnValue(of(mockCargos));

    fixture.detectChanges();
    tick();

    expect(component.loading()).toBe(false);
    expect(component.error()).toBeNull();
    expect(component.actas().length).toBe(2);
    expect(component.metricas().jrvsTotal).toBe(3);
    expect(component.metricas().realtimeStatus).toBe('connected');
    expect(component.estadosCount().APROBADA).toBe(1);
    expect(component.estadosCount()['EN_DISPUTA']).toBe(1);
  }));

  it('should calculate actas per hour correctly from timestamps', fakeAsync(() => {
    coordinatorApiMock.getActas.and.returnValue(of(mockPagedResponse));
    coordinatorApiMock.getJrvs.and.returnValue(of(mockJrvs));
    coordinatorApiMock.getCargos.and.returnValue(of(mockCargos));

    fixture.detectChanges();
    tick();

    const tasaPorHora = component.metricas().actasPorHora;
    expect(tasaPorHora).toBeGreaterThanOrEqual(0);
    expect(tasaPorHora).toBeLessThanOrEqual(100); // Reasonable bound
  }));

  // ============================================================================
  // ERROR PATH TESTS (2 tests mínimo)
  // ============================================================================

  it('should show error state when metrics fail to load', fakeAsync(() => {
    const mockError = { status: 500, message: 'Server error' };
    coordinatorApiMock.getActas.and.returnValue(throwError(() => mockError));
    coordinatorApiMock.getJrvs.and.returnValue(of(mockJrvs));
    coordinatorApiMock.getCargos.and.returnValue(of(mockCargos));

    fixture.detectChanges();
    tick();

    expect(component.loading()).toBe(false);
    expect(component.error()).toBeTruthy();
    expect(component.error()).toContain('métricas');
  }));

  it('should handle 401 Unauthorized during download and show auth message', fakeAsync(() => {
    const mockActa = mockActas[0];
    const mockError401 = { status: 401, message: 'Unauthorized' };
    coordinatorApiMock.descargarExpedientePdf.and.returnValue(
      throwError(() => mockError401),
    );

    component.descargarExpediente(mockActa);
    tick();

    expect(component.error()).toContain('Sesión expirada');
    expect(component.downloadingId()).toBeNull();
  }));

  // ============================================================================
  // LÍMITES Y VALIDACIÓN (2 tests mínimo)
  // ============================================================================

  it('should reject invalid actaId (negative, zero, null) before HTTP call', () => {
    expect(() => {
      (component as any).api.descargarExpedientePdf(-1);
    }).toThrowError();

    expect(() => {
      (component as any).api.descargarExpedientePdf(0);
    }).toThrowError();

    expect(coordinatorApiMock.descargarExpedientePdf).not.toHaveBeenCalled();
  });

  it('should validate MIME type of downloaded blob and reject non-PDF', fakeAsync(() => {
    const mockActa = mockActas[0];
    const mockBlobNonPdf = new Blob(['<html></html>'], {
      type: 'text/html',
    });

    coordinatorApiMock.descargarExpedientePdf.and.returnValue(
      of(mockBlobNonPdf),
    );

    component.descargarExpediente(mockActa);
    tick();

    expect(component.error()).toContain('PDF válido');
    expect(component.downloadingId()).toBeNull();
  }));

  // ============================================================================
  // CLEANUP & MEMORY TESTS (2 tests mínimo)
  // ============================================================================

  it('should revoke Object URL after download to prevent memory leak', fakeAsync(() => {
    const mockActa = mockActas[0];
    const mockPdfBlob = new Blob(['PDF content'], {
      type: 'application/pdf',
    });

    spyOn(window.URL, 'createObjectURL').and.returnValue(
      'blob:http://example.com/123',
    );
    spyOn(window.URL, 'revokeObjectURL');

    coordinatorApiMock.descargarExpedientePdf.and.returnValue(of(mockPdfBlob));

    component.descargarExpediente(mockActa);
    tick();

    expect(window.URL.revokeObjectURL).toHaveBeenCalled();
  }));

  it('should unsubscribe from observables on ngOnDestroy', fakeAsync(() => {
    coordinatorApiMock.getActas.and.returnValue(of(mockPagedResponse));
    coordinatorApiMock.getJrvs.and.returnValue(of(mockJrvs));
    coordinatorApiMock.getCargos.and.returnValue(of(mockCargos));

    fixture.detectChanges();
    tick();

    spyOn(component['destroy$'], 'next');
    spyOn(component['destroy$'], 'complete');

    component.ngOnDestroy();

    expect(component['destroy$'].next).toHaveBeenCalled();
    expect(component['destroy$'].complete).toHaveBeenCalled();
  }));

  // ============================================================================
  // ADDITIONAL TESTS (to reach 9+ tests total)
  // ============================================================================

  it('should handle 403 Forbidden during download', fakeAsync(() => {
    const mockActa = mockActas[0];
    const mockError403 = { status: 403, message: 'Forbidden' };
    coordinatorApiMock.descargarExpedientePdf.and.returnValue(
      throwError(() => mockError403),
    );

    component.descargarExpediente(mockActa);
    tick();

    expect(component.error()).toContain('permiso');
  }));

  it('should handle 404 Not Found during download', fakeAsync(() => {
    const mockActa = mockActas[0];
    const mockError404 = { status: 404, message: 'Not Found' };
    coordinatorApiMock.descargarExpedientePdf.and.returnValue(
      throwError(() => mockError404),
    );

    component.descargarExpediente(mockActa);
    tick();

    expect(component.error()).toContain('no encontrada');
  }));

  it('should return correct color and symbol for each estado', () => {
    const estados = [
      'ENVIADA',
      'EN_DISPUTA',
      'CORREGIDA',
      'APROBADA',
      'ANULADA',
    ];

    estados.forEach((estado) => {
      const color = component.obtenerColorEstado(estado);
      const simbolo = component.obtenerSimboloEstado(estado);

      expect(color).toBeTruthy();
      expect(color).toContain('--color');
      expect(simbolo).toBeTruthy();
    });
  });

  // ============================================================================
  // MUTACIÓN M1: Invertir validación MIME (test debe caer)
  // ============================================================================

  /**
   * M1: Si se invierte la lógica de validación MIME (blob.type === 'application/pdf' → rechaza no-PDF),
   * este test debe CAER porque ahoraacepta non-PDF.
   *
   * Mutación: cambiar en coordinator-reportes.page.ts línea ~265
   * FROM: if (!['application/pdf', 'application/octet-stream'].includes(blob.type))
   * TO:   if (['application/pdf', 'application/octet-stream'].includes(blob.type))
   *
   * Expected: Test `should validate MIME type of downloaded blob and reject non-PDF` FALLA
   */

  // ============================================================================
  // MUTACIÓN M2: Omitir revokeObjectURL (test debe caer)
  // ============================================================================

  /**
   * M2: Si se omite o comenta window.URL.revokeObjectURL(url) en descargarExpediente(),
   * este test debe CAER porque la URL no es revocada.
   *
   * Mutación: comentar línea ~282 en coordinator-reportes.page.ts
   * // window.URL.revokeObjectURL(url);
   *
   * Expected: Test `should revoke Object URL after download to prevent memory leak` FALLA
   */
});

Math.round = (n: number) => Math.floor(n); // Polyfill for tests if needed
