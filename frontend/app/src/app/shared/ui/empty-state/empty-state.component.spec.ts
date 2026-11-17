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
    fixture.componentRef.setInput('icon', 'document');
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

  /* T8.S14: Configurable heading level for semantic HTML */

  it('should render h2 heading by default', () => {
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2.empty-state__title');
    expect(h2).toBeTruthy();
  });

  it('should render h1 heading when headingLevel is "h1"', () => {
    fixture.componentRef.setInput('title', 'Empty title');
    fixture.componentRef.setInput('headingLevel', 'h1');
    fixture.detectChanges();
    const h1 = fixture.nativeElement.querySelector('h1.empty-state__title');
    expect(h1).toBeTruthy();
  });

  it('should render h3 heading when headingLevel is "h3"', () => {
    fixture.componentRef.setInput('headingLevel', 'h3');
    fixture.detectChanges();
    const h3 = fixture.nativeElement.querySelector('h3.empty-state__title');
    expect(h3).toBeTruthy();
  });
});
