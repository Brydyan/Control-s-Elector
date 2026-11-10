import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { MisActasPage } from './mis-actas.page';
import { MisActasService, ActaDTO, PageResult } from '../../data/mis-actas.api';

describe('MisActasPage', () => {
  let component: MisActasPage;
  let fixture: ComponentFixture<MisActasPage>;
  let mockMisActasService: jasmine.SpyObj<MisActasService>;
  let mockRouter: jasmine.SpyObj<Router>;

  const mockActaDTO: ActaDTO = {
    id: '1',
    numeroActa: '001',
    jrvNombre: 'J-1',
    fechaTransmision: '2026-11-08T15:30:00Z',
    estado: 'TRANSMITIDO',
    accionLabel: 'Ver detalles',
  };

  const mockPageResult: PageResult<ActaDTO> = {
    content: [mockActaDTO],
    totalElements: 1,
    totalPages: 1,
    currentPage: 0,
    size: 10,
  };

  beforeEach(async () => {
    mockMisActasService = jasmine.createSpyObj('MisActasService', [
      'getMisActas',
    ]);
    mockRouter = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [MisActasPage],
      providers: [
        { provide: MisActasService, useValue: mockMisActasService },
        { provide: Router, useValue: mockRouter },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MisActasPage);
    component = fixture.componentInstance;
  });

  describe('initialization', () => {
    it('should create', () => {
      expect(component).toBeTruthy();
    });

    it('should load actas on init', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));

      fixture.detectChanges(); // ngOnInit

      expect(mockMisActasService.getMisActas).toHaveBeenCalled();
      expect(component.actas().length).toBe(1);
      expect(component.totalItems()).toBe(1);
      expect(component.loading()).toBeFalsy();
    });
  });

  describe('data loading', () => {
    it('should populate actas signal from service response', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));

      fixture.detectChanges();

      expect(component.actas()).toEqual([mockActaDTO]);
    });

    it('should set totalItems from service response', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));

      fixture.detectChanges();

      expect(component.totalItems()).toBe(1);
    });

    it('should clear error on successful load', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));

      component.cargar();

      expect(component.error()).toBeNull();
    });
  });

  describe('error handling', () => {
    it('should set error message on load failure', (done) => {
      const error = new Error('Network error');
      mockMisActasService.getMisActas.and.returnValue(throwError(() => error));

      component.cargar();

      fixture.detectChanges();
      fixture.whenStable().then(() => {
        expect(component.error()).toBeTruthy();
        expect(component.loading()).toBeFalsy();
        done();
      });
    });

    it('should redirect to dashboard on 403 error', (done) => {
      const error = { status: 403 };
      mockMisActasService.getMisActas.and.returnValue(throwError(() => error));

      component.cargar();

      fixture.detectChanges();
      fixture.whenStable().then(() => {
        // Wait for setTimeout
        setTimeout(() => {
          expect(mockRouter.navigate).toHaveBeenCalledWith(['/dashboard']);
          done();
        }, 2100);
      });
    });

    it('should show custom error message on 403', (done) => {
      const error = { status: 403 };
      mockMisActasService.getMisActas.and.returnValue(throwError(() => error));

      component.cargar();

      fixture.detectChanges();
      fixture.whenStable().then(() => {
        expect(component.error()).toContain('JRVs asignadas');
        done();
      });
    });

    it('should show generic error message on other errors', (done) => {
      const error = { status: 500 };
      mockMisActasService.getMisActas.and.returnValue(throwError(() => error));

      component.cargar();

      fixture.detectChanges();
      fixture.whenStable().then(() => {
        expect(component.error()).toContain('No se pudieron cargar');
        done();
      });
    });
  });

  describe('pagination', () => {
    it('should update page on onPageChange', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));
      fixture.detectChanges();

      component.onPageChange(2);

      expect(component.currentPage()).toBe(2);
    });

    it('should call cargar on page change', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));
      fixture.detectChanges();

      component.onPageChange(1);

      expect(mockMisActasService.getMisActas).toHaveBeenCalledTimes(2); // init + page change
    });
  });

  describe('navigation', () => {
    it('should navigate to /observer/mis-jrvs on volver', () => {
      component.volver();

      expect(mockRouter.navigate).toHaveBeenCalledWith(['/observer/mis-jrvs']);
    });

    it('should navigate to acta detail on onRowClick', () => {
      component.onRowClick(mockActaDTO);

      expect(mockRouter.navigate).toHaveBeenCalledWith([
        '/observer/mis-actas',
        '1',
        'detalles',
      ]);
    });
  });

  describe('retry', () => {
    it('should reload actas on reintentar', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));
      fixture.detectChanges();

      component.reintentar();

      expect(mockMisActasService.getMisActas).toHaveBeenCalledTimes(2); // init + reintentar
    });

    it('should clear error on reintentar', () => {
      mockMisActasService.getMisActas.and.returnValue(of(mockPageResult));

      component.reintentar();

      expect(component.error()).toBeNull();
    });
  });

  describe('table columns', () => {
    it('should have correct columns defined', () => {
      expect(component.columns.length).toBeGreaterThan(0);
      expect(component.columns[0].key).toBe('numeroActa');
      expect(component.columns[1].key).toBe('jrvNombre');
      expect(component.columns[2].key).toBe('fechaTransmision');
      expect(component.columns[3].key).toBe('estado');
    });

    it('should have appropriate headers for columns', () => {
      expect(component.columns[0].header).toBe('#Acta');
      expect(component.columns[1].header).toBe('JRV');
      expect(component.columns[2].header).toBe('Fecha Transmisión');
      expect(component.columns[3].header).toBe('Estado');
    });
  });

  describe('signals', () => {
    it('should initialize with correct default values', () => {
      expect(component.loading()).toBeTruthy();
      expect(component.error()).toBeNull();
      expect(component.actas().length).toBe(0);
      expect(component.totalItems()).toBe(0);
      expect(component.currentPage()).toBe(0);
      expect(component.pageSize()).toBe(10);
    });
  });
});
