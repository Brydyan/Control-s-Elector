import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminService } from '../services/admin.service';
import { AdminUsuariosPage } from './admin-usuarios.page';

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
      providers: [{ provide: AdminService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminUsuariosPage);
    page = fixture.componentInstance;
  });
  it('ngOnInit_cargaUsuariosYCargos', () => {
    page.ngOnInit();
    expect(page.loading).toBe(false);
    expect(page.usuarios).toEqual([]);
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
    page.esMaster = false;
    expect(page.puedeAnular({ habilitado: true } as never)).toBe(false);
  });
  it('crear_sinPasswordTemporalEsNull', () => {
    page.esMaster = true;
    page.abrirCrear();
    page.nombre = 'A';
    page.apellido = 'B';
    page.guardar();
    expect(page.passwordTemporal).toBeNull();
  });
});
