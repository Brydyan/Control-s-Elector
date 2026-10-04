import { ComponentFixture, TestBed } from '@angular/core/testing';
import { RolBadgeComponent } from './rol-badge.component';

/**
 * Tests del wrapper de badge de rol (T-FE-12 S2, CRITICAL 3 — Ronda 2).
 *
 * Cubre el mapeo de cada rol a la variante BadgeVariant correspondiente.
 */
describe('RolBadgeComponent (T-FE-12 S2)', () => {
  let component: RolBadgeComponent;
  let fixture: ComponentFixture<RolBadgeComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RolBadgeComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(RolBadgeComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should map COORDINATOR to CORREGIDA variant', () => {
    component.rol = 'COORDINATOR';
    expect(component.variant).toBe('CORREGIDA');
  });

  it('should map MASTER to APROBADA variant', () => {
    component.rol = 'MASTER';
    expect(component.variant).toBe('APROBADA');
  });

  it('should map ADMIN to ENVIADA variant', () => {
    component.rol = 'ADMIN';
    expect(component.variant).toBe('ENVIADA');
  });

  it('should map OBSERVER to default variant', () => {
    component.rol = 'OBSERVER';
    expect(component.variant).toBe('default');
  });
});