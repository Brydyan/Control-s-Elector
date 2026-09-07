# Tasks — T-FE-05: Pantalla de Login

## Fase A: Componente y Formulario Reactivo

- [ ] **A.1** — Crear `features/auth/login/login.component.ts`.
  - Standalone: true, imports: [CommonModule, ReactiveFormsModule, IonicModule]
  - FormGroup: email (validators: required, email), password (validators: required)
  - Propiedades: form, loading = false, errorMessage = ''

- [ ] **A.2** — Crear template `login.component.html`.
  - Form con dos inputs (email, password)
  - Mensajes de error bajo cada campo (solo si touched + invalid)
  - Botón "Login" (disabled si loading o form invalid)
  - Spinner si loading

- [ ] **A.3** — Crear CSS `login.component.css` (básico: centrado, padding).

**Compuerta A:** `ng build`. Componente compila.

## Fase B: Lógica de Login

- [ ] **B.1** — En LoginComponent, inyectar: AuthService, Router
  
- [ ] **B.2** — Implementar método `onLogin()`:
  ```typescript
  onLogin() {
    if (!form.valid) return;
    loading = true;
    authService.login(email, password).subscribe({
      next: (result) => {
        // Redirigir según rol
        const roleRoutes = {
          'OBSERVER': '/observer/home',
          'COORDINATOR': '/coordinator/actas',
          'MASTER': '/master/config',
          'ADMIN': '/dashboard'
        };
        router.navigate([roleRoutes[result.user.rol]]);
      },
      error: (err) => {
        errorMessage = 'Email o contraseña incorrecto';
        loading = false;
      }
    });
  }
  ```

- [ ] **B.3** — Test: llamar onLogin() con datos válidos, verificar que navigation ocurre.

**Compuerta B:** Test pasa. Router.navigate es llamado con ruta correcta.

## Fase C: Error Handling y UX

- [ ] **C.1** — Agregar validaciones de UI:
  - mostrar `<div *ngIf="form.get('email').touched && form.get('email').errors">` 
  - mensajes: "Email es requerido", "Email inválido"

- [ ] **C.2** — Limpiar campo password si login falla.

- [ ] **C.3** — Test: login fallido, verificar que errorMessage se muestra y form.password se limpia.

**Compuerta C:** `ng test` sin errores. UI muestra errores correctamente.

## Qué NO hacer

- **No agregar recuperación de contraseña** — out of scope
- **No agregar sign up** — out of scope
- **No compilar para prod** — dev build es suficiente

## Compuertas

| Fase | Comando | Expected |
|------|---------|----------|
| A | `ng build` | exit 0 |
| B | `ng test` (onLogin flow) | Router.navigate called |
| C | `ng test` (validation + error) | error message shown, password cleared |

## Verificación por Mutación

- **V.1** — Comentar Router.navigate() → login exitoso pero no redirige → restaurar
- **V.2** — Comentar validators.required → form.valid = true aunque campo vacío → restaurar
