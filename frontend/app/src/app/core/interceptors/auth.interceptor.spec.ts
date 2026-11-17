import {
  HttpClient,
  HttpErrorResponse,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { environment } from '../../../environments/environment';
import { authInterceptor } from './auth.interceptor';
import { AuthService } from '../../features/auth/services/auth.service';
import { AuthStore } from '../../features/auth/services/auth.store';
import { CurrentUser } from '../../features/auth/domain/models/user.model';

describe('authInterceptor', () => {
  let http: HttpClient;
  let httpTesting: HttpTestingController;
  let store: AuthStore;
  let authService: { refresh: ReturnType<typeof vi.fn> };
  let router: { navigateByUrl: ReturnType<typeof vi.fn> };

  const mockUser: CurrentUser = {
    id: 1,
    publicId: 'user-uuid',
    nombre: 'Juan',
    apellido: 'Perez',
    email: 'juan@test.local',
    role: 'ADMIN',
    permissions: [],
  };

  beforeEach(() => {
    authService = {
      refresh: vi.fn(),
    };
    router = {
      navigateByUrl: vi.fn(),
    };

    TestBed.configureTestingModule({
      providers: [
        AuthStore,
        { provide: AuthService, useValue: authService },
        { provide: Router, useValue: router },
        provideHttpClient(withInterceptors([authInterceptor])),
        provideHttpClientTesting(),
      ],
    });

    http = TestBed.inject(HttpClient);
    httpTesting = TestBed.inject(HttpTestingController);
    store = TestBed.inject(AuthStore);
  });

  afterEach(() => {
    httpTesting.verify();
  });

  it('should attach Bearer token to API requests when token exists', () => {
    store.setSession('test-token-123', mockUser);

    http.get(`${environment.apiUrl}/menus/me`).subscribe();

    const req = httpTesting.expectOne(`${environment.apiUrl}/menus/me`);
    expect(req.request.headers.get('Authorization')).toBe(
      'Bearer test-token-123',
    );
    req.flush([]);
  });

  it('should not attach Bearer token to non-API external requests', () => {
    store.setSession('test-token-123', mockUser);

    http.get('https://s3.external.com/file.jpg').subscribe();

    const req = httpTesting.expectOne('https://s3.external.com/file.jpg');
    expect(req.request.headers.has('Authorization')).toBe(false);
    req.flush({});
  });

  it('should redirect to /dashboard on 403 Forbidden without clearing session', () => {
    store.setSession('test-token-123', mockUser);

    let receivedError: HttpErrorResponse | null = null;
    http.get(`${environment.apiUrl}/admin/users`).subscribe({
      error: (err) => (receivedError = err),
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/admin/users`);
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

    expect(receivedError).not.toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/dashboard');
    expect(store.isAuthenticated()).toBe(true);
  });

  it('should redirect OBSERVER to /observer/mis-jrvs on 403 Forbidden (avoids /dashboard loop)', () => {
    const observerUser: CurrentUser = { ...mockUser, role: 'OBSERVER' };
    store.setSession('test-token-123', observerUser);

    let receivedError: HttpErrorResponse | null = null;
    http.get(`${environment.apiUrl}/dashboard`).subscribe({
      error: (err) => (receivedError = err),
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/dashboard`);
    req.flush('Forbidden', { status: 403, statusText: 'Forbidden' });

    expect(receivedError).not.toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/observer/mis-jrvs');
    expect(router.navigateByUrl).not.toHaveBeenCalledWith('/dashboard');
    expect(store.isAuthenticated()).toBe(true);
  });

  it('should redirect to /login and clear session on 401 from /auth/refresh', () => {
    store.setSession('test-token-123', mockUser);

    let receivedError: HttpErrorResponse | null = null;
    http.post(`${environment.apiUrl}/auth/refresh`, {}).subscribe({
      error: (err) => (receivedError = err),
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/auth/refresh`);
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).not.toBeNull();
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
    expect(store.isAuthenticated()).toBe(false);
  });

  it('should attempt refresh on 401 and retry original request when refresh succeeds', () => {
    store.setSession('old-token', mockUser);
    authService.refresh.mockReturnValue(
      of({ accessToken: 'new-token', user: mockUser }),
    );

    let responseData: unknown = null;
    http.get(`${environment.apiUrl}/data`).subscribe((res) => {
      responseData = res;
    });

    const firstReq = httpTesting.expectOne(`${environment.apiUrl}/data`);
    expect(firstReq.request.headers.get('Authorization')).toBe(
      'Bearer old-token',
    );
    firstReq.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(authService.refresh).toHaveBeenCalled();

    const retryReq = httpTesting.expectOne(`${environment.apiUrl}/data`);
    expect(retryReq.request.headers.get('Authorization')).toBe(
      'Bearer new-token',
    );
    retryReq.flush({ success: true });

    expect(responseData).toEqual({ success: true });
    expect(router.navigateByUrl).not.toHaveBeenCalled();
  });

  it('should clear session and redirect to /login when refresh attempt fails on 401', () => {
    store.setSession('old-token', mockUser);
    authService.refresh.mockReturnValue(
      throwError(() => new Error('Refresh failed')),
    );

    let receivedError: HttpErrorResponse | null = null;
    http.get(`${environment.apiUrl}/data`).subscribe({
      error: (err) => (receivedError = err),
    });

    const req = httpTesting.expectOne(`${environment.apiUrl}/data`);
    req.flush('Unauthorized', { status: 401, statusText: 'Unauthorized' });

    expect(receivedError).not.toBeNull();
    expect(store.isAuthenticated()).toBe(false);
    expect(router.navigateByUrl).toHaveBeenCalledWith('/login');
  });
});
