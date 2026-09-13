import { ComponentFixture, TestBed } from '@angular/core/testing';
import { of } from 'rxjs';
import { AdminService } from '../services/admin.service';
import { AdminSesionesPage } from './admin-sesiones.page';

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
      providers: [{ provide: AdminService, useValue: service }],
    }).compileComponents();
    fixture = TestBed.createComponent(AdminSesionesPage);
    page = fixture.componentInstance;
  });
  it('ngOnInit_cargaSesiones', () => {
    page.ngOnInit();
    expect(page.loading).toBe(false);
  });
  it('puedeRevocar_soloMaster', () => {
    page.esMaster = false;
    expect(page.puedeRevocar()).toBe(false);
    page.esMaster = true;
    expect(page.puedeRevocar()).toBe(true);
  });
  it('revocar_llamaServicioYRecarga', () => {
    page.esMaster = true;
    page.revocar({ id: 1, usuarioEmail: 'a' } as never);
    expect(page.sesiones).toEqual([]);
  });
});
