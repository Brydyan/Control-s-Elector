import { ComponentFixture, TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { ErrorStateComponent } from './error-state.component';

describe('ErrorStateComponent', () => {
  let component: ErrorStateComponent;
  let fixture: ComponentFixture<ErrorStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorStateComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(ErrorStateComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should render default title', () => {
    const titleEl = fixture.nativeElement.querySelector('.error-state__title');
    expect(titleEl.textContent).toContain('Ocurrió un error');
  });

  it('should emit retry when retry button is clicked', () => {
    spyOn(component.retry, 'emit');
    fixture.componentRef.setInput('showRetry', true);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.p-button');
    button.click();

    expect(component.retry.emit).toHaveBeenCalled();
  });

  /* T8.S14: Configurable heading level for semantic HTML */

  it('should render h2 heading by default', () => {
    fixture.detectChanges();
    const h2 = fixture.nativeElement.querySelector('h2.error-state__title');
    expect(h2).toBeTruthy();
  });

  it('should render h1 heading when headingLevel is "h1"', () => {
    fixture.componentRef.setInput('headingLevel', 'h1');
    fixture.detectChanges();
    const h1 = fixture.nativeElement.querySelector('h1.error-state__title');
    expect(h1).toBeTruthy();
    expect(h1.textContent).toContain('Ocurrió un error');
  });

  it('should render h3 heading when headingLevel is "h3"', () => {
    fixture.componentRef.setInput('headingLevel', 'h3');
    fixture.detectChanges();
    const h3 = fixture.nativeElement.querySelector('h3.error-state__title');
    expect(h3).toBeTruthy();
  });

  it('should have role="alert" for accessibility', () => {
    const alertEl = fixture.nativeElement.querySelector('[role="alert"]');
    expect(alertEl).toBeTruthy();
  });
});
