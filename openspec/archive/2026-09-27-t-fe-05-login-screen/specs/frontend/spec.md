# Spec — T-FE-05: Pantalla de Login

## Escenarios

### S1 — Login exitoso como OBSERVER

```gherkin
GIVEN    LoginComponent cargado en /login
WHEN     usuario ingresa email 'observer@example.com' + password 'pass123'
AND      hace click en "Login"
AND      backend retorna {accessToken, user: {id, rol: 'OBSERVER', email}}
THEN     AuthService.login() emite {token, user}
AND      LoginComponent redirige a /observer/mis-jrvs
```

### S2 — Login exitoso como COORDINATOR

```gherkin
GIVEN    usuario ingresa email 'coordinator@example.com' + password 'pass123'
WHEN     hace click en "Login"
AND      backend retorna rol: 'COORDINATOR'
THEN     LoginComponent redirige a /dashboard
```

### S3 — Credenciales incorrectas

```gherkin
GIVEN    usuario ingresa email incorrecto o password incorrecto
WHEN     hace click en "Login"
AND      backend retorna 401
THEN     formulario muestra mensaje "Email o contraseña incorrecto"
AND      campo password se limpia
AND      usuario permanece en /login
```

### S4 — Validación en tiempo real

```gherkin
GIVEN    LoginComponent cargado
WHEN     usuario deja campo email vacío y hace blur
THEN     mensaje de error "Email es requerido"
WHEN     ingresa texto no válido como email (ej: 'abc')
THEN     mensaje "Email inválido"
WHEN     ingresa email válido (ej: 'test@ex.com')
THEN     error desaparece
```

### S5 — Loading spinner durante login

```gherkin
GIVEN    formulario está esperando respuesta del servidor
WHEN     hace click en "Login"
THEN     botón "Login" se deshabilita
AND      spinner gira mientras AuthService.login() está pending
AND      cuando respuesta llega, spinner desaparece
```

## Mutaciones

### M1 — Comentar redirección en LoginComponent

Si se quita el Router.navigate() tras login(), usuario permanece en /login tras éxito. Verificación: login exitoso, debe estar en /login (detectar falla).

### M2 — Quitañ validación de email

Si se quita `Validators.email`, campo acepta 'abc' como email válido. Verificación: ingresa 'abc', error debe desaparecer (detectar falla).

## Garantías

- **G1:** Login redirects según rol
- **G2:** Validación previene envíos inválidos
- **G3:** Errores se muestran solo si campo fue tocado
