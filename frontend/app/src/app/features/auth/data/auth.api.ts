import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, map } from 'rxjs';

import { environment } from '../../../../environments/environment';
import { CurrentUser } from '../domain/models/user.model';
import { AuthResponseDto, CurrentUserDto, LoginRequestDto } from './auth.dto';

/**
 * Raw session result: the in-memory access token plus the resolved user.
 * The refresh token is NOT here on purpose — it travels in an httpOnly
 * cookie set by the backend and is never visible to JS.
 */
export interface Session {
  accessToken: string;
  expiresInSeconds: number;
  sesionId: string;
  user: CurrentUser;
}

/**
 * Thin HTTP layer. Talks to /api/v1/auth and maps transport DTOs into
 * the domain model. No state, no side effects beyond the request.
 *
 * `withCredentials: true` is required so the browser sends/receives the
 * httpOnly refresh cookie.
 */
@Injectable({ providedIn: 'root' })
export class AuthApi {
  private readonly http = inject(HttpClient);
  private readonly base = `${environment.apiUrl}/auth`;

  login(credentials: LoginRequestDto): Observable<Session> {
    return this.http
      .post<AuthResponseDto>(`${this.base}/login`, credentials, {
        withCredentials: true,
      })
      .pipe(map((dto) => this.toSession(dto)));
  }

  refresh(): Observable<Session> {
    return this.http
      .post<AuthResponseDto>(
        `${this.base}/refresh`,
        {},
        { withCredentials: true },
      )
      .pipe(map((dto) => this.toSession(dto)));
  }

  logout(): Observable<void> {
    return this.http.post<void>(
      `${this.base}/logout`,
      {},
      { withCredentials: true },
    );
  }

  me(): Observable<CurrentUser> {
    return this.http
      .get<CurrentUserDto>(`${this.base}/me`, { withCredentials: true })
      .pipe(map((dto) => this.toUser(dto)));
  }

  private toSession(dto: AuthResponseDto): Session {
    return {
      accessToken: dto.accessToken,
      expiresInSeconds: dto.expiresInSeconds,
      sesionId: dto.sesionId,
      user: this.toUser(dto.usuario),
    };
  }

  private toUser(dto: CurrentUserDto): CurrentUser {
    return {
      id: dto.id,
      email: dto.email,
      role: dto.rol as CurrentUser['role'],
      permissions: dto.permisos ?? [],
    };
  }
}
