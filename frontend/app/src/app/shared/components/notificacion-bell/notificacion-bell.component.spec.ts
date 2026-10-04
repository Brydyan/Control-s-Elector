import { ComponentFixture, TestBed } from '@angular/core/testing';
import { NotificacionBellComponent } from './notificacion-bell.component';

/**
 * Tests del componente de campana (T-FE-12 S4).
 */
describe('NotificacionBellComponent (T-FE-12 S4)', () => {
  let component: NotificacionBellComponent;
  let fixture: ComponentFixture<NotificacionBellComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificacionBellComponent],
    }).compileComponents();
    fixture = TestBed.createComponent(NotificacionBellComponent);
    component = fixture.componentInstance;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should NOT render badge when count=0 (S4)', () => {
    component.count = 0;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('[data-testid="bell-count"]');
    expect(badge).toBeNull();
  });

  it('should render badge with count=5 (S4)', () => {
    component.count = 5;
    fixture.detectChanges();
    const badge = fixture.nativeElement.querySelector('[data-testid="bell-count"]');
    expect(badge?.textContent).toContain('5');
  });

  it('should emit bellClick on button click', () => {
    let emitted = false;
    component.bellClick.subscribe(() => (emitted = true));
    component.count = 3;
    fixture.detectChanges();
    fixture.nativeElement.querySelector('[data-testid="bell-button"]').click();
    expect(emitted).toBe(true);
  });
});