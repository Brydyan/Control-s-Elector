# T-FE-03 — AuthService y Manejo de Tokens

**Dificultad:** Intermedio
**Tiempo estimado:** 2-3 horas
**Depende de:** T-FE-01

---

## ¿Qué vas a construir?

Los servicios de autenticación:
- **TokenService** — guarda el access token en memoria (nunca localStorage)
- **AuthService** — login, logout, refresh, estado del usuario

---

## Conceptos clave

### ¿Por qué el access token en memoria y no localStorage?
`localStorage` es accesible por cualquier script JavaScript de la página. Si un atacante inyecta código (XSS), puede robar el token de localStorage. En memoria, el token desaparece al recargar/cerrar la página.

```
localStorage:  persiste para siempre, accesible por cualquier JS ⚠️
sessionStorage: persiste en la pestaña, accesible por cualquier JS ⚠️
memoria (variable): desaparece al recargar, solo accesible dentro del servicio ✅
```

### BehaviorSubject — estado reactivo
Un `BehaviorSubject` es como una variable que puede ser "observada". Cuando cambia, todos los que la están escuchando se actualizan automáticamente:

```typescript
// Crear
private usuario$ = new BehaviorSubject<UsuarioAutenticado | null>(null);

// Actualizar
this.usuario$.next(nuevoUsuario);

// Escuchar cambios
this.authService.getUsuario().subscribe(usuario => {
  console.log('Usuario cambió:', usuario);
});
```

### jwtDecode — leer el payload del token
Para leer los datos del JWT sin verificar la firma (el backend ya lo verificó):
```bash
npm install jwt-decode
```

```typescript
import { jwtDecode } from 'jwt-decode';

const payload = jwtDecode<{ userId: number, rol: string }>(token);
```

---

## Archivos a crear

```
src/app/core/services/
  token.service.ts
  auth.service.ts
```

---

## Pasos

### Paso 1 — Instalar jwt-decode

```bash
cd frontend/app
npm install jwt-decode
```

### Paso 2 — TokenService

```typescript
// src/app/core/services/token.service.ts
import { Injectable } from '@angular/core';
import { jwtDecode } from 'jwt-decode';
import { RolUsuario } from '../../shared/models';

// Estructura del payload del JWT (debe coincidir con lo que genera el backend)
interface JwtPayload {
  sub: string;    // userId como string
  email: string;
  rol: string;
  exp: number;    // timestamp de expiración (segundos Unix)
}

@Injectable({
  providedIn: 'root'  // Singleton — una sola instancia en toda la app
})
export class TokenService {

  // El access token vive SOLO en esta variable — nunca localStorage
  // Al recargar la página, se pierde (eso es lo que queremos)
  private accessToken: string | null = null;

  // Clave para el refresh token en localStorage
  private readonly REFRESH_TOKEN_KEY = 'electoral_refresh_token';

  // ─── Access Token ──────────────────────────────────────────────────────────

  setAccessToken(token: string): void {
    this.accessToken = token;
  }

  getAccessToken(): string | null {
    return this.accessToken;
  }

  clearAccessToken(): void {
    this.accessToken = null;
  }

  hasValidToken(): boolean {
    if (!this.accessToken) return false;

    try {
      const payload = jwtDecode<JwtPayload>(this.accessToken);
      // exp está en segundos, Date.now() en milisegundos
      const expirado = payload.exp * 1000 < Date.now();
      return !expirado;
    } catch {
      return false;
    }
  }

  // ─── Datos del token ────────────────────────────────────────────────────────

  getUserId(): number | null {
    if (!this.accessToken) return null;
    try {
      const payload = jwtDecode<JwtPayload>(this.accessToken);
      return parseInt(payload.sub);
    } catch {
      return null;
    }
  }

  getRol(): RolUsuario | null {
    if (!this.accessToken) return null;
    try {
      const payload = jwtDecode<JwtPayload>(this.accessToken);
      return payload.rol as RolUsuario;
    } catch {
      return null;
    }
  }

  // ─── Refresh Token ─────────────────────────────────────────────────────────
  // El refresh token SÍ va en localStorage porque tiene vida más corta (8h)
  // y necesita sobrevivir a los recargas de página

  setRefreshToken(token: string): void {
    localStorage.setItem(this.REFRESH_TOKEN_KEY, token);
  }

  getRefreshToken(): string | null {
    return localStorage.getItem(this.REFRESH_TOKEN_KEY);
  }

  clearRefreshToken(): void {
    localStorage.removeItem(this.REFRESH_TOKEN_KEY);
  }

  // ─── Limpiar todo ──────────────────────────────────────────────────────────

  clearAll(): void {
    this.clearAccessToken();
    this.clearRefreshToken();
  }
}
```

### Paso 3 — AuthService

```typescript
// src/app/core/services/auth.service.ts
import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Router } from '@angular/router';
import { BehaviorSubject, Observable, tap, catchError, EMPTY } from 'rxjs';
import { TokenService } from './token.service';
import { LoginResponse, UsuarioAutenticado, RolUsuario } from '../../shared/models';
import { environment } from '../../../environments/environment';

@Injectable({
  providedIn: 'root'
})
export class AuthService {

  private readonly apiUrl = environment.apiUrl;

  // Estado reactivo del usuario autenticado
  // null = no autenticado
  private usuarioActual$ = new BehaviorSubject<UsuarioAutenticado | null>(null);

  constructor(
    private http: HttpClient,
    private tokenService: TokenService,
    private router: Router
  ) {
    // Al iniciar el servicio, intentar recuperar la sesión si hay refresh token
    this.intentarRecuperarSesion();
  }

  /**
   * Iniciar sesión.
   * Guarda tokens y emite el usuario autenticado.
   */
  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(`${this.apiUrl}/auth/login`, { email, password }).pipe(
      tap(response => {
        // Guardar tokens
        this.tokenService.setAccessToken(response.accessToken);
        this.tokenService.setRefreshToken(response.refreshToken);

        // Emitir usuario autenticado
        this.usuarioActual$.next({
          userId: response.userId,
          nombre: response.nombre,
          rol: response.rol
        });
      })
    );
  }

  /**
   * Renovar el access token usando el refresh token.
   * Se llama automáticamente desde el interceptor cuando hay un 401.
   */
  refresh(): Observable<{ accessToken: string }> {
    const refreshToken = this.tokenService.getRefreshToken();
    if (!refreshToken) {
      this.logout();
      return EMPTY;
    }

    return this.http.post<{ accessToken: string }>(
      `${this.apiUrl}/auth/refresh`,
      { refreshToken }
    ).pipe(
      tap(response => {
        this.tokenService.setAccessToken(response.accessToken);
      }),
      catchError(() => {
        // Si el refresh falla, cerrar sesión
        this.logout();
        return EMPTY;
      })
    );
  }

  /**
   * Cerrar sesión.
   * Limpia tokens y redirige al login.
   */
  logout(): void {
    // Intentar revocar el refresh token en el backend
    const refreshToken = this.tokenService.getRefreshToken();
    if (refreshToken) {
      this.http.post(`${this.apiUrl}/auth/logout`, {}).subscribe({
        error: () => {}  // Ignorar error — igual limpiamos localmente
      });
    }

    // Limpiar estado local
    this.tokenService.clearAll();
    this.usuarioActual$.next(null);

    // Redirigir al login
    this.router.navigate(['/login']);
  }

  /**
   * Redirigir al usuario a su página de inicio según su rol.
   */
  redirigirSegunRol(): void {
    const rol = this.tokenService.getRol();
    switch (rol) {
      case 'OBSERVER':
        this.router.navigate(['/observer/home']);
        break;
      case 'COORDINATOR':
      case 'MASTER':
        this.router.navigate(['/coordinator/home']);
        break;
      case 'ADMIN':
        this.router.navigate(['/dashboard']);
        break;
      default:
        this.router.navigate(['/login']);
    }
  }

  /**
   * Observable del usuario actual.
   * Los componentes se suscriben aquí para saber quién está logueado.
   */
  getUsuario(): Observable<UsuarioAutenticado | null> {
    return this.usuarioActual$.asObservable();
  }

  /**
   * Intenta recuperar la sesión usando el refresh token.
   * Se llama al iniciar la app (si el usuario ya había iniciado sesión).
   */
  private intentarRecuperarSesion(): void {
    if (this.tokenService.getRefreshToken()) {
      this.refresh().subscribe();
    }
  }

  isAuthenticated(): boolean {
    return this.tokenService.hasValidToken();
  }
}
```

---

## Cómo verificar que funciona

Agrega un console.log temporal en el LoginPage (que crearás en T-FE-05):

```typescript
this.authService.login('test@email.com', 'pass').subscribe({
  next: (response) => {
    console.log('Login exitoso:', response);
    console.log('Token en memoria:', this.tokenService.getAccessToken()?.slice(0, 20) + '...');
    console.log('Refresh en localStorage:', localStorage.getItem('electoral_refresh_token'));
  },
  error: (err) => console.error('Login falló:', err)
});
```

Verifica:
- El access token está en memoria (no en localStorage)
- El refresh token aparece en localStorage
- Al recargar la página, el access token desaparece pero el refresh token persiste

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `jwtDecode is not a function` | Import incorrecto | Usa `import { jwtDecode } from 'jwt-decode'` |
| `hasValidToken()` retorna false inmediatamente | Token expirado o mal formado | Verifica que el backend genera el token correctamente |
| `BehaviorSubject` no emite | No llamas `next()` | Verifica que `usuarioActual$.next(usuario)` se llama en el `tap` del login |
| Token persiste después de logout | `clearAccessToken()` no limpia la variable | Verifica que `this.accessToken = null` está en el método |
