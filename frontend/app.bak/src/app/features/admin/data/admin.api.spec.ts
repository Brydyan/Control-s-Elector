import { provideHttpClient } from '@angular/common/http';
import {
  provideHttpClientTesting,
  HttpTestingController,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { environment } from '../../../../environments/environment';
import { AdminApi } from './admin.api';

describe('AdminApi', () => {
  let service: AdminApi;
  let http: HttpTestingController;
  const paged = {
    data: [],
    meta: { page: 0, size: 20, totalElements: 0, totalPages: 0 },
  };
  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [AdminApi, provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AdminApi);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());
  it('listarUsuarios_returnsPaged', () => {
    service.listarUsuarios().subscribe((r) => expect(r.meta).toBeTruthy());
    const req = http.expectOne(
      `${environment.apiUrl}/admin/usuarios?page=0&size=20`,
    );
    expect(req.request.method).toBe('GET');
    req.flush(paged);
  });
  it('listarSesiones_returnsPaged', () => {
    service.listarSesiones().subscribe((r) => expect(r.data).toEqual([]));
    const req = http.expectOne(
      `${environment.apiUrl}/admin/sesiones?page=0&size=20`,
    );
    req.flush(paged);
  });
  it('crearUsuario_returnsUsuarioConPasswordTemporal', () => {
    const value = {
      publicId: 'u',
      email: 'a@b',
      nombre: 'A',
      apellido: 'B',
      rolNombre: 'OBSERVER',
      habilitado: true,
      creadoEn: '',
      passwordTemporal: 'tmp',
    };
    service
      .crearUsuario({ nombre: 'A', apellido: 'B', rol: 'OBSERVER' })
      .subscribe((r) => expect(r.passwordTemporal).toBe('tmp'));
    const req = http.expectOne(`${environment.apiUrl}/admin/usuarios`);
    expect(req.request.method).toBe('POST');
    req.flush(value);
  });
  it('actualizarUsuario_returnsUsuarioActualizado', () => {
    service
      .actualizarUsuario('u', { nombre: 'A', apellido: 'B', rol: 'ADMIN' })
      .subscribe((r) => expect(r.rolNombre).toBe('ADMIN'));
    const req = http.expectOne(`${environment.apiUrl}/admin/usuarios/u`);
    expect(req.request.method).toBe('PUT');
    req.flush({ rolNombre: 'ADMIN' });
  });
  it('deshabilitarUsuario_callsDelete', () => {
    service.deshabilitarUsuario('u').subscribe();
    const req = http.expectOne(`${environment.apiUrl}/admin/usuarios/u`);
    expect(req.request.method).toBe('DELETE');
    req.flush('');
  });
  it('revocarSesion_callsDelete', () => {
    service.revocarSesion(7).subscribe();
    const req = http.expectOne(`${environment.apiUrl}/admin/sesiones/7`);
    expect(req.request.method).toBe('DELETE');
    req.flush('');
  });
});
