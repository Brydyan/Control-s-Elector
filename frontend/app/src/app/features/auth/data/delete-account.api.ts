import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { environment } from '../../../../environments/environment';

/**
 * Request para solicitar eliminación de cuenta.
 */
export interface DeleteAccountRequestDto {
  password: string;
}

/**
 * Response de POST /api/v1/user/request-deletion.
 */
export interface DeleteAccountResponseDto {
  recoveryDeadline: string; // ISO 8601 timestamp
  hardDeleteDeadline: string; // ISO 8601 timestamp
}

/**
 * HTTP layer para eliminación de cuenta (T-COMPLIANCE-04).
 *
 * Maneja:
 * - POST /api/v1/user/request-deletion — solicita soft-delete
 * - POST /api/v1/auth/cancel-deletion/{token} — cancela dentro de 7d
 */
@Injectable({ providedIn: 'root' })
export class DeleteAccountApi {
  private readonly http = inject(HttpClient);
  private readonly baseUser = `${environment.apiUrl}/user`;
  private readonly baseAuth = `${environment.apiUrl}/auth`;

  /**
   * Solicita eliminación de cuenta (soft-delete con 30d grace).
   *
   * Requiere autenticación. El usuario recibe email con link de recuperación válido 7 días.
   *
   * @param password contraseña en claro para confirmar
   * @return deadlines de recuperación (7d) y hard-delete (30d)
   */
  requestDeletion(
    request: DeleteAccountRequestDto,
  ): Observable<DeleteAccountResponseDto> {
    return this.http.post<DeleteAccountResponseDto>(
      `${this.baseUser}/request-deletion`,
      request,
      { withCredentials: true },
    );
  }

  /**
   * Cancela eliminación de cuenta (dentro de 7 días).
   *
   * Público: el usuario sin sesión puede clickear el link en el email para recuperarse.
   *
   * @param token token de recuperación del email
   * @return void si éxito
   */
  cancelDeletion(token: string): Observable<void> {
    return this.http.post<void>(
      `${this.baseAuth}/cancel-deletion/${encodeURIComponent(token)}`,
      {},
    );
  }
}
