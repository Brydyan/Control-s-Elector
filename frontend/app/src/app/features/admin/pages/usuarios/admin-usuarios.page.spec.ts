import { ComponentFixture, TestBed } from '@angular/core/testing';
import { LOCALE_ID } from '@angular/core';
import { registerLocaleData } from '@angular/common';
import localeEs from '@angular/common/locales/es';
import { providePrimeNG } from 'primeng/config';
import { of } from 'rxjs';
import { AdminApi } from '../../data/admin.api';
import { AuthStore } from '../../../auth/services/auth.store';
import type { Role } from '../../../auth/domain/models/user.model';
import { AdminUsuariosPage } from './admin-usuarios.page';

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

describe('AdminUsuariosPage', () => {
  let fixture: ComponentFixture<AdminUsuariosPage>;
  let page: AdminUsuariosPage;
  const service = {
    listarUsuarios: () =>
      of({
        data: [],
        meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
      }),
    crearUsuario: () => of({ passwordTemporal: null }),
    actualizarUsuario: () => of({}),
    deshabilitarUsuario: () => of(undefined),
  };
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsuariosPage],
      providers: [{ provide: AdminApi, useValue: service }, providePrimeNG()],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminUsuariosPage);
    page = fixture.componentInstance;
    // Renders the template: the PrimeNG components and their bindings are
    // only exercised when the view is actually created.
    fixture.detectChanges();
  });
  it('ngOnInit_cargaUsuariosYCargos', () => {
    page.ngOnInit();
    expect(page.loading()).toBe(false);
    expect(page.usuarios()).toEqual([]);
  });
  it('limpiarFiltros_reseteaEstado', () => {
    page.terminoBusqueda = 'x';
    page.rolSeleccionado = 'ADMIN';
    page.habilitadoSeleccionado = 'DESHABILITADOS';
    page.limpiarFiltros();
    expect(page.terminoBusqueda).toBe('');
    expect(page.rolSeleccionado).toBe('');
    expect(page.habilitadoSeleccionado).toBe('TODOS');
  });
  it('puedeAnular_falseSiNoEsMaster', () => {
    loginAs('ADMIN');
    expect(page.puedeAnular({ habilitado: true } as never)).toBe(false);
  });
  it('crear_sinPasswordTemporalEsNull', () => {
    loginAs('MASTER');
    page.abrirCrear();
    page.nombre = 'A';
    page.apellido = 'B';
    page.guardar();
    expect(page.passwordTemporal).toBeNull();
  });
});

/**
 * Rendering contract of the page: what the operator finds in the DOM.
 *
 * The unit spec above asserts the state; this one asserts the controls that
 * the role and the locale decide, because "the flag is true" and "the button
 * is on screen" are not the same thing.
 */
describe('AdminUsuariosPage (rendered contract)', () => {
  let fixture: ComponentFixture<AdminUsuariosPage>;
  let page: AdminUsuariosPage;

  const usuario = {
    publicId: 'u1',
    email: 'a@cne.gob.ec',
    nombre: 'Ana',
    apellido: 'Perez',
    rolNombre: 'ADMIN',
    habilitado: true,
    // Local noon, so the rendered date is the same whatever the timezone of
    // the machine that runs the suite is.
    creadoEn: new Date(2026, 9, 21, 12, 45).toISOString(),
  };

  const service = {
    listarUsuarios: () =>
      of({
        data: [usuario],
        meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      }),
    crearUsuario: () => of({ passwordTemporal: null }),
    actualizarUsuario: () => of({}),
    deshabilitarUsuario: () => of(undefined),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsuariosPage],
      providers: [
        { provide: AdminApi, useValue: service },
        { provide: LOCALE_ID, useValue: 'es' },
        providePrimeNG(),
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsuariosPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  /** Labels of every button the page renders right now. */
  function buttonLabels(): string[] {
    return Array.from(
      fixture.nativeElement.querySelectorAll(
        'button',
      ) as NodeListOf<HTMLButtonElement>,
    ).map((button) => (button.textContent ?? '').trim());
  }

  it('muestra los controles de MASTER solo con una sesión MASTER', () => {
    loginAs('MASTER');
    fixture.detectChanges();

    expect(buttonLabels()).toContain('Crear usuario');
    expect(buttonLabels()).toContain('Editar');
    expect(buttonLabels()).toContain('Deshabilitar');

    loginAs('ADMIN');
    fixture.detectChanges();

    expect(buttonLabels()).not.toContain('Crear usuario');
    expect(buttonLabels()).not.toContain('Editar');
    expect(buttonLabels()).not.toContain('Deshabilitar');
  });

  it('muestra las fechas en formato español dd/MM/yyyy HH:mm', async () => {
    page.verDetalles(usuario);
    fixture.detectChanges();
    await fixture.whenStable();

    const dialog = document.querySelector('.p-dialog');
    expect(dialog?.textContent).toContain('21/10/2026 12:45');
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
