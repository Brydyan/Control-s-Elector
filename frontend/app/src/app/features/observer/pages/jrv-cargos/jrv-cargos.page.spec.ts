import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ActivatedRoute, Router } from '@angular/router';
import { of } from 'rxjs';
import { JrvCargosPage } from './jrv-cargos.page';
import { ObserverService } from '../../services/observer.service';

describe('JrvCargosPage', () => {
  let component: JrvCargosPage;
  let fixture: ComponentFixture<JrvCargosPage>;
  let observerServiceSpy: jasmine.SpyObj<ObserverService>;
  let routerSpy: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    observerServiceSpy = jasmine.createSpyObj('ObserverService', [
      'getCargosDeJrv',
    ]);
    routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    observerServiceSpy.getCargosDeJrv.and.returnValue(
      of([
        { cargoId: 1, nombre: 'Alcalde', nivel: 'CANTONAL', registrado: false },
        {
          cargoId: 2,
          nombre: 'Prefecto',
          nivel: 'PROVINCIAL',
          registrado: true,
          estadoActa: 'ENVIADA',
        },
      ]),
    );

    await TestBed.configureTestingModule({
      imports: [JrvCargosPage],
      providers: [
        { provide: ObserverService, useValue: observerServiceSpy },
        { provide: Router, useValue: routerSpy },
        {
          provide: ActivatedRoute,
          useValue: {
            snapshot: {
              paramMap: {
                get: (key: string) => (key === 'jrvId' ? '42' : null),
              },
            },
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(JrvCargosPage);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create and load cargos for JRV', () => {
    expect(component).toBeTruthy();
    expect(component.jrvId).toBe(42);
    expect(component.cargos().length).toBe(2);
  });

  it('should navigate to registrar acta', () => {
    component.registrarActa(component.cargos()[0]);
    expect(routerSpy.navigate).toHaveBeenCalledWith([
      '/observer/jrv',
      42,
      'cargo',
      1,
      'registrar',
    ]);
  });
});
