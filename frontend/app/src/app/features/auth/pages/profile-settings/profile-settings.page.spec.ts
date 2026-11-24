import { ComponentFixture, TestBed } from '@angular/core/testing';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { of, throwError } from 'rxjs';
import { ActivatedRoute } from '@angular/router';
import { MessageService } from 'primeng/api';

import { ProfileSettingsPage } from './profile-settings.page';
import { UserApi, ProfileDto } from '../../data/user.api';

/**
 * User Profile & Settings Page Tests (SDD Phase D)
 *
 * Tests:
 * - Load profile → display immutable fields
 * - Form validation: empty → submit disabled
 * - Submit change-password → API called
 * - API error → show error message
 * - API success → show success + clear form
 * - Submit email update → API called
 * - Password requirements feedback (real-time)
 */
describe('ProfileSettingsPage (SDD Phase D)', () => {
  let fixture: ComponentFixture<ProfileSettingsPage>;
  let component: ProfileSettingsPage;
  let userApi: {
    getProfile: ReturnType<typeof vi.fn>;
    changePassword: ReturnType<typeof vi.fn>;
    updatePersonalEmail: ReturnType<typeof vi.fn>;
  };
  let messageService: { add: ReturnType<typeof vi.fn> };

  const mockProfile: ProfileDto = {
    id: '550e8400-e29b-41d4-a716-446655440000',
    nombre: 'Juan Pérez',
    emailControl: 'juanperez@control.ttcs',
    emailPersonal: 'juan@example.com',
    rol: 'COORDINATOR',
    estado: 'ACTIVO',
    lastPasswordChangeAt: '2026-11-01T10:00:00Z',
  };

  beforeEach(async () => {
    userApi = {
      getProfile: vi.fn(),
      changePassword: vi.fn(),
      updatePersonalEmail: vi.fn(),
    };
    messageService = { add: vi.fn() };

    await TestBed.configureTestingModule({
      imports: [ProfileSettingsPage],
      providers: [
        { provide: UserApi, useValue: userApi },
        { provide: MessageService, useValue: messageService },
        {
          provide: ActivatedRoute,
          useValue: {
            queryParams: of({}),
          },
        },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(ProfileSettingsPage);
    component = fixture.componentInstance;
  });

  describe('Profile Loading', () => {
    it('carga el perfil al iniciar', () => {
      userApi.getProfile.mockReturnValue(of(mockProfile));

      component.ngOnInit();

      expect(userApi.getProfile).toHaveBeenCalled();
      expect(component.profile()).toEqual(mockProfile);
      expect(component.profileLoading()).toBe(false);
    });

    it('muestra error si falla la carga del perfil', () => {
      const error = { error: { message: 'No se pudo cargar el perfil' } };
      userApi.getProfile.mockReturnValue(throwError(() => error));

      component.ngOnInit();

      expect(component.profileError()).toBe('No se pudo cargar el perfil');
      expect(component.profileLoading()).toBe(false);
      expect(messageService.add).toHaveBeenCalled();
    });

    it('muestra los datos del perfil en la template', () => {
      userApi.getProfile.mockReturnValue(of(mockProfile));
      component.ngOnInit();
      fixture.detectChanges();

      const text = document.body.textContent ?? '';
      expect(text).toContain('Juan Pérez');
      expect(text).toContain('juanperez@control.ttcs');
      expect(text).toContain('juan@example.com');
    });
  });

  describe('Password Validation', () => {
    it('sin password nueva: submit deshabilitado', () => {
      component.currentPassword.set('OldPass123!');
      component.newPassword.set('');
      component.newPasswordConfirm.set('');

      expect(component.changePasswordDisabled()).toBe(true);
    });

    it('valida requisito mínimo de 8 caracteres', () => {
      component.newPassword.set('Short1!');
      expect(component.passwordRequirements().minLength).toBe(false);

      component.newPassword.set('LongPass1!');
      expect(component.passwordRequirements().minLength).toBe(true);
    });

    it('valida requisito de mayúscula', () => {
      component.newPassword.set('password123!');
      expect(component.passwordRequirements().hasUppercase).toBe(false);

      component.newPassword.set('Password123!');
      expect(component.passwordRequirements().hasUppercase).toBe(true);
    });

    it('valida requisito de minúscula', () => {
      component.newPassword.set('PASSWORD123!');
      expect(component.passwordRequirements().hasLowercase).toBe(false);

      component.newPassword.set('PASSWORD123a!');
      expect(component.passwordRequirements().hasLowercase).toBe(true);
    });

    it('valida requisito de número', () => {
      component.newPassword.set('Password!');
      expect(component.passwordRequirements().hasNumber).toBe(false);

      component.newPassword.set('Password1!');
      expect(component.passwordRequirements().hasNumber).toBe(true);
    });

    it('valida requisito de carácter especial', () => {
      component.newPassword.set('Password123');
      expect(component.passwordRequirements().hasSpecial).toBe(false);

      component.newPassword.set('Password123!');
      expect(component.passwordRequirements().hasSpecial).toBe(true);
    });

    it('todas las validaciones pasan con contraseña válida', () => {
      component.newPassword.set('ValidPass123!');

      expect(component.allPasswordRequirementsMet()).toBe(true);
    });

    it('valida que las contraseñas coincidan', () => {
      component.newPassword.set('ValidPass123!');
      component.newPasswordConfirm.set('DifferentPass123!');

      expect(component.passwordsMatch()).toBe(false);

      component.newPasswordConfirm.set('ValidPass123!');

      expect(component.passwordsMatch()).toBe(true);
    });
  });

  describe('Change Password Form', () => {
    it('submit deshabilitado cuando faltan campos', () => {
      component.currentPassword.set('OldPass123!');
      component.newPassword.set('ValidPass123!');
      component.newPasswordConfirm.set('ValidPass123!');

      // Sin contraseña actual
      component.currentPassword.set('');
      expect(component.changePasswordDisabled()).toBe(true);

      // Sin nueva contraseña válida
      component.currentPassword.set('OldPass123!');
      component.newPassword.set('Short1!');
      expect(component.changePasswordDisabled()).toBe(true);
    });

    it('submit exitoso cambia la contraseña', () => {
      userApi.changePassword.mockReturnValue(
        of({
          message: 'Contraseña cambiada exitosamente',
          lastPasswordChangeAt: new Date().toISOString(),
        }),
      );

      component.currentPassword.set('OldPass123!');
      component.newPassword.set('NewPass123!');
      component.newPasswordConfirm.set('NewPass123!');

      component.submitChangePassword();

      expect(userApi.changePassword).toHaveBeenCalledWith({
        currentPassword: 'OldPass123!',
        newPassword: 'NewPass123!',
        passwordConfirm: 'NewPass123!',
      });
      expect(component.changePasswordSuccess()).toBe(true);
      expect(component.currentPassword()).toBe('');
      expect(component.newPassword()).toBe('');
      expect(messageService.add).toHaveBeenCalled();
    });

    it('muestra error si API falla', () => {
      const error = { error: { message: 'Contraseña actual incorrecta' } };
      userApi.changePassword.mockReturnValue(throwError(() => error));

      component.currentPassword.set('OldPass123!');
      component.newPassword.set('NewPass123!');
      component.newPasswordConfirm.set('NewPass123!');

      component.submitChangePassword();

      expect(component.changePasswordError()).toBe(
        'Contraseña actual incorrecta',
      );
      expect(messageService.add).toHaveBeenCalled();
    });

    it('valida que las contraseñas coincidan antes de enviar', () => {
      component.currentPassword.set('OldPass123!');
      component.newPassword.set('NewPass123!');
      component.newPasswordConfirm.set('DifferentPass123!');

      component.submitChangePassword();

      expect(component.changePasswordError()).toBe(
        'Las contraseñas no coinciden',
      );
      expect(userApi.changePassword).not.toHaveBeenCalled();
    });
  });

  describe('Email Validation', () => {
    it('valida formato de email', () => {
      component.personalEmail.set('invalid-email');
      expect(component.emailFormatValid()).toBe(false);

      component.personalEmail.set('valid@email.com');
      expect(component.emailFormatValid()).toBe(true);
    });

    it('submit deshabilitado con email inválido', () => {
      component.personalEmail.set('invalid');
      component.emailConfirmPassword.set('Password123!');

      expect(component.updateEmailDisabled()).toBe(true);
    });

    it('submit deshabilitado sin contraseña de confirmación', () => {
      component.personalEmail.set('valid@email.com');
      component.emailConfirmPassword.set('');

      expect(component.updateEmailDisabled()).toBe(true);
    });
  });

  describe('Update Email Form', () => {
    it('submit exitoso actualiza el email', () => {
      userApi.updatePersonalEmail.mockReturnValue(
        of({
          message: 'Email actualizado exitosamente',
          emailPersonal: 'newemail@example.com',
        }),
      );
      userApi.getProfile.mockReturnValue(of(mockProfile));

      component.personalEmail.set('newemail@example.com');
      component.emailConfirmPassword.set('Password123!');

      component.submitUpdateEmail();

      expect(userApi.updatePersonalEmail).toHaveBeenCalledWith({
        emailPersonal: 'newemail@example.com',
        confirmPassword: 'Password123!',
      });
      expect(component.emailSuccess()).toBe(true);
      expect(component.emailConfirmPassword()).toBe('');
      expect(messageService.add).toHaveBeenCalled();
    });

    it('muestra error si API falla', () => {
      const error = { error: { message: 'Email duplicado' } };
      userApi.updatePersonalEmail.mockReturnValue(throwError(() => error));

      component.personalEmail.set('duplicate@email.com');
      component.emailConfirmPassword.set('Password123!');

      component.submitUpdateEmail();

      expect(component.emailError()).toBe('Email duplicado');
      expect(messageService.add).toHaveBeenCalled();
    });

    it('con 429 muestra el tiempo de espera que manda el backend', () => {
      const error = {
        status: 429,
        error: {
          status: 429,
          error: 'Too Many Requests',
          message: 'Demasiadas solicitudes. Reintente en 840 segundos.',
        },
      };
      userApi.updatePersonalEmail.mockReturnValue(throwError(() => error));

      component.personalEmail.set('nuevo@example.com');
      component.emailConfirmPassword.set('Password123!');

      component.submitUpdateEmail();

      expect(component.emailError()).toBe(
        'Demasiadas solicitudes. Reintente en 840 segundos.',
      );
      expect(component.emailLoading()).toBe(false);
    });

    it('valida formato de email antes de enviar', () => {
      component.personalEmail.set('invalid-email');
      component.emailConfirmPassword.set('Password123!');

      component.submitUpdateEmail();

      expect(component.emailError()).toBe('El formato del email no es válido');
      expect(userApi.updatePersonalEmail).not.toHaveBeenCalled();
    });
  });

  describe('Password Visibility', () => {
    it('toggle contraseña actual visibility', () => {
      expect(component.showCurrentPassword()).toBe(false);

      component.toggleCurrentPasswordVisibility();
      expect(component.showCurrentPassword()).toBe(true);

      component.toggleCurrentPasswordVisibility();
      expect(component.showCurrentPassword()).toBe(false);
    });

    it('toggle nueva contraseña visibility', () => {
      expect(component.showNewPassword()).toBe(false);

      component.toggleNewPasswordVisibility();
      expect(component.showNewPassword()).toBe(true);

      component.toggleNewPasswordVisibility();
      expect(component.showNewPassword()).toBe(false);
    });

    it('toggle contraseña email visibility', () => {
      expect(component.showEmailPassword()).toBe(false);

      component.toggleEmailPasswordVisibility();
      expect(component.showEmailPassword()).toBe(true);

      component.toggleEmailPasswordVisibility();
      expect(component.showEmailPassword()).toBe(false);
    });
  });
});
