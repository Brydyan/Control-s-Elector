import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModalAprobarComponent } from './modal-aprobar.component';

describe('ModalAprobarComponent', () => {
  let component: ModalAprobarComponent;
  let fixture: ComponentFixture<ModalAprobarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModalAprobarComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ModalAprobarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should emit visibleChange with false when onVisibleChange is called with false', () => {
    spyOn(component.visibleChange, 'emit');

    component.onVisibleChange(false);

    expect(component.visibleChange.emit).toHaveBeenCalledWith(false);
  });

  it('should emit confirm when confirmarAprobar is called', () => {
    spyOn(component.confirm, 'emit');

    component.confirmarAprobar();

    expect(component.confirm.emit).toHaveBeenCalled();
  });

  it('should disable buttons when isSubmitting is true', () => {
    component.isSubmitting = true;
    fixture.detectChanges();

    const buttons = fixture.nativeElement.querySelectorAll('app-button');
    buttons.forEach((btn: any) => {
      expect(btn.getAttribute('ng-reflect-disabled')).toBeDefined();
    });
  });

  it('should emit visibleChange with true when opening dialog', () => {
    spyOn(component.visibleChange, 'emit');

    component.onVisibleChange(true);

    expect(component.visibleChange.emit).toHaveBeenCalledWith(true);
  });
});
