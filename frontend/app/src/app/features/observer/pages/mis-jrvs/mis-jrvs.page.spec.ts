import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
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
});
