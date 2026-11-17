import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HttpErrorResponse } from '@angular/common/http';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { Subject, of, throwError } from 'rxjs';

import { AuthStore } from '../../../auth/services/auth.store';
import { TermsAcceptanceService } from '../../services/terms-acceptance.service';
import { TermsAcceptanceModalComponent } from './terms-acceptance-modal.component';

/**
 * Tests del modal de aceptacion de terminos (T-LEGAL-01.7).
 *
 * <p>El modal es la pantalla que el usuario no puede saltarse, asi que lo que importa son sus
 * salidas: aceptar (registra y sigue) y cerrar (cierra la sesion). No hay una tercera.
 */
describe('TermsAcceptanceModalComponent', () => {
  let fixture: ComponentFixture<TermsAcceptanceModalComponent>;
  let component: TermsAcceptanceModalComponent;
  let terms: { aceptar: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    terms = { aceptar: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [TermsAcceptanceModalComponent],
      providers: [
        AuthStore,
        { provide: TermsAcceptanceService, useValue: terms },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(TermsAcceptanceModalComponent);
    component = fixture.componentInstance;
  });

  it('no tiene boton de rechazo: la unica accion es aceptar', () => {
    fixture.detectChanges();

    // El dialog de PrimeNG se monta en un overlay del body, no dentro del
    // componente: hay que buscarlo ahi, no en el fixture.
    const texto = document.body.textContent as string;

    expect(texto).toContain('Acepto');
    expect(texto).not.toContain('Rechazar');
    expect(texto).not.toContain('Declinar');
  });

  it('enlaza a los terminos y a la politica de privacidad', () => {
    fixture.detectChanges();

    const enlaces = Array.from(
      fixture.nativeElement.querySelectorAll(
        'a',
      ) as NodeListOf<HTMLAnchorElement>,
    ).map((a) => a.getAttribute('href'));

    expect(enlaces).toContain('/terms');
    expect(enlaces).toContain('/privacy');
  });

  it('al aceptar se avisa al padre y se apaga el estado de envio', () => {
    const aceptado = vi.fn();
    component.accepted.subscribe(aceptado);
    terms.aceptar.mockReturnValue(of('1.0'));

    component.onAceptar();

    expect(terms.aceptar).toHaveBeenCalledOnce();
    expect(aceptado).toHaveBeenCalledOnce();
    expect(component.enviando).toBe(false);
  });

  it('si el backend rechaza, el modal sigue abierto con el error a la vista', () => {
    const aceptado = vi.fn();
    component.accepted.subscribe(aceptado);
    terms.aceptar.mockReturnValue(
      throwError(() => new HttpErrorResponse({ status: 500 })),
    );

    component.onAceptar();

    expect(aceptado).not.toHaveBeenCalled();
    expect(component.errorMessage).toBeTruthy();
    expect(component.enviando).toBe(false);
  });

  it('no se mandan dos aceptaciones mientras la primera esta en vuelo', () => {
    // Un Subject que no emite deja la aceptacion "en vuelo": es el caso que
    // el boton tiene que bloquear (doble clic).
    const enVuelo = new Subject<string>();
    terms.aceptar.mockReturnValue(enVuelo.asObservable());

    component.onAceptar();
    component.onAceptar();

    expect(terms.aceptar).toHaveBeenCalledOnce();
    expect(component.enviando).toBe(true);
  });

  it('cerrar el modal avisa al padre para que cierre la sesion', () => {
    const cerrado = vi.fn();
    component.closed.subscribe(cerrado);

    component.onVisibleChange(false);

    expect(cerrado).toHaveBeenCalledOnce();
  });
});
