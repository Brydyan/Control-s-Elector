# Tasks — T-FE-03: AuthService y Manejo de Tokens

## Fase A: TokenService / AuthStore

- [x] **A.1** — Crear `core/auth/auth.store.ts` (Signals: access token en memoria, no localStorage).
  - Propiedades privadas: `accessToken: signal<string | null>(null)`
  - Getters reactivos: `isAuthenticated()`, `user()`, `accessToken()`
  - Métodos: `setSession()`, `clear()`
  - Sin uso de localStorage
- [x] **A.2** — Crear `core/auth/data/auth.repository.ts`.
  - Método `login(credentials)` → POST /api/v1/auth/login
  - Método `refresh()` → POST /api/v1/auth/refresh
  - Método `logout()` → POST /api/v1/auth/logout
  - Todos retornan Observable y usan `withCredentials: true` para cookies httpOnly
- [x] **A.3** — Tests para almacenamiento en memoria y repositorio de autenticación.

**Compuerta A:** `pnpm test:ci` sin errores en AuthStore/AuthRepository.

## Fase B: AuthService

- [x] **B.1** — Crear `core/auth/auth.service.ts`.
  - Inyectar: AuthStore, AuthRepository
  - Método `login(credentials): Observable<Session>`
    - Llamar authRepository.login()
    - Si éxito: persistir en AuthStore
    - Si error: relanzar error
- [x] **B.2** — Agregar `logout()`: limpia token y AuthStore
- [x] **B.3** — Agregar `refresh(): Observable<Session>`
  - Llama authRepository.refresh()
  - Single-flight refresh con `shareReplay`
  - Actualiza token en store
- [x] **B.4** — Exportar en módulo core/auth.

**Compuerta B:** `pnpm build` compila. `AuthService` inyectable globalmente.

## Fase C: Integración y Restauración de Sesión

- [x] **C.1** — Implementar `restoreSession()` para reconstruir sesión al recargar página vía cookie httpOnly.
- [x] **C.2** — Tests de integración: orquestación de login, restoreSession, refresh y logout.

**Compuerta C:** Test pasa. `pnpm test:ci` sin errores en auth.service.

## Qué NO hacer

- **No agregar retry logic** — eso es interceptor (T-FE-04)
- **No crear UI** — servicios solo, stubs
- **No guardar token en localStorage**
- **No agregar expiración automática** — eso es parte del interceptor

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `pnpm test:ci` | exit 0 |
| B | `pnpm build` | exit 0, AuthService importable |
| C | `pnpm test:ci` | Observable emite, sesión actualiza |

## Verificación por Mutación

- **V.1** — Comentar persistencia en login() → store no actualiza → restaurar
- **V.2** — Comentar store.clear() en logout() → sesión permanece abierta → restaurar
