# Tasks — T-FE-03: AuthService y Manejo de Tokens

## Fase A: TokenService

- [ ] **A.1** — Crear `core/auth/services/token.service.ts`.
  - Propiedades privadas: `private token: string | null = null`
  - Métodos: `getToken()`, `setToken(token)`, `clearToken()`
  - Sin uso de localStorage

- [ ] **A.2** — Crear `core/auth/services/auth.repository.ts`.
  - Método `login(email, password)` → POST /api/auth/login
  - Método `refresh()` → POST /api/auth/refresh
  - Método `logout()` → POST /api/auth/logout (si backend lo requiere)
  - Todos retornan Observable

- [ ] **A.3** — Crear tests para TokenService:
  ```
  getToken() retorna null al inicio
  setToken() actualiza el token
  clearToken() limpia el token
  ```
  Reportar nombre de archivo de test.

**Compuerta A:** `ng test` sin errores en TokenService.

## Fase B: AuthService

- [ ] **B.1** — Crear `core/auth/services/auth.service.ts`.
  - Inyectar: TokenService, AuthRepository, HttpClient
  - Propiedad: `currentUser$ = new BehaviorSubject<CurrentUser | null>(null)`
  - Método: `login(email, password): Observable<{token, user}>`
    - Llamar authRepository.login()
    - Si éxito: tokenService.setToken(), currentUser$.next(user)
    - Si error: currentUser$.next(null), relanzar error

- [ ] **B.2** — Agregar `logout()`: limpia token y currentUser$

- [ ] **B.3** — Agregar `refresh(): Observable<{token}>`
  - Llama authRepository.refresh()
  - Actualiza token
  - No toca currentUser$ (usuario sigue igual)

- [ ] **B.4** — Crear barrel: `core/auth/services/index.ts`

**Compuerta B:** `ng build` compila. `import { AuthService } from '@core/auth/services'` funciona.

## Fase C: Integración Stubs

- [ ] **C.1** — Crear stubs de `AuthRepository` que retornan datos hardcodeados (para desarrollo).
  - login() retorna {accessToken: 'jwt-stub', user: {id: 1, rol: 'OBSERVER', email: 'test@ex.com'}}

- [ ] **C.2** — Test de integración: inyecta AuthService, llama login(), verifica que currentUser$ emite.

**Compuerta C:** Test pasa. `ng test` sin errores en auth.service.

## Qué NO hacer

- **No agregar retry logic** — eso es interceptor (T-FE-04)
- **No crear UI** — servicios solo, stubs
- **No guardar token en localStorage**
- **No agregar expiración automática** — eso es parte del interceptor

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng test` (TokenService) | exit 0, 3+ tests pass |
| B | `ng build` | exit 0, AuthService importable |
| C | `ng test` (AuthService.login) | Observable emite, currentUser$ actualiza |

## Verificación por Mutación

- **V.1** — Comentar tokenService.setToken() en login() → currentUser$ emite pero token es null → restaurar
- **V.2** — Comentar currentUser$.next() en logout() → token se limpia pero currentUser$ no emite nada → restaurar
