import { TestBed } from '@angular/core/testing';
import { Observable, Subject, of, throwError } from 'rxjs';
import { beforeEach, describe, expect, it, vi } from 'vitest';

import { AuthRepository, Session } from '../data/auth.repository';
import { AuthStore } from '../auth.store';
import { AuthService } from './auth.service';
import { CurrentUser } from '../domain/user.model';

/**
 * Tests unitarios para AuthService (T-FE-03 CRITICAL pendientes).
 *
 * <p>Cubre:
 * <ul>
 *   <li>S-AuthService-1: login persiste token y usuario en store.</li>
 *   <li>S-AuthService-2: logout limpia store.</li>
 *   <li>S-AuthService-3: refresh single-flight reusa la primera request.</li>
 * </ul>
 *
 * <p>Estos tests son los que captura la mutación C.1 (comentar persistencia) y la mutación
 * C.2 (comentar store.clear). Sin ellos las regresiones pasan sin detección.
 */
describe('AuthService (T-FE-03)', () => {
  const mockUser: CurrentUser = {
    id: 'user-123',
    email: 'juan@test.local',
    role: 'OBSERVER',
    permissions: ['acta:upload'],
  };

  const mockSession: Session = {
    accessToken: 'token-123',
    expiresInSeconds: 900,
    sesionId: 'session-456',
    user: mockUser,
  };

  let service: AuthService;
  let store: AuthStore;
  let repo: {
    login: ReturnType<typeof vi.fn>;
    refresh: ReturnType<typeof vi.fn>;
    logout: ReturnType<typeof vi.fn>;
  };

  beforeEach(() => {
    repo = {
      login: vi.fn(),
      refresh: vi.fn(),
      logout: vi.fn(),
    };
    TestBed.configureTestingModule({
      providers: [
        AuthService,
        AuthStore,
        { provide: AuthRepository, useValue: repo },
      ],
    });
    service = TestBed.inject(AuthService);
    store = TestBed.inject(AuthStore);
    store.clear();
  });

  // -------------------------------------------------------------------------
  // S-AuthService-1: login persiste token y usuario en store.
  // -------------------------------------------------------------------------

  it('S-AuthService-1: login persiste accessToken y user en AuthStore', () => {
    repo.login.mockReturnValue(of(mockSession));

    service.login({ email: 'juan@test.local', password: '123' }).subscribe();

    expect(store.accessToken()).toBe('token-123');
    expect(store.user()?.id).toBe('user-123');
    expect(store.isAuthenticated()).toBe(true);
  });

  it('S-AuthService-1b: login propaga error sin tocar el store', () => {
    repo.login.mockReturnValue(throwError(() => new Error('credenciales inválidas')));
    const observed: unknown[] = [];
    service.login({ email: 'bad@test.local', password: 'wrong' }).subscribe({
      error: (e) => observed.push(e),
    });
    expect(store.accessToken()).toBeNull();
    expect(store.user()).toBeNull();
    expect(observed.length).toBe(1);
  });

  // -------------------------------------------------------------------------
  // S-AuthService-2: logout limpia store.
  // -------------------------------------------------------------------------

  it('S-AuthService-2: logout limpia accessToken y user en AuthStore', () => {
    repo.logout.mockReturnValue(of(undefined));
    store.setSession('token-123', mockUser);
    expect(store.isAuthenticated()).toBe(true);

    service.logout().subscribe();

    expect(store.accessToken()).toBeNull();
    expect(store.user()).toBeNull();
    expect(store.isAuthenticated()).toBe(false);
  });

  // -------------------------------------------------------------------------
  // S-AuthService-3: refresh single-flight.
  // -------------------------------------------------------------------------

  it('S-AuthService-3: dos refreshes concurrentes comparten la misma petición', () => {
    // refresh() debe reusar un Observable in-flight. Usar un Subject (no cold Observable como
    // of()) que se mantiene abierto hasta que llamemos .next() — así no completa sync.
    const subject$ = new Subject<Session>();
    const shared$: Observable<Session> = new Observable((subscriber) => {
      const s = subject$.subscribe(subscriber);
      return () => s.unsubscribe();
    });
    repo.refresh.mockReturnValue(shared$);

    const received1: Session[] = [];
    const received2: Session[] = [];
    service.refresh().subscribe((s) => received1.push(s));
    service.refresh().subscribe((s) => received2.push(s));

    // En este punto ambos subscribers deberían estar enganchados al MISMO Observable.
    expect(repo.refresh).toHaveBeenCalledTimes(1);

    // Emitir valor; ambos deben recibir.
    subject$.next({ ...mockSession, accessToken: 'refreshed-token' });
    expect(received1.length).toBe(1);
    expect(received2.length).toBe(1);
    expect(received1[0].accessToken).toBe('refreshed-token');
  });

  it('S-AuthService-3b: refresh() persiste el nuevo token en store', () => {
    repo.refresh.mockReturnValue(of({ ...mockSession, accessToken: 'new-token' }));

    service.refresh().subscribe();

    expect(store.accessToken()).toBe('new-token');
  });
});
