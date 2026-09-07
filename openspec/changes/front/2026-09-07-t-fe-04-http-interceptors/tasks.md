# Tasks — T-FE-04: Interceptors HTTP

## Fase A: JwtInterceptor

- [ ] **A.1** — Crear `core/auth/interceptors/jwt.interceptor.ts`.
  - Exporta `jwtInterceptor: HttpInterceptorFn`
  - Inyecta TokenService
  - Si URL contiene `/api/` (no /auth/login, /auth/register):
    - Si token existe, agrega `Authorization: Bearer <token>`
  - Retorna `next(request)`

- [ ] **A.2** — Test: http.get('/api/observer/*') tiene header Authorization
  - Usar HttpTestingController para mockear backend
  - Verificar que header existe

**Compuerta A:** `ng test` pasa test de JwtInterceptor.

## Fase B: RefreshInterceptor

- [ ] **B.1** — Crear `core/auth/interceptors/refresh.interceptor.ts`.
  - Exporta `refreshInterceptor: HttpInterceptorFn`
  - Inyecta TokenService, AuthService, HttpClient
  - Llama `next(request)`
  - Si response tiene status 401:
    - Llama `authService.refresh()`
    - Si refresh éxito: clona request original, lo ejecuta con nuevo token
    - Si refresh falla: propaga 401

- [ ] **B.2** — Test: mock 401, verificar que se intenta refresh

**Compuerta B:** `ng test` pasa test de RefreshInterceptor.

## Fase C: Registración en AppConfig

- [ ] **C.1** — En `src/main.ts` o `app.config.ts`, registrar interceptors:
  ```typescript
  providedIn: [
    withInterceptors([jwtInterceptor, refreshInterceptor])
  ]
  ```

- [ ] **C.2** — Crear barrel: `core/auth/interceptors/index.ts`

- [ ] **C.3** — Test de integración: mock backend retorna 401, verifica que se intenta refresh.

**Compuerta C:** `ng build` compila. Interceptors están registrados.

## Qué NO hacer

- **No agregar retry de otros errores** — solo 401
- **No logout automático** — eso puede hacerlo el caller
- **No agregar rate limiting** — una sola reintento está bien

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng test` (JwtInterceptor) | header Authorization present |
| B | `ng test` (RefreshInterceptor) | 401 → refresh → reintento |
| C | `ng build` | exit 0, interceptors registrados |

## Verificación por Mutación

- **V.1** — Comentar agrega de Authorization header → request no tiene header → restaurar
- **V.2** — Comentar reintento en 401 → 401 propaga directo → restaurar
