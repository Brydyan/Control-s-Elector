import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EstadoBadgeComponent } from './estado-badge.component';

/**
 * Tests del wrapper de badge de estado (T-FE-12 S1).
 *
 * Variante getter derivando de @Input() estado (Ronda 2 — WARNING 1 fix:
 * test comportamental vía TestBed directo, sin HostComponent anidado).
 */
describe('EstadoBadgeComponent (T-FE-12 S1)', () => {
  let component: EstadoBadgeComponent;
  let fixture: ComponentFixture<EstadoBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EstadoBadgeComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(EstadoBadgeComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should default variant to ENVIADA', () => {
    expect(component.variant).toBe('ENVIADA');
  });

  it('should map @Input() estado to variant (Ronda 2 WARNING 1)', () => {
    component.estado = 'APROBADA';
    expect(component.variant).toBe('APROBADA');

    component.estado = 'EN_DISPUTA';
    expect(component.variant).toBe('EN_DISPUTA');

    component.estado = 'CORREGIDA';
    expect(component.variant).toBe('CORREGIDA');

    component.estado = 'BORRADOR';
    expect(component.variant).toBe('BORRADOR');

    component.estado = 'ANULADA';
    expect(component.variant).toBe('ANULADA');
  });

  it('should render app-badge in DOM with mapped variant', () => {
    component.estado = 'APROBADA';
    fixture.detectChanges();
    const badgeEl = fixture.nativeElement.querySelector('app-badge');
    expect(badgeEl).toBeTruthy();
  });
});