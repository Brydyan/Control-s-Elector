# T-FE-04 — Interceptors HTTP (JWT + Refresh Automático)

**Dificultad:** Intermedio
**Tiempo estimado:** 2 horas
**Depende de:** T-FE-03

---

## ¿Qué vas a construir?

Dos interceptors que se ejecutan automáticamente en cada request HTTP:

1. **JwtInterceptor** — agrega el token JWT al header `Authorization` de cada request
2. **RefreshInterceptor** — si el servidor responde `401`, intenta renovar el token y reintenta el request

---

## Conceptos clave

### ¿Qué es un interceptor HTTP?
Es una función que "intercepta" todos los requests y responses HTTP de tu app. Funciona como un middleware:

```
Tu código → [JwtInterceptor] → [RefreshInterceptor] → Backend
Tu código ← [JwtInterceptor] ← [RefreshInterceptor] ← Backend
```

### HttpInterceptorFn (Angular 16+)
En Angular moderno, los interceptors son funciones simples:

```typescript
export const miInterceptor: HttpInterceptorFn = (req, next) => {
  // Modificar el request
  const reqModificado = req.clone({ headers: req.headers.set('X-Custom', 'valor') });
  // Continuar con el request modificado
  return next(reqModificado);
};
```

### req.clone() — el request es inmutable
No puedes modificar un request directamente. Debes clonar y modificar el clon:

```typescript
// ❌ Incorrecto — los requests son inmutables
req.headers.set('Authorization', 'Bearer token');

// ✅ Correcto — clonar con los cambios
const reqConToken = req.clone({
  headers: req.headers.set('Authorization', 'Bearer ' + token)
});
```

### switchMap — reemplazar un observable con otro
Cuando recibimos un 401, necesitamos llamar al refresh y luego reintentar:
```typescript
catchError(error => {
  if (error.status === 401) {
    return authService.refresh().pipe(
      switchMap(() => next(reqConNuevoToken))  // reintenta con token nuevo
    );
  }
  throw error;
})
```

---

## Archivos a crear/modificar

```
src/app/core/interceptors/
  jwt.interceptor.ts
  refresh.interceptor.ts
src/app/
  app.config.ts       ← registrar los interceptors
```

---

## Pasos

### Paso 1 — JwtInterceptor

```typescript
// src/app/core/interceptors/jwt.interceptor.ts
import { HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { TokenService } from '../services/token.service';

export const jwtInterceptor: HttpInterceptorFn = (req, next) => {
  const tokenService = inject(TokenService);
  const token = tokenService.getAccessToken();

  // Si no hay token o el request es para el endpoint de auth (login/refresh),
  // dejamos pasar sin modificar
  const esEndpointAuth = req.url.includes('/auth/login') ||
                          req.url.includes('/auth/refresh');

  if (!token || esEndpointAuth) {
    return next(req);
  }

  // Clonar el request agregando el header Authorization
  const reqConToken = req.clone({
    headers: req.headers.set('Authorization', `Bearer ${token}`)
  });

  return next(reqConToken);
};
```

### Paso 2 — RefreshInterceptor

```typescript
// src/app/core/interceptors/refresh.interceptor.ts
import { HttpInterceptorFn, HttpErrorResponse } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, switchMap, throwError } from 'rxjs';
import { AuthService } from '../services/auth.service';
import { TokenService } from '../services/token.service';

export const refreshInterceptor: HttpInterceptorFn = (req, next) => {
  const authService = inject(AuthService);
  const tokenService = inject(TokenService);

  return next(req).pipe(
    catchError((error: HttpErrorResponse) => {

      // Solo actuamos si es un 401 (Unauthorized)
      // Y si NO es el endpoint de login o refresh (evitar bucle infinito)
      const esEndpointAuth = req.url.includes('/auth/login') ||
                              req.url.includes('/auth/refresh');

      if (error.status === 401 && !esEndpointAuth) {

        // Intentar renovar el token con el refresh token
        return authService.refresh().pipe(
          switchMap(() => {
            // Refresh exitoso — reintenta el request original con el nuevo token
            const nuevoToken = tokenService.getAccessToken();
            const reqReintento = req.clone({
              headers: req.headers.set('Authorization', `Bearer ${nuevoToken}`)
            });
            return next(reqReintento);
          })
          // Si el refresh falla, authService.refresh() ya llama logout()
        );
      }

      // Para cualquier otro error, propagarlo normal
      return throwError(() => error);
    })
  );
};
```

### Paso 3 — Registrar los interceptors en app.config.ts

```typescript
// src/app/app.config.ts
import { ApplicationConfig, provideZoneChangeDetection } from '@angular/core';
import { provideRouter } from '@angular/router';
import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { routes } from './app.routes';
import { jwtInterceptor } from './core/interceptors/jwt.interceptor';
import { refreshInterceptor } from './core/interceptors/refresh.interceptor';

export const appConfig: ApplicationConfig = {
  providers: [
    provideZoneChangeDetection({ eventCoalescing: true }),
    provideRouter(routes),
    provideHttpClient(
      // El orden importa: jwt se ejecuta primero, luego refresh
      withInterceptors([jwtInterceptor, refreshInterceptor])
    ),
  ]
};
```

---

## Cómo verificar que funciona

**Verificar que el token se agrega al request:**
1. Abre DevTools → Network
2. Haz cualquier request autenticado (ej: GET /api/observer/mis-actas)
3. Verifica que en "Request Headers" aparece `Authorization: Bearer eyJ...`

**Verificar el refresh automático:**
1. Genera un token con expiración muy corta (1 minuto) para pruebas
2. Espera a que expire
3. Haz un request — debes ver en Network que primero hay un 401, luego un POST /auth/refresh, y luego el request original que tuvo éxito

---

## Errores comunes

| Error | Causa | Solución |
|-------|-------|----------|
| `NullInjectorError` en el interceptor | Circular dependency entre services | Verifica que `AuthService` no inyecta el interceptor indirectamente |
| Bucle infinito de 401 | El endpoint de refresh también devuelve 401 | Verifica que excluyes `/auth/refresh` de la lógica de retry |
| `Maximum call stack exceeded` | Interceptors en orden incorrecto | Verifica el orden en `withInterceptors([jwt, refresh])` |
| Token no aparece en headers | `jwtInterceptor` no está registrado | Verifica que aparece en `app.config.ts` |
| Requests de login llevan token | Falta la exclusión de `/auth/login` | Agrega la condición `esEndpointAuth` |
