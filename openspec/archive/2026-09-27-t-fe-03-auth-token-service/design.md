# Design — T-FE-03: AuthService y Manejo de Tokens

## D1 — Token en Memoria vs localStorage

**Decisión:** Memoria. Token se pierde al recargar, que es el comportamiento deseado.

**Alternativa descartada — localStorage.** Persiste para siempre, accesible a cualquier script JS (XSS risk). Refresh token en BD es suficiente para recuperar sesión.

## D2 — BehaviorSubject vs ReplaySubject vs Subject

**Decisión:** BehaviorSubject para currentUser$ (siempre emite el estado actual a nuevos subscribers). Subject para eventos puntuales.

**Alternativa descartada — ReplaySubject(1).** Similar a BehaviorSubject pero requiere inicialización. BehaviorSubject es más simple.

## D3 — Dónde vive AuthService

**Decisión:** `core/auth/services/auth.service.ts`. Exportable vía `@core/auth`.

**Alternativa descartada — en `core/services/`.** AuthService es específico de auth, mérece su carpeta.

## D4 — Manejo de errores en login

**Decisión:** login() propagandiza errores (throw desde el Observable). El caller decide qué hacer.

**Alternativa descartada — login() retorna {success: bool, error?: string}.** Observable y throw es el patrón Angular estándar.
