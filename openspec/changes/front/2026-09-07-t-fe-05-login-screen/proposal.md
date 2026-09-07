# Proposal — T-FE-05: Pantalla de Login

## Qué se construye
Componente LoginComponent. El usuario ingresa email y contraseña. Si éxito, es redirigido automáticamente a su área según rol.

## Por qué
- Punto de entrada único para todos los roles.
- Redirección automática al dashboard del rol.
- Validación en tiempo real (email, contraseña requerida).

## Alcance

| Elemento | Incluye | No incluye |
|----------|---------|-----------|
| Componente | LoginComponent (email + password) | cambio de contraseña |
| Formulario | Reactivo, validadores built-in | custom validators |
| UI | Input campos, botón, mensajes de error | estilos avanzados |
| Lógica | login(), navegación al rol | recupeación de contraseña |
| Error | Mostrar mensaje si credenciales fallan | logs detallados |

## Criterio de aceptación

- [ ] Componente LoginComponent en features/auth/ o shared/
- [ ] Formulario con email, password (validators: required, email válido)
- [ ] Botón "Login" dispara authService.login()
- [ ] Si éxito, redirige a /observer/home, /coordinator/actas, etc. según rol
- [ ] Si error, muestra mensaje "Email o contraseña incorrecto"
- [ ] Loading spinner mientras se procesa el login

## Fuera de alcance

- Recuperación de contraseña
- Registro de usuarios
- Social login (Google, GitHub)

## Dependencias

- T-FE-02 (rutas)
- T-FE-03 (AuthService)
- T-FE-04 (interceptors)

## Decisiones a tomar

**D1 — dónde vive LoginComponent:** En `features/auth/login/` o `shared/auth-login/`. Elegir: compartido o feature.

**D2 — input password:** `type="password"` o `type="text"` con toggle visibility? Solo password por ahora.
