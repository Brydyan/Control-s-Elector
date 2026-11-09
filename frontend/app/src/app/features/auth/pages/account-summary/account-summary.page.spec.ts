import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AccountSummaryPage } from './account-summary.page';

/**
 * Resumen de la cuenta activada (ODD #504, B.5.2).
 *
 * <p>La pantalla muestra con que credencial entra la persona. Los datos llegan
 * desde la pantalla de activacion: si no estan, la pantalla lo dice y no
 * inventa nada.
 */
describe('AccountSummaryPage (ODD 504, B.5.2)', () => {
  let fixture: ComponentFixture<AccountSummaryPage>;
  let component: AccountSummaryPage;
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  beforeEach(async () => {
    router = { navigateByUrl: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [AccountSummaryPage],
      providers: [{ provide: Router, useValue: router }],
    }).compileComponents();
  });

  it('muestra el usuario de Control, el correo personal y el rol', () => {
    history.replaceState(
      {
        cuenta: {
          nombre: 'Ana Perez',
          email: 'anaobs@control.ttcs',
          emailPersonal: 'ana@example.com',
          rolNombre: 'OBSERVER',
        },
      },
      '',
    );

    fixture = TestBed.createComponent(AccountSummaryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.cuenta()?.email).toBe('anaobs@control.ttcs');
    expect(component.usuarioCorto()).toBe('anaobs');
    expect(component.rolLegible()).toBe('Observador Electoral');

    const texto = document.body.textContent ?? '';
    expect(texto).toContain('anaobs');
    expect(texto).toContain('anaobs@control.ttcs');
    expect(texto).toContain('ana@example.com');
    expect(texto).toContain('Observador Electoral');
  });

  it('sin datos no inventa nada: lo dice y sigue', () => {
    history.replaceState({}, '');

    fixture = TestBed.createComponent(AccountSummaryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();

    expect(component.cuenta()).toBeNull();
    expect(document.body.textContent ?? '').toContain(
      'Tu contraseña ha sido establecida correctamente',
    );
  });

  it('el botón lleva al login', () => {
    history.replaceState({}, '');

    fixture = TestBed.createComponent(AccountSummaryPage);
    component = fixture.componentInstance;
    fixture.detectChanges();

    component.irAlLogin();

    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
