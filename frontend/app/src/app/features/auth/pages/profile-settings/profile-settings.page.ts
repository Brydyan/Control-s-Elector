import { Component, OnInit, computed, inject, signal } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule, ReactiveFormsModule } from '@angular/forms';
import { InputTextModule } from 'primeng/inputtext';
import { ButtonModule } from 'primeng/button';
import { MessageModule } from 'primeng/message';
import { ButtonComponent } from '../../../../shared/ui';
import { MessageService } from 'primeng/api';
import {
  UserApi,
  ProfileDto,
  ChangePasswordRequest,
  UpdateEmailRequest,
} from '../../data/user.api';

/**
 * Password validation requirements
 */
export interface PasswordRequirements {
  minLength: boolean;
  hasUppercase: boolean;
  hasLowercase: boolean;
  hasNumber: boolean;
  hasSpecial: boolean;
}

/**
 * User Profile & Settings Page (SDD Phase D)
 *
 * Features:
 * - Tab 1: "Mi Perfil" — immutable user data + last password change
 * - Tab 2: "Cambiar Contraseña" — form with real-time validation
 * - Tab 3: "Email Personal" — form to update personal email
 */
@Component({
  selector: 'app-profile-settings',
  standalone: true,
  imports: [
    CommonModule,
    FormsModule,
    ReactiveFormsModule,
    InputTextModule,
    ButtonModule,
    MessageModule,
    ButtonComponent,
  ],
  templateUrl: './profile-settings.page.html',
  styleUrls: ['./profile-settings.page.scss'],
})
export class ProfileSettingsPage implements OnInit {
  private readonly userApi = inject(UserApi);
  private readonly messages = inject(MessageService);

  // Tab control
  readonly activeTab = signal<'profile' | 'password' | 'email'>('profile');

  // Profile data
  readonly profile = signal<ProfileDto | null>(null);
  readonly profileLoading = signal(true);
  readonly profileError = signal<string | null>(null);

  // Change password form
  readonly currentPassword = signal('');
  readonly newPassword = signal('');
  readonly newPasswordConfirm = signal('');
  readonly changePasswordLoading = signal(false);
  readonly changePasswordError = signal<string | null>(null);
  readonly changePasswordSuccess = signal(false);
  readonly showCurrentPassword = signal(false);
  readonly showNewPassword = signal(false);

  // Email update form
  readonly personalEmail = signal('');
  readonly emailConfirmPassword = signal('');
  readonly emailLoading = signal(false);
  readonly emailError = signal<string | null>(null);
  readonly emailSuccess = signal(false);
  readonly showEmailPassword = signal(false);

  // Real-time password requirements validation
  readonly passwordRequirements = computed(() => {
    const pwd = this.newPassword();
    return {
      minLength: pwd.length >= 8,
      hasUppercase: /[A-Z]/.test(pwd),
      hasLowercase: /[a-z]/.test(pwd),
      hasNumber: /\d/.test(pwd),
      hasSpecial: /[!@#$%^&*()_+\-=[\]{};':"\\|,.<>/?]/.test(pwd),
    };
  });

  readonly allPasswordRequirementsMet = computed(() => {
    const reqs = this.passwordRequirements();
    return (
      reqs.minLength &&
      reqs.hasUppercase &&
      reqs.hasLowercase &&
      reqs.hasNumber &&
      reqs.hasSpecial
    );
  });

  readonly passwordsMatch = computed(
    () =>
      this.newPassword() === this.newPasswordConfirm() &&
      this.newPassword().length > 0,
  );

  readonly changePasswordDisabled = computed(
    () =>
      !this.allPasswordRequirementsMet() ||
      !this.passwordsMatch() ||
      !this.currentPassword() ||
      this.changePasswordLoading(),
  );

  readonly emailFormatValid = computed(() => {
    const email = this.personalEmail();
    if (!email) return false;
    const emailRegex = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;
    return emailRegex.test(email);
  });

  readonly updateEmailDisabled = computed(
    () =>
      !this.emailFormatValid() ||
      !this.emailConfirmPassword() ||
      this.emailLoading() ||
      !this.personalEmail().trim(),
  );

  ngOnInit(): void {
    this.loadProfile();
  }

  /**
   * Load user profile data
   */
  loadProfile(): void {
    this.profileLoading.set(true);
    this.profileError.set(null);
    this.userApi.getProfile().subscribe({
      next: (data) => {
        this.profile.set(data);
        this.personalEmail.set(data.emailPersonal || '');
        this.profileLoading.set(false);
      },
      error: (err) => {
        const message = err?.error?.message || 'No se pudo cargar el perfil';
        this.profileError.set(message);
        this.profileLoading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Error',
          detail: message,
          life: 5000,
        });
      },
    });
  }

  /**
   * Submit password change form
   */
  submitChangePassword(): void {
    // Validate passwords match first (before checking disabled state)
    if (this.newPassword() !== this.newPasswordConfirm()) {
      this.changePasswordError.set('Las contraseñas no coinciden');
      return;
    }

    if (this.changePasswordDisabled()) return;

    this.changePasswordLoading.set(true);
    this.changePasswordError.set(null);

    const request: ChangePasswordRequest = {
      currentPassword: this.currentPassword(),
      newPassword: this.newPassword(),
      passwordConfirm: this.newPasswordConfirm(),
    };

    this.userApi.changePassword(request).subscribe({
      next: (response) => {
        this.changePasswordSuccess.set(true);
        this.changePasswordLoading.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Éxito',
          detail: response.message,
          life: 5000,
        });
        this.clearChangePasswordForm();
      },
      error: (err) => {
        const message = err?.error?.message || 'Error al cambiar la contraseña';
        this.changePasswordError.set(message);
        this.changePasswordLoading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Error',
          detail: message,
          life: 5000,
        });
      },
    });
  }

  /**
   * Submit email update form
   */
  submitUpdateEmail(): void {
    // Validate email format first (before checking disabled state)
    if (!this.emailFormatValid()) {
      this.emailError.set('El formato del email no es válido');
      return;
    }

    if (this.updateEmailDisabled()) return;

    this.emailLoading.set(true);
    this.emailError.set(null);

    const request: UpdateEmailRequest = {
      emailPersonal: this.personalEmail(),
      confirmPassword: this.emailConfirmPassword(),
    };

    this.userApi.updatePersonalEmail(request).subscribe({
      next: (response) => {
        this.emailSuccess.set(true);
        this.emailLoading.set(false);
        this.messages.add({
          severity: 'success',
          summary: 'Éxito',
          detail: response.message,
          life: 5000,
        });
        this.emailConfirmPassword.set('');
        this.loadProfile();
      },
      error: (err) => {
        const message = err?.error?.message || 'Error al actualizar el email';
        this.emailError.set(message);
        this.emailLoading.set(false);
        this.messages.add({
          severity: 'error',
          summary: 'Error',
          detail: message,
          life: 5000,
        });
      },
    });
  }

  /**
   * Clear the change password form
   */
  private clearChangePasswordForm(): void {
    this.currentPassword.set('');
    this.newPassword.set('');
    this.newPasswordConfirm.set('');
    this.changePasswordError.set(null);
  }

  /**
   * Toggle current password visibility
   */
  toggleCurrentPasswordVisibility(): void {
    this.showCurrentPassword.set(!this.showCurrentPassword());
  }

  /**
   * Toggle new password visibility
   */
  toggleNewPasswordVisibility(): void {
    this.showNewPassword.set(!this.showNewPassword());
  }

  /**
   * Toggle email confirmation password visibility
   */
  toggleEmailPasswordVisibility(): void {
    this.showEmailPassword.set(!this.showEmailPassword());
  }
}
