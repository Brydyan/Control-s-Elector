import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { providePrimeNG } from 'primeng/config';
import { of } from 'rxjs';
import { AdminService } from '../services/admin.service';
import { AuthStore } from '../../../core/auth/auth.store';
import type { Role } from '../../../core/auth/domain/user.model';
import { AdminSesionesPage } from './admin-sesiones.page';

/**
 * The app bootstraps with the Spanish locale (see src/main.ts). The specs
 * reproduce that configuration so the assertions see the dates the operator
 * actually reads in the field.
 */
registerLocaleData(localeEs);

/** Puts a session of the given role in the in-memory store. */
function loginAs(role: Role): void {
  TestBed.inject(AuthStore).setSession('token', {
    id: 'u-1',
    email: 'user@cne.gob.ec',
    role,
    permissions: [],
  });
}

describe('AdminSesionesPage', () => {
  let page: AdminSesionesPage;
  let fixture: ComponentFixture<AdminSesionesPage>;
  const service = {
    listarSesiones: () =>
      of({
        data: [],
        meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      }),
    revocarSesion: () => of(undefined),
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminSesionesPage],
      providers: [
        { provide: AdminService, useValue: service },
        providePrimeNG(),
      ],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminSesionesPage);
    page = fixture.componentInstance;
    // Renders the template: the PrimeNG components and their bindings are
    // only exercised when the view is actually created.
    fixture.detectChanges();
  });
  it('ngOnInit_cargaSesiones', () => {
    page.ngOnInit();
    expect(page.loading()).toBe(false);
  });
  it('puedeRevocar_soloMaster', () => {
    loginAs('ADMIN');
    expect(page.puedeRevocar()).toBe(false);
    loginAs('MASTER');
    expect(page.puedeRevocar()).toBe(true);
  });
  it('revocar_llamaServicioYRecarga', () => {
    loginAs('MASTER');
    page.revocar({ id: 1, usuarioEmail: 'a' } as never);
    expect(page.sesiones()).toEqual([]);
  });
});

/**
 * Rendering contract of the page: what the operator finds in the DOM.
 *
 * The unit spec above asserts the state; this one asserts the controls that
 * the role and the locale decide, because "the flag is true" and "the button
 * is on screen" are not the same thing.
 */
describe('AdminSesionesPage (rendered contract)', () => {
  let fixture: ComponentFixture<AdminSesionesPage>;
  let page: AdminSesionesPage;

  const sesion = {
    id: 1,
    publicId: 's1',
    usuarioId: 7,
    usuarioEmail: 'a@cne.gob.ec',
    ipCliente: '10.0.0.1',
    userAgent: 'Chrome',
    // Local noon, so the rendered date is the same whatever the timezone of
    // the machine that runs the suite is.
    creadoEn: new Date(2026, 9, 20, 8, 0).toISOString(),
    ultimoUsoEn: new Date(2026, 9, 21, 12, 45).toISOString(),
    expiraEn: new Date(2026, 9, 22, 12, 45).toISOString(),
  };

  const service = {
    listarSesiones: () =>
      of({
        data: [sesion],
        meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      }),
    revocarSesion: () => of(undefined),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminSesionesPage],
      providers: [
        { provide: AdminService, useValue: service },
        { provide: LOCALE_ID, useValue: 'es' },
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminSesionesPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  /** Labels of every button the page renders right now. */
  function buttonLabels(): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll('button') as NodeListOf<
        HTMLButtonElement
      >,
    ).map((button) => (button.textContent ?? '').trim());
  }

  it('muestra el botón Revocar solo con una sesión MASTER', () => {
    loginAs('MASTER');
    fixture.detectChanges();

    expect(buttonLabels()).toContain('Revocar');

    loginAs('ADMIN');
    fixture.detectChanges();

    expect(buttonLabels()).not.toContain('Revocar');
  });

  it('muestra las fechas en formato español dd/MM/yyyy HH:mm', () => {
    const card = fixture.nativeElement.querySelector('.cards article');
    expect(card?.textContent).toContain('21/10/2026 12:45');
  });

  it('no deja interpolaciones rotas en el texto ni en los atributos', () => {
    const text: string = fixture.nativeElement.textContent;
    expect(text).not.toContain('()');

    const brokenAttributes = Array.from(
      fixture.nativeElement.querySelectorAll('*') as NodeListOf<Element>,
    )
      .flatMap((element) =>
        Array.from(element.attributes).map((attribute) => attribute.value),
      )
      .filter((value) => value.includes('()'));

    expect(brokenAttributes).toEqual([]);
  });
});
