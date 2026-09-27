# Tasks — T-FE-05: Pantalla de Login

## Fase A: Componente y Formulario Reactivo

- [x] **A.1** — Crear `features/auth/login/login.page.ts`.
  - Standalone: true, imports: [ReactiveFormsModule, CommonModule]
  - FormGroup: email (Validators.required, Validators.email), password (Validators.required, Validators.minLength(8))
  - Signals: `loading`, `errorMessage`, `showPassword`
- [x] **A.2** — Crear template `login.page.html`.
  - Form con inputs (email, password con toggle de visibilidad)
  - Mensajes de validación reactivos al tocar campo
  - Botón de envío con estado loading/disabled
- [x] **A.3** — Crear SCSS `login.page.scss` con diseño responsive y corporativo.

**Compuerta A:** `pnpm build`. Componente compila sin errores.

## Fase B: Lógica de Login e Integración

- [x] **B.1** — Inyectar `AuthService`, `Router` y `NonNullableFormBuilder` en `LoginPage`.
- [x] **B.2** — Implementar método `submit()`:
  - Invoca `authService.login(credentials)`
  - Gestiona ciclo de vida de `loading` y captura de excepciones HTTP en `errorMessage`
- [x] **B.3** — **Pendiente para Builder (Alineación por Rol):**
  - Actualmente `login.page.ts:51` navega fijamente a `/dashboard`.
  - Redirigir según el rol del usuario autenticado:
    - `OBSERVER` → `/observer/mis-jrvs` (evita 403 Forbidden)
    - `COORDINATOR`, `MASTER`, `ADMIN` → `/dashboard`

**Compuerta B:** Redirección post-login segregada por rol funcional.

## Fase C: Error Handling y UX

- [x] **C.1** — Validaciones visuales y mensajes claros para errores de formato.
- [x] **C.2** — Mapeo de errores HTTP (`readError`):
  - Status 0: Error de conexión
  - Status 401: Credenciales incorrectas
  - Status 403: Cuenta bloqueada o sin permisos
  - Default: Error general de autenticación

**Compuerta C:** `pnpm test:ci` sin errores.

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `pnpm build` | exit 0 |
| B | `pnpm test:ci` | Flujo de login y redirección por rol |
| C | `pnpm build` | exit 0, diseño y UX responsive |
