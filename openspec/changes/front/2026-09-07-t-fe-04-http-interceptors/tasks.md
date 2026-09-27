# Tasks — T-FE-04: Interceptors HTTP

## Fase A: JwtInterceptor

- [x] **A.1** — Crear `core/auth/auth.interceptor.ts` (Interceptor funcional unificado `authInterceptor`).
  - Inyecta `AuthStore`, `AuthService`, `Router`
  - Agrega `Authorization: Bearer <token>` únicamente a URLs de la API (`environment.apiUrl`)
  - Retorna `next(authorized)`
- [x] **A.2** — Tests unitarios en `core/auth/auth.interceptor.spec.ts`:
  - Request a la API incluye header Authorization con Bearer token.
  - Request externo no incluye token para prevenir fugas de credenciales.

**Compuerta A:** `pnpm test:ci` pasa tests de JwtInterceptor (10 tests pasando).

## Fase B: RefreshInterceptor & Manejo 401/403

- [x] **B.1** — Manejo reactivo de status 401 y 403 en `authInterceptor`:
  - En 401 en endpoints protegidos: reintento único transparente vía `AuthService.refresh()`.
  - En fallo de refresh o 401 en auth: limpia sesión y redirige a `/login`.
  - En 403 Forbidden: preserva sesión y redirige.
- [x] **B.2** — Tests unitarios en `auth.interceptor.spec.ts`:
  - 401 dispara refresh y reintenta la petición original con el nuevo token.
  - Fallo de refresh propaga error y redirige a `/login`.

**Compuerta B:** `pnpm test:ci` pasa tests de refresh y manejo de errores.

## Fase C: Registración en AppConfig

- [x] **C.1** — En `app.config.ts`, registrar `withInterceptors([authInterceptor])` en `provideHttpClient()`.
- [x] **C.2** — Exportación limpia desde `core/auth/`.
- [x] **C.3** — Tests de integración verificando flujo completo de llamadas HTTP interceptadas.

**Compuerta C:** `pnpm build` compila. Interceptor activo en toda la aplicación.

## Deuda Técnica / Bugfix Detectado para Builder

- [ ] **D.1** — En `core/auth/auth.interceptor.ts:56`: el manejo de 403 redirige fijamente a `/dashboard`. Para usuarios con rol `OBSERVER`, el backend devuelve 403 en `/dashboard` generando un bucle. Se debe condicionar: si `store.user()?.role === 'OBSERVER'`, redirigir a `/observer/mis-jrvs`.

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `pnpm test:ci` | 10 tests de auth.interceptor.spec.ts pasando |
| B | `pnpm test:ci` | 401 → refresh → reintento verificado |
| C | `pnpm build` | exit 0, interceptor registrado |
