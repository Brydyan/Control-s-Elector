import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { Message } from 'primeng/message';
import { Textarea } from 'primeng/textarea';
import { ButtonComponent } from '../../../../shared/ui';
import { DisputarActaPayload } from '../../domain/models/coordinator.model';
import { ModalDisputarComponent } from './modal-disputar.component';

describe('ModalDisputarComponent', () => {
  let component: ModalDisputarComponent;
  let fixture: ComponentFixture<ModalDisputarComponent>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ModalDisputarComponent,
        FormsModule,
        Dialog,
        Message,
        Textarea,
        ButtonComponent,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ModalDisputarComponent);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Modal visibility', () => {
    it('should initialize with visible=false', () => {
      expect(component.visible).toBeFalse();
    });

    it('should emit visibleChange when closing', () => {
      spyOn(component.visibleChange, 'emit');
      component.onVisibleChange(false);
      expect(component.visibleChange.emit).toHaveBeenCalledWith(false);
    });

    it('should reset form when modal closes', () => {
      component.motivoDisputa.set('algún motivo');
      component.errorDisputa.set('error');
      component.onVisibleChange(false);
      expect(component.motivoDisputa()).toBe('');
      expect(component.errorDisputa()).toBeNull();
    });
  });

  describe('Form validation', () => {
    it('should require non-empty motivo', () => {
      component.motivoDisputa.set('');
      component.confirmarDisputa();
      expect(component.errorDisputa()).toBe(
        'El motivo de la disputa es obligatorio.',
      );
    });

    it('should trim whitespace from motivo', () => {
      component.motivoDisputa.set('   ');
      component.confirmarDisputa();
      expect(component.errorDisputa()).toBe(
        'El motivo de la disputa es obligatorio.',
      );
    });

    it('should disable confirm button when motivo is empty', () => {
      component.motivoDisputa.set('');
      component.isSubmitting = false;
      expect(component.isDisabledConfirm).toBeTrue();
    });

    it('should disable confirm button when submitting', () => {
      component.motivoDisputa.set('valid motivo');
      component.isSubmitting = true;
      expect(component.isDisabledConfirm).toBeTrue();
    });

    it('should enable confirm button with valid motivo and not submitting', () => {
      component.motivoDisputa.set('valid motivo');
      component.isSubmitting = false;
      expect(component.isDisabledConfirm).toBeFalse();
    });
  });

  describe('Form submission', () => {
    it('should emit confirm event with trimmed motivo', () => {
      spyOn(component.confirm, 'emit');
      component.motivoDisputa.set('  el motivo  ');
      component.confirmarDisputa();
      expect(component.confirm.emit).toHaveBeenCalledWith({
        motivo: 'el motivo',
      } as DisputarActaPayload);
    });

    it('should clear local error when confirming with valid motivo', () => {
      component.errorDisputa.set('previous error');
      component.motivoDisputa.set('valid motivo');
      component.confirmarDisputa();
      expect(component.errorDisputa()).toBeNull();
    });
  });

  describe('Error handling', () => {
    it('should display local error when validation fails', () => {
      component.motivoDisputa.set('');
      component.confirmarDisputa();
      expect(component.errorDisputa()).not.toBeNull();
    });

    it('should accept @Input error from parent', () => {
      component.error = 'API error message';
      fixture.detectChanges();
      expect(component.error).toBe('API error message');
    });
  });
});
