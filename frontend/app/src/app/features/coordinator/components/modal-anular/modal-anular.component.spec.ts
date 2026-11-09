import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ModalAnularComponent } from './modal-anular.component';

describe('ModalAnularComponent', () => {
  let component: ModalAnularComponent;
  let fixture: ComponentFixture<ModalAnularComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [ModalAnularComponent],
    }).compileComponents();

    fixture = TestBed.createComponent(ModalAnularComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should identify motivo as empty when blank', () => {
    component.motivoAnular.set('');
    expect(component.isDisabledConfirm).toBe(true);

    component.motivoAnular.set('   ');
    expect(component.isDisabledConfirm).toBe(true);
  });

  it('should identify motivo as not empty when has content', () => {
    component.motivoAnular.set('Datos fraudulentos');
    component.isSubmitting = false;
    expect(component.isDisabledConfirm).toBe(false);
  });

  it('should emit visibleChange with false when onVisibleChange is called with false', () => {
    spyOn(component.visibleChange, 'emit');

    component.onVisibleChange(false);

    expect(component.visibleChange.emit).toHaveBeenCalledWith(false);
  });

  it('should reset motivo when closing dialog', () => {
    component.motivoAnular.set('Test motivo');

    component.onVisibleChange(false);

    expect(component.motivoAnular()).toBe('');
  });

  it('should not emit confirm when motivo is empty', () => {
    spyOn(component.confirm, 'emit');
    component.motivoAnular.set('');

    component.confirmarAnular();

    expect(component.confirm.emit).not.toHaveBeenCalled();
  });

  it('should emit confirm with trimmed motivo when motivo is valid', () => {
    spyOn(component.confirm, 'emit');
    component.motivoAnular.set('  Datos fraudulentos  ');

    component.confirmarAnular();

    expect(component.confirm.emit).toHaveBeenCalledWith('Datos fraudulentos');
  });

  it('should disable confirm button when isSubmitting is true', () => {
    component.motivoAnular.set('Motivo válido');
    component.isSubmitting = true;

    expect(component.isDisabledConfirm).toBe(true);
  });

  it('should disable confirm button when motivo is empty', () => {
    component.motivoAnular.set('');
    component.isSubmitting = false;

    expect(component.isDisabledConfirm).toBe(true);
  });

  it('should enable confirm button when motivo is valid and not submitting', () => {
    component.motivoAnular.set('Motivo válido');
    component.isSubmitting = false;

    expect(component.isDisabledConfirm).toBe(false);
  });
});
