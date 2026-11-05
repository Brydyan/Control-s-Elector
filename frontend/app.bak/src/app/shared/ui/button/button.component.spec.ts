import { ComponentFixture, TestBed } from '@angular/core/testing';
import { providePrimeNG } from 'primeng/config';
import { ButtonComponent } from './button.component';

describe('ButtonComponent', () => {
  let component: ButtonComponent;
  let fixture: ComponentFixture<ButtonComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ButtonComponent],
      providers: [providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(ButtonComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit clicked event when not disabled or loading', () => {
    spyOn(component.clicked, 'emit');
    const event = new MouseEvent('click');
    component.onClick(event);
    expect(component.clicked.emit).toHaveBeenCalledWith(event);
  });

  it('should not emit clicked event when disabled', () => {
    spyOn(component.clicked, 'emit');
    component.disabled = true;
    const event = new MouseEvent('click');
    component.onClick(event);
    expect(component.clicked.emit).not.toHaveBeenCalled();
  });

  it('should not emit clicked event when loading', () => {
    spyOn(component.clicked, 'emit');
    component.loading = true;
    const event = new MouseEvent('click');
    component.onClick(event);
    expect(component.clicked.emit).not.toHaveBeenCalled();
  });

  it('should render the primary variant without a severity', () => {
    fixture.componentRef.setInput('variant', 'primary');
    fixture.detectChanges();

    expect(component.severity).toBeUndefined();
    const button = fixture.nativeElement.querySelector('.p-button');
    expect(button.classList).not.toContain('p-button-text');
    expect(button.classList).not.toContain('p-button-outlined');
  });

  it('should render the danger variant with the danger severity', () => {
    fixture.componentRef.setInput('variant', 'danger');
    fixture.detectChanges();

    expect(component.severity).toBe('danger');
  });

  it('should render the secondary variant as an outlined button', () => {
    fixture.componentRef.setInput('variant', 'secondary');
    fixture.detectChanges();

    expect(component.severity).toBe('secondary');
    expect(component.isSecondary).toBe(true);

    const button = fixture.nativeElement.querySelector('.p-button');
    expect(button.classList).toContain('p-button-outlined');
  });

  it('should render the ghost variant as a text button', () => {
    fixture.componentRef.setInput('variant', 'ghost');
    fixture.detectChanges();

    expect(component.isGhost).toBe(true);

    const button = fixture.nativeElement.querySelector('.p-button');
    expect(button.classList).toContain('p-button-text');
  });

  it('should map the sizes to the PrimeNG ones', () => {
    fixture.componentRef.setInput('size', 'sm');
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('.p-button').classList,
    ).toContain('p-button-sm');

    fixture.componentRef.setInput('size', 'lg');
    fixture.detectChanges();
    expect(
      fixture.nativeElement.querySelector('.p-button').classList,
    ).toContain('p-button-lg');
  });

  it('should pass icon and rounded inputs to the rendered PrimeNG button', () => {
    fixture.componentRef.setInput('icon', 'pi pi-ellipsis-v');
    fixture.componentRef.setInput('rounded', true);
    fixture.detectChanges();

    const button = fixture.nativeElement.querySelector('button');
    expect(button.classList).toContain('p-button-rounded');
    const iconEl = fixture.nativeElement.querySelector('.pi.pi-ellipsis-v');
    expect(iconEl).toBeTruthy();
  });
});
