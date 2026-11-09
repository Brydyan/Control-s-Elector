# Proposal — T-FE-03: AuthService y Manejo de Tokens

## Qué se construye
Servicios centralizados de autenticación:
- **TokenService** — guarda access token en memoria (nunca localStorage)
- **AuthService** — orquesta login, logout, refresh, estado del usuario

## Por qué
- Token en memoria evita robo por XSS (localStorage es accesible por cualquier script).
- Refresh token en BD permite revocación global (logout).
- Servicios centralizados → un solo lugar donde está la lógica auth.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| TokenService | get/set token en memory, BehaviorSubject | persistencia |
| AuthService | login(email, pass), logout, refresh, estado$ | UI, componentes |
| Estado | currentUser$ (BehaviorSubject con user info) | notificaciones |
| Refresh | lógica POST /api/auth/refresh | retry logic |

## Criterio de aceptación

- [ ] TokenService guarda token en variable privada (no localStorage)
- [ ] AuthService.login(email, pass) retorna Observable<{token, user}>
- [ ] AuthService.logout() limpia token y currentUser$
- [ ] AuthService.refresh() llama POST /api/auth/refresh
- [ ] currentUser$ emite cuando cambios
- [ ] Tests: logout borra token, refresh obtiene nuevo token

## Fuera de alcance

- Interceptors (T-FE-04)
- UI de login (T-FE-05)
- Guards que usan estos servicios (T-FE-02)

## Dependencias

- T-FE-01 (estructura)

## Decisiones a tomar

**D1 — dónde vive el token:** En memoria (TokenService), no localStorage.

**D2 — BehaviorSubject vs ReplaySubject:** Usar BehaviorSubject (siempre emite último valor a nuevos subscribers).
