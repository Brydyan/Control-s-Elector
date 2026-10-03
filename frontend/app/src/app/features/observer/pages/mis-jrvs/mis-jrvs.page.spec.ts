import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { HttpErrorResponse } from '@angular/common/http';
import { of, throwError } from 'rxjs';
import { MisJrvsPage } from './mis-jrvs.page';
import { ObserverService } from '../../services/observer.service';

describe('MisJrvsPage', () => {
  let component: MisJrvsPage;
  let fixture: ComponentFixture<MisJrvsPage>;
  let observerServiceSpy: jasmine.SpyObj<ObserverService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    observerServiceSpy = jasmine.createSpyObj('ObserverService', [
      'getMisJrvs',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    observerServiceSpy.getMisJrvs.and.returnValue(
      of([
        {
          jrvId: 1,
          numero: '101',
          parroquia: 'Colonche',
          cargosPendientes: 2,
          cargosRegistrados: 0,
        },
        {
          jrvId: 2,
          numero: '102',
          parroquia: 'Manglaralto',
          cargosPendientes: 0,
          cargosRegistrados: 2,
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [MisJrvsPage],
      providers: [
        { provide: ObserverService, useValue: observerServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(MisJrvsPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load JRVs', () => {
    expect(component).toBeTruthy();
    expect(component.jrvs.length).toBe(2);
    expect(component.totalPendientes).toBe(1);
    expect(component.totalCompletadas).toBe(1);
  });

  it('should filter JRVs', () => {
    component.applyFilter('PENDIENTES');
    expect(component.filteredJrvs.length).toBe(1);
    expect(component.filteredJrvs[0].jrvId).toBe(1);

    component.applyFilter('COMPLETADAS');
    expect(component.filteredJrvs.length).toBe(1);
    expect(component.filteredJrvs[0].jrvId).toBe(2);
  });

  it('should navigate to jrv cargos on selection', () => {
    component.seleccionarJrv(component.jrvs[0]);
    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/observer/jrv',
      1,
      'cargos',
    ]);
  });

  it('should show error state on HTTP failure and retry', async () => {
    const errorServiceSpy = jasmine.createSpyObj('ObserverService', [
      'getMisJrvs',
    ]);
    const error = new HttpErrorResponse({ status: 500 });
    errorServiceSpy.getMisJrvs.and.returnValue(throwError(() => error));

    await TestBed.resetTestingModule();
    await TestBed.configureTestingModule({
      imports: [MisJrvsPage],
      providers: [
        { provide: ObserverService, useValue: errorServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    const fixture2 = TestBed.createComponent(MisJrvsPage);
    const component2 = fixture2.componentInstance;
    fixture2.detectChanges();

    expect(component2.error).toBeTruthy();
    expect(component2.loading).toBe(false);

    // Retry
    component2.cargarJrvs();
    expect(errorServiceSpy.getMisJrvs).toHaveBeenCalledTimes(2);
  });
});
