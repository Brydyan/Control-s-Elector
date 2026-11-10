import { ComponentFixture, TestBed } from '@angular/core/testing';
import { FormsModule } from '@angular/forms';
import { Dialog } from 'primeng/dialog';
import { InputText } from 'primeng/inputtext';
import { Message } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';
import {
  CandidatoResumen,
  CorregirActaPayload,
} from '../../domain/models/coordinator.model';
import { ModalCorregirComponent } from './modal-corregir.component';

describe('ModalCorregirComponent', () => {
  let component: ModalCorregirComponent;
  let fixture: ComponentFixture<ModalCorregirComponent>;

  const mockCandidatos: CandidatoResumen[] = [
    {
      id: 1,
      nombre: 'Candidato A',
      orden: 1,
      cargoId: 1,
      partidoId: 1,
      partidoNombre: 'Partido A',
      partidoSiglas: 'PA',
      partidoNumeroLista: 1,
      votos: 100,
    },
    {
      id: 2,
      nombre: 'Candidato B',
      orden: 2,
      cargoId: 1,
      partidoId: 2,
      partidoNombre: 'Partido B',
      partidoSiglas: 'PB',
      partidoNumeroLista: 2,
      votos: 80,
    },
  ];

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        ModalCorregirComponent,
        FormsModule,
        Dialog,
        InputText,
        Message,
        ButtonComponent,
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ModalCorregirComponent);
    component = fixture.componentInstance;
    component.candidatos = mockCandidatos;
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  describe('Modal visibility', () => {
    it('should initialize with visible=false', () => {
      expect(component.visible).toBeFalsy();
    });

    it('should emit visibleChange when closing', () => {
      spyOn(component.visibleChange, 'emit');
      component.onVisibleChange(false);
      expect(component.visibleChange.emit).toHaveBeenCalledWith(false);
    });

    it('should reset form when modal closes', () => {
      component.correcionSufragantes.set(100);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.errorCorregir.set('error');
      component.onVisibleChange(false);

      expect(component.correcionSufragantes()).toBe(0);
      expect(component.correcionBlancos()).toBe(0);
      expect(component.correcionNulos()).toBe(0);
      expect(component.errorCorregir()).toBeNull();
    });
  });

  describe('Form initialization', () => {
    it('should initialize form with provided data', () => {
      const votesMap = new Map<number, number>([
        [1, 100],
        [2, 80],
      ]);

      component.initializeForm({
        totalSufragantes: 195,
        votosBlancos: 10,
        votosNulos: 5,
        votesPerCandidate: votesMap,
      });

      expect(component.correcionSufragantes()).toBe(195);
      expect(component.correcionBlancos()).toBe(10);
      expect(component.correcionNulos()).toBe(5);
      expect(component.votosCorregidosMap().get(1)).toBe(100);
      expect(component.votosCorregidosMap().get(2)).toBe(80);
    });
  });

  describe('Checksum validation', () => {
    it('should calculate correct sum of all votes', () => {
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      expect(component.sumaVotosCorregidos()).toBe(195);
    });

    it('should calculate correct checksum difference', () => {
      component.correcionSufragantes.set(195);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      expect(component.diferenciaChecksumCorregida()).toBe(0);
    });

    it('should mark checksum as valid when sum equals sufragantes', () => {
      component.correcionSufragantes.set(195);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      expect(component.isChecksumCorregidoValido()).toBeTruthy();
    });

    it('should mark checksum as invalid when sum does not equal sufragantes', () => {
      component.correcionSufragantes.set(200);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      expect(component.isChecksumCorregidoValido()).toBeFalsy();
    });

    it('should mark checksum as invalid when sufragantes is 0', () => {
      component.correcionSufragantes.set(0);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      expect(component.isChecksumCorregidoValido()).toBeFalsy();
    });
  });

  describe('Vote entry', () => {
    it('should update vote for candidate', () => {
      component.votosCorregidosMap.set(new Map<number, number>());
      const mockEvent = {
        target: { value: '150' },
      } as unknown as Event;

      component.setVotoCorregido(1, mockEvent);

      expect(component.votosCorregidosMap().get(1)).toBe(150);
    });

    it('should parse vote as integer', () => {
      component.votosCorregidosMap.set(new Map<number, number>());
      const mockEvent = {
        target: { value: '42.5' },
      } as unknown as Event;

      component.setVotoCorregido(1, mockEvent);

      expect(component.votosCorregidosMap().get(1)).toBe(42);
    });

    it('should default to 0 if vote is invalid', () => {
      component.votosCorregidosMap.set(new Map<number, number>());
      const mockEvent = {
        target: { value: 'invalid' },
      } as unknown as Event;

      component.setVotoCorregido(1, mockEvent);

      expect(component.votosCorregidosMap().get(1)).toBe(0);
    });

    it('should ensure vote is not negative', () => {
      component.votosCorregidosMap.set(new Map<number, number>());
      const mockEvent = {
        target: { value: '-10' },
      } as unknown as Event;

      component.setVotoCorregido(1, mockEvent);

      expect(component.votosCorregidosMap().get(1)).toBe(0);
    });
  });

  describe('Form submission', () => {
    it('should disable confirm button when checksum is invalid', () => {
      component.correcionSufragantes.set(200);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );
      component.isSubmitting = false;

      expect(component.isDisabledConfirm).toBeTruthy();
    });

    /**
     * M6: Skip checksum validation in correction modal.
     * Mutation: Remove checksum validation, always allow confirm.
     * This test kills mutations that would bypass isChecksumCorregidoValido check.
     */
    it('should NEVER emit confirm when checksum is invalid (M6 mutation)', () => {
      spyOn(component.confirm, 'emit');
      component.correcionSufragantes.set(200); // Invalid: sum will be 195, not 200
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      component.confirmarCorrecion();

      // Mutation would allow emit even with invalid checksum
      // Correct behavior: MUST NOT emit, must show error
      expect(component.confirm.emit).not.toHaveBeenCalled();
      expect(component.errorCorregir()).toContain('suma de votos corregidos');
    });

    it('should disable confirm button when submitting', () => {
      component.correcionSufragantes.set(195);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );
      component.isSubmitting = true;

      expect(component.isDisabledConfirm).toBeTruthy();
    });

    it('should emit confirm event with correct payload', () => {
      spyOn(component.confirm, 'emit');
      component.correcionSufragantes.set(195);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      component.confirmarCorrecion();

      expect(component.confirm.emit).toHaveBeenCalled();
      const emittedPayload = (component.confirm.emit as any).mock
        .lastCall[0] as CorregirActaPayload;
      expect(emittedPayload.totalSufragantes).toBe(195);
      expect(emittedPayload.votosBlancos).toBe(10);
      expect(emittedPayload.votosNulos).toBe(5);
      expect(emittedPayload.detalles.length).toBe(2);
    });

    it('should not emit if checksum is invalid', () => {
      spyOn(component.confirm, 'emit');
      component.correcionSufragantes.set(200);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      component.confirmarCorrecion();

      expect(component.confirm.emit).not.toHaveBeenCalled();
      expect(component.errorCorregir()).not.toBeNull();
    });
  });

  describe('Error handling', () => {
    it('should display validation error when checksum is invalid', () => {
      component.correcionSufragantes.set(200);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );

      component.confirmarCorrecion();

      expect(component.errorCorregir()).not.toBeNull();
    });

    it('should accept @Input error from parent', () => {
      component.error = 'API error message';
      fixture.detectChanges();
      expect(component.error).toBe('API error message');
    });

    it('should clear error on successful submission', () => {
      component.correcionSufragantes.set(195);
      component.correcionBlancos.set(10);
      component.correcionNulos.set(5);
      component.votosCorregidosMap.set(
        new Map<number, number>([
          [1, 100],
          [2, 80],
        ]),
      );
      component.errorCorregir.set('previous error');

      component.confirmarCorrecion();

      expect(component.errorCorregir()).toBeNull();
    });
  });
});
