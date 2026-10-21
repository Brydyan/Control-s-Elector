import { ComponentFixture, TestBed } from '@angular/core/testing';
import { EmptyStateComponent } from './empty-state.component';

describe('EmptyStateComponent', () => {
  let component: EmptyStateComponent;
  let fixture: ComponentFixture<EmptyStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmptyStateComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(EmptyStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should translate the Ionic icon name to a PrimeIcons class', () => {
    fixture.componentRef.setInput('icon', 'document-text-outline');
    fixture.detectChanges();

    const icon = fixture.nativeElement.querySelector('.empty-state__icon');
    expect(icon.classList).toContain('pi');
    expect(icon.classList).toContain('pi-file');
  });

  it('should render default title', () => {
    const titleEl = fixture.nativeElement.querySelector('.empty-state__title');
    expect(titleEl.textContent).toContain('No hay datos');
  });

  it('should render custom title and description', () => {
    fixture.componentRef.setInput('title', 'Sin registros');
    fixture.componentRef.setInput(
      'description',
      'No se encontraron actas pendientes',
    );
    fixture.detectChanges();

    const titleEl = fixture.nativeElement.querySelector('.empty-state__title');
    const descEl = fixture.nativeElement.querySelector(
      '.empty-state__description',
    );

    expect(titleEl.textContent).toContain('Sin registros');
    expect(descEl.textContent).toContain('No se encontraron actas pendientes');
  });
});
