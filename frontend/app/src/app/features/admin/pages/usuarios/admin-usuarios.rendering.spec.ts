import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { providePrimeNG } from 'primeng/config';
import { AdminUsuariosPage } from './admin-usuarios.page';
import { AdminApi } from '../../data/admin.api';

/**
 * Rendering contract of the page.
 *
 * The unit spec of the component asserts the state; this one asserts what
 * actually reaches the DOM, which is where a broken table or a stuck loading
 * state would show up.
 */
describe('AdminUsuariosPage (rendering)', () => {
  let fixture: ComponentFixture<AdminUsuariosPage>;
  let page: AdminUsuariosPage;

  const service = {
    listarUsuarios: () =>
      of({
        data: [
          {
            publicId: 'u1',
            email: 'a@cne.gob.ec',
            nombre: 'Ana',
            apellido: 'Perez',
            rolNombre: 'ADMIN',
            habilitado: true,
            creadoEn: '2026-01-01T00:00:00Z',
          },
        ],
        meta: { page: 0, size: 20, totalElements: 1, totalPages: 1 },
      }),
    crearUsuario: () => of({}),
    actualizarUsuario: () => of({}),
    deshabilitarUsuario: () => of({}),
  };

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AdminUsuariosPage],
      providers: [{ provide: AdminApi, useValue: service }, providePrimeNG()],
    }).compileComponents();

    fixture = TestBed.createComponent(AdminUsuariosPage);
    page = fixture.componentInstance;
    fixture.detectChanges();
  });

  it('should leave the loading state once the data arrives', () => {
    expect(page.loading()).toBe(false);
    expect(fixture.nativeElement.querySelector('app-loading-state')).toBeNull();
  });

  it('should render one row per user', () => {
    const rows = fixture.nativeElement.querySelectorAll('tbody tr');

    expect(rows.length).toBe(1);
    expect(rows[0].textContent).toContain('Ana Perez');
    expect(rows[0].textContent).toContain('a@cne.gob.ec');
  });

  it('should not show the error state when the load succeeds', () => {
    expect(page.error()).toBeNull();
    expect(fixture.nativeElement.querySelector('app-error-state')).toBeNull();
  });
});
