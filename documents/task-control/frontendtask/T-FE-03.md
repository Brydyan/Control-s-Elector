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

### Arquitectura de Seguridad: Tokens en Memoria y Cookie HttpOnly

La seguridad de autenticación del sistema se basa en dos capas estrictas:
- **Access Token**: Se almacena **exclusivamente en memoria JS** (Signal reactivo en `AuthStore` o variable en `TokenService`). **Nunca** en `localStorage` ni `sessionStorage`. Al recargar o cerrar la pestaña, el access token desaparece, protegiéndolo completamente de robo por inyección de código (XSS).
- **Refresh Token**: Viaja y se almacena en una **Cookie HttpOnly** (`SameSite=Strict; Secure; Path=/`). Es totalmente invisible e inaccesible para cualquier código JavaScript (`document.cookie` no puede leerla). El navegador la administra y envía automáticamente al backend en cada petición relevante (`withCredentials: true`).

```
localStorage / sessionStorage: accesible por cualquier JS (vulnerable a XSS) ❌
Access Token:                  memoria JS (Signal en AuthStore / TokenService) ✅
Refresh Token:                 Cookie HttpOnly (SameSite=Strict, Secure) invisible a JS ✅
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

  // El access token vive SOLO en memoria JS (Signal reactivo o variable) — nunca localStorage
  // Al recargar la página, se pierde y se recupera vía Cookie HttpOnly
  private accessToken: string | null = null;

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

  // ─── Refresh Token (Cookie HttpOnly) ───────────────────────────────────────
  // El refresh token NO se almacena en localStorage ni en ninguna API de JS.
  // Viaja y reside exclusivamente en una Cookie HttpOnly (SameSite=Strict, Secure)
  // administrada de forma nativa por el navegador.

  // ─── Limpiar todo ──────────────────────────────────────────────────────────

  clearAll(): void {
    this.clearAccessToken();
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
   * Guarda access token en memoria y emite el usuario autenticado.
   * El refresh token viaja en Cookie HttpOnly (SameSite=Strict, Secure) gestionada por el navegador.
   */
  login(email: string, password: string): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/auth/login`,
      { email, password },
      { withCredentials: true }
    ).pipe(
      tap(response => {
        // Guardar access token en memoria
        this.tokenService.setAccessToken(response.accessToken);

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
   * Renovar el access token usando la Cookie HttpOnly del refresh token.
   * Se llama automáticamente desde el interceptor cuando hay un 401.
   */
  refresh(): Observable<LoginResponse> {
    return this.http.post<LoginResponse>(
      `${this.apiUrl}/auth/refresh`,
      {},
      { withCredentials: true }
    ).pipe(
      tap(response => {
        this.tokenService.setAccessToken(response.accessToken);
      }),
      catchError(() => {
        // Si el refresh falla (cookie ausente, expirada o revocada), cerrar sesión
        this.logout();
        return EMPTY;
      })
    );
  }

  /**
   * Cerrar sesión.
   * Revoca la sesión en backend (eliminando la cookie HttpOnly) y limpia el estado local.
   */
  logout(): void {
    // Revocar en el backend enviando la cookie HttpOnly
    this.http.post(`${this.apiUrl}/auth/logout`, {}, { withCredentials: true }).subscribe({
      error: () => {}  // Ignorar error — igual limpiamos localmente
    });

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
   * Intenta recuperar la sesión usando la cookie HttpOnly al iniciar la app.
   */
  private intentarRecuperarSesion(): void {
    this.refresh().subscribe();
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
    console.log('Refresh Token en Cookie HttpOnly: gestionado por navegador (withCredentials: true)');
  },
  error: (err) => console.error('Login falló:', err)
});
```

Verifica:
- El access token está exclusivamente en memoria JS (Signal reactivo en AuthStore o variable en TokenService, nunca en localStorage ni sessionStorage)
- El refresh token viaja y se almacena en Cookie HttpOnly (`SameSite=Strict, Secure`), invisible para JavaScript (`document.cookie` no lo muestra) y visible únicamente en DevTools del navegador (pestaña Application > Cookies)
- Al recargar la página, el access token en memoria se recupera mediante la llamada a `/auth/refresh` con `withCredentials: true` gracias a la cookie HttpOnly

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `jwtDecode is not a function` | Import incorrecto | Usa `import { jwtDecode } from 'jwt-decode'` |
| `hasValidToken()` retorna false inmediatamente | Token expirado o mal formado | Verifica que el backend genera el token correctamente |
| `BehaviorSubject` no emite | No llamas `next()` | Verifica que `usuarioActual$.next(usuario)` se llama en el `tap` del login |
| Token persiste después de logout | `clearAccessToken()` no limpia la variable | Verifica que `this.accessToken = null` está en el método |
