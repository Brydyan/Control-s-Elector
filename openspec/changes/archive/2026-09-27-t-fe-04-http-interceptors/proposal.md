# Proposal — T-FE-04: Interceptors HTTP (JWT + Refresh Automático)

## Qué se construye
Dos interceptors HTTP que se ejecutan automáticamente en cada request/response:

1. **JwtInterceptor** — agrega header `Authorization: Bearer <token>` a cada request
2. **RefreshInterceptor** — si backend retorna 401, intenta renovar token y reintenta el request

## Por qué
- JWT debe agregarse automáticamente sin que el llamador lo haga manualmente.
- 401 → refresh es patrón estándar. Sin interceptor, cada service tendría que manejarlo.
- Centralizado: un solo lugar donde vive la lógica.

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| JwtInterceptor | Agrega Authorization header | manejo de errores |
| RefreshInterceptor | Detecta 401, reintenta request | logout automático |
| Orden | JwtInterceptor primero, luego Refresh | otros interceptors |

## Criterio de aceptación

- [ ] JwtInterceptor agrega header a todos los requests (menos /auth/login)
- [ ] RefreshInterceptor detecta 401 y retira `/api/auth/refresh`
- [ ] Si refresh tiene éxito, reintenta el request original
- [ ] Si refresh falla, propaga el 401
- [ ] Tests: request con token tiene Authorization header, 401 dispara refresh

## Fuera de alcance

- Logout automático si refresh falla (puede manejarlo T-FE-05)
- Retry de otros errores (4xx, 5xx)

## Dependencias

- T-FE-03 (TokenService, AuthService)

## Decisiones a tomar

**D1 — HttpInterceptorFn vs class:** Funcionales. Más simples en Angular 16+.

**D2 — dónde viven:** En `core/auth/interceptors/`. Registrados en AppConfig.
