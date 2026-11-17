import { ComponentFixture, TestBed } from '@angular/core/testing';
import { ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse, HttpHeaders } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';

import { LegalService } from '../../services/legal.service';
import { ContactDpoPage } from './contact-dpo.page';

/**
 * Tests del formulario de contacto con el DPO (T-LEGAL-01 / spec dpo-contact).
 *
 * <p>El formulario es publico y el canal de ejercicio de derechos: si valida mal, el usuario
 * pierde el derecho a pedir la supresion de su cuenta. Por eso el test cubre las dos mitades:
 * que NO se mande lo que esta mal (validacion del cliente) y que lo que llega bien se mande
 * (una sola peticion).
 */
describe('ContactDpoPage', () => {
  let fixture: ComponentFixture<ContactDpoPage>;
  let component: ContactDpoPage;
  let legal: { contactoDpo: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    legal = { contactoDpo: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ContactDpoPage, ReactiveFormsModule],
      providers: [{ provide: LegalService, useValue: legal }],
    }).compileComponents();

    fixture = TestBed.createComponent(ContactDpoPage);
    component = fixture.componentInstance;
  });

  it('un formulario vacio no se manda y muestra los errores', () => {
    component.onEnviar();

    expect(legal.contactoDpo).not.toHaveBeenCalled();
    expect(component.errorDe('name')).toBe('El nombre es requerido');
    expect(component.errorDe('email')).toBe('El email es requerido');
    expect(component.errorDe('subject')).toBe('El asunto es requerido');
    expect(component.errorDe('message')).toBe('El mensaje es requerido');
  });

  it('un email invalido no se manda', () => {
    component.form.setValue({
      name: 'Juan García',
      email: 'no-es-un-email',
      subject: 'Consulta',
      message: 'Mensaje',
    });
    component.form.controls.email.markAsTouched();

    component.onEnviar();

    expect(legal.contactoDpo).not.toHaveBeenCalled();
    expect(component.errorDe('email')).toBe('El email debe ser válido');
  });

  it('un formulario valido se manda una sola vez', () => {
    legal.contactoDpo.mockReturnValue(
      of({
        status: 'received',
        message: 'Mensaje recibido. DPO responderá en 5 días hábiles.',
      }),
    );
    component.form.setValue({
      name: 'Juan García',
      email: 'juan@example.com',
      subject: 'Ejercer derecho al olvido',
      message: 'Quiero borrar mi cuenta.',
    });

    component.onEnviar();

    expect(legal.contactoDpo).toHaveBeenCalledOnce();
    expect(legal.contactoDpo).toHaveBeenCalledWith({
      name: 'Juan García',
      email: 'juan@example.com',
      subject: 'Ejercer derecho al olvido',
      message: 'Quiero borrar mi cuenta.',
    });
    expect(component.enviado()).toBe(true);
    expect(component.confirmationMessage()).toContain('DPO');
  });

  it('un 429 muestra el tiempo que falta para reintentar', () => {
    legal.contactoDpo.mockReturnValue(
      throwError(
        () =>
          new HttpErrorResponse({
            status: 429,
            headers: new HttpHeaders({ 'Retry-After': '15' }),
          }),
      ),
    );
    component.form.setValue({
      name: 'Juan García',
      email: 'juan@example.com',
      subject: 'Consulta',
      message: 'Mensaje',
    });

    component.onEnviar();

    expect(component.enviado()).toBe(false);
    expect(component.errorMessage()).toContain('15');
  });
});
