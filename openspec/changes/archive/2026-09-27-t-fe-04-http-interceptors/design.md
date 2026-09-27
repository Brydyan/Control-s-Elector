# Design — T-FE-04: Interceptors HTTP

## D1 — HttpInterceptorFn vs CanActivate Class

**Decisión:** Funcionales. `export const jwtInterceptor: HttpInterceptorFn`.

**Alternativa descartada — HttpInterceptor class.** Angular 16+ prefiere funcionales, menos boilerplate.

## D2 — Ubicación

**Decisión:** `core/auth/interceptors/jwt.interceptor.ts`, `core/auth/interceptors/refresh.interceptor.ts`.

**Alternativa descartada — un solo archivo con ambos.** Responsabilidades separadas, archivo más legible.

## D3 — Orden de Interceptors

**Decisión:** JwtInterceptor primero (agrega token), luego RefreshInterceptor (maneja 401).

**Alternativa descartada — al revés.** Si RefreshInterceptor va primero, JwtInterceptor nunca agrega el token (porque RefreshInterceptor lo pasó).

## D4 — Excluyir /api/auth/login del token

**Decisión:** JwtInterceptor chequea URL. Si es /auth/login, /auth/register, no agrega token.

**Alternativa descartada — siempre agregar.** Login puede tener un token previo (refresh). Es seguro agregarlo, pero opcional.

## D5 — Reintento en 401

**Decisión:** RefreshInterceptor llama authService.refresh(). Si éxito, clona el request original y lo reintenta. Si falla, propaga el 401.

**Alternativa descartada — retry automático de cualquier request fallido.** Solo 401 es refresh. Otros errores propagan.
