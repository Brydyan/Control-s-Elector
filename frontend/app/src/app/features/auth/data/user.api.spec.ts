import { TestBed } from '@angular/core/testing';
import {
  HttpClientTestingModule,
  HttpTestingController,
} from '@angular/common/http/testing';
import { afterEach, beforeEach, describe, expect, it } from 'vitest';
import { environment } from '../../../../environments/environment';

import {
  UserApi,
  ProfileDto,
  ChangePasswordRequest,
  UpdateEmailRequest,
} from './user.api';

/**
 * User API Tests
 *
 * Tests:
 * - getProfile() HTTP GET call
 * - changePassword() HTTP POST call
 * - updatePersonalEmail() HTTP PUT call
 */
describe('UserApi', () => {
  let service: UserApi;
  let httpMock: HttpTestingController;
  const baseUrl = `${environment.apiUrl}`;

  beforeEach(() => {
    TestBed.configureTestingModule({
      imports: [HttpClientTestingModule],
      providers: [UserApi],
    });
    service = TestBed.inject(UserApi);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    httpMock.verify();
  });

  describe('getProfile', () => {
    it('devuelve el perfil del usuario', () => {
      const mockProfile: ProfileDto = {
        id: '550e8400-e29b-41d4-a716-446655440000',
        nombre: 'Juan Pérez',
        emailControl: 'juanperez@control.ttcs',
        emailPersonal: 'juan@example.com',
        rol: 'COORDINATOR',
        estado: 'ACTIVO',
        lastPasswordChangeAt: '2026-11-01T10:00:00Z',
      };

      service.getProfile().subscribe((profile) => {
        expect(profile).toEqual(mockProfile);
      });

      const req = httpMock.expectOne(`${baseUrl}/user/profile`);
      expect(req.request.method).toBe('GET');
      expect(req.request.withCredentials).toBe(true);
      req.flush(mockProfile);
    });

    it('incluye credenciales en la solicitud', () => {
      service.getProfile().subscribe();

      const req = httpMock.expectOne(`${baseUrl}/user/profile`);
      expect(req.request.withCredentials).toBe(true);
      req.flush({});
    });
  });

  describe('changePassword', () => {
    it('envía solicitud POST con nueva contraseña', () => {
      const request: ChangePasswordRequest = {
        currentPassword: 'OldPass123!',
        newPassword: 'NewPass123!',
        passwordConfirm: 'NewPass123!',
      };

      const response = {
        message: 'Contraseña cambiada exitosamente',
        lastPasswordChangeAt: new Date().toISOString(),
      };

      service.changePassword(request).subscribe((result) => {
        expect(result.message).toBe('Contraseña cambiada exitosamente');
      });

      const req = httpMock.expectOne(`${baseUrl}/auth/change-password`);
      expect(req.request.method).toBe('POST');
      expect(req.request.body).toEqual(request);
      expect(req.request.withCredentials).toBe(true);
      req.flush(response);
    });

    it('incluye credenciales en la solicitud', () => {
      const request: ChangePasswordRequest = {
        currentPassword: 'OldPass123!',
        newPassword: 'NewPass123!',
        passwordConfirm: 'NewPass123!',
      };

      service.changePassword(request).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/auth/change-password`);
      expect(req.request.withCredentials).toBe(true);
      req.flush({});
    });
  });

  describe('updatePersonalEmail', () => {
    it('envía solicitud PUT para actualizar email', () => {
      const request: UpdateEmailRequest = {
        emailPersonal: 'newemail@example.com',
        confirmPassword: 'Password123!',
      };

      const response = {
        message: 'Email actualizado exitosamente',
        emailPersonal: 'newemail@example.com',
      };

      service.updatePersonalEmail(request).subscribe((result) => {
        expect(result.emailPersonal).toBe('newemail@example.com');
      });

      const req = httpMock.expectOne(`${baseUrl}/user/update-personal-email`);
      expect(req.request.method).toBe('PUT');
      expect(req.request.body).toEqual(request);
      expect(req.request.withCredentials).toBe(true);
      req.flush(response);
    });

    it('incluye credenciales en la solicitud', () => {
      const request: UpdateEmailRequest = {
        emailPersonal: 'newemail@example.com',
        confirmPassword: 'Password123!',
      };

      service.updatePersonalEmail(request).subscribe();

      const req = httpMock.expectOne(`${baseUrl}/user/update-personal-email`);
      expect(req.request.withCredentials).toBe(true);
      req.flush({});
    });
  });
});
