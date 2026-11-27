import { TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { CoordinatorApi } from '../../data/coordinator.api';
import { ActaDetalleModalComponent } from './acta-detalle.modal';

describe('ActaDetalleModalComponent', () => {
  let apiSpy: jasmine.SpyObj<CoordinatorApi>;
  let component: ActaDetalleModalComponent;

  const acta = {
    id: 7,
    jrvNumero: '12',
    cargo: 'Alcalde',
    observer: 'obs',
    estado: 'ENVIADA' as const,
    fechaRegistro: '2026-11-26',
  };

  beforeEach(() => {
    apiSpy = jasmine.createSpyObj('CoordinatorApi', ['obtenerActaDetalle']);
    apiSpy.obtenerActaDetalle.and.returnValue(
      of({
        id: 7,
        urlEvidencia: 'https://ejemplo.com/v1.webp',
        votosBlancos: 3,
      } as never),
    );
    TestBed.configureTestingModule({
      imports: [ActaDetalleModalComponent],
      providers: [{ provide: CoordinatorApi, useValue: apiSpy }],
    });
    component = TestBed.createComponent(
      ActaDetalleModalComponent,
    ).componentInstance;
    component.acta = acta;
    component.abierto = true;
    component.ngOnChanges();
  });

  it('should renew the evidence URL once when the image fails to load', () => {
    apiSpy.obtenerActaDetalle.and.returnValue(
      of({ id: 7, urlEvidencia: 'https://ejemplo.com/v2.webp' } as never),
    );

    component.onEvidenceError();
    component.onEvidenceError();

    expect(apiSpy.obtenerActaDetalle).toHaveBeenCalledTimes(2);
    expect(component.detalle()?.urlEvidencia).toBe(
      'https://ejemplo.com/v2.webp',
    );
    expect(component.detalle()?.votosBlancos).toBe(3);
  });

  it('should allow a new renewal after the modal is closed', () => {
    component.onEvidenceError();
    component.cerrar();
    component.ngOnChanges();
    component.onEvidenceError();

    expect(apiSpy.obtenerActaDetalle).toHaveBeenCalledTimes(4);
  });
});
