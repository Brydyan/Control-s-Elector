import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ErrorStateComponent } from './error-state.component';

describe('ErrorStateComponent', () => {
  let component: ErrorStateComponent;
  let fixture: ComponentFixture<ErrorStateComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ErrorStateComponent],
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
    component.showRetry = true;
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('.error-state__retry-btn');
    button.click();

    expect(component.retry.emit).toHaveBeenCalled();
  });
});
