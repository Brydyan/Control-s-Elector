import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';
import { ConfigService } from '../../../core/config/config.service';

/**
 * Profile data returned by GET /api/v1/user/profile
 */
export interface ProfileDto {
  id: string;
  nombre: string;
  emailControl: string;
  emailPersonal: string | null;
  rol: string;
  estado: string;
  lastPasswordChangeAt: string | null;
}

/**
 * Request to change password: POST /api/v1/auth/change-password
 */
export interface ChangePasswordRequest {
  currentPassword: string;
  newPassword: string;
  passwordConfirm: string;
}

/**
 * Response from change password endpoint
 */
export interface ChangePasswordResponse {
  message: string;
  lastPasswordChangeAt: string;
}

/**
 * Request to update personal email: PUT /api/v1/user/update-personal-email
 */
export interface UpdateEmailRequest {
  emailPersonal: string;
  confirmPassword: string;
}

/**
 * Response from update email endpoint
 */
export interface UpdateEmailResponse {
  message: string;
  emailPersonal: string;
}

/**
 * User API service: profile retrieval and account settings changes
 *
 * - GET /user/profile → retrieve user profile data
 * - POST /auth/change-password → change user password
 * - PUT /user/update-personal-email → update personal email
 */
@Injectable({ providedIn: 'root' })
export class UserApi {
  private readonly http = inject(HttpClient);
  private readonly configService = inject(ConfigService);
  private readonly baseUrl = this.configService.getApiUrlSafe();

  /**
   * Fetches the current user profile
   */
  getProfile(): Observable<ProfileDto> {
    return this.http.get<ProfileDto>(`${this.baseUrl}/user/profile`, {
      withCredentials: true,
    });
  }

  /**
   * Changes the user password
   * @param request contains currentPassword, newPassword, and passwordConfirm
   */
  changePassword(
    request: ChangePasswordRequest,
  ): Observable<ChangePasswordResponse> {
    return this.http.post<ChangePasswordResponse>(
      `${this.baseUrl}/auth/change-password`,
      request,
      { withCredentials: true },
    );
  }

  /**
   * Updates the user's personal email
   * @param request contains emailPersonal and confirmPassword
   */
  updatePersonalEmail(
    request: UpdateEmailRequest,
  ): Observable<UpdateEmailResponse> {
    return this.http.put<UpdateEmailResponse>(
      `${this.baseUrl}/user/update-personal-email`,
      request,
      { withCredentials: true },
    );
  }
}
