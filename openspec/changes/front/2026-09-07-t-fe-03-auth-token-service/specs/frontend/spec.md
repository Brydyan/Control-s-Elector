# Spec — T-FE-03: AuthService y Manejo de Tokens

## Escenarios

### S1 — Login exitoso

```gherkin
GIVEN    AuthService inyectado
AND      endpoint POST /api/auth/login disponible
WHEN     llamas authService.login('user@example.com', 'pass123')
AND      backend retorna {accessToken: '...', user: {id, rol, email}}
THEN     TokenService.getToken() retorna el accessToken
AND      AuthService.currentUser$ emite {id, rol, email}
AND      Observable resuelve sin error
```

### S2 — Logout

```gherkin
GIVEN    usuario logged in
AND      TokenService tiene token
AND      currentUser$ tiene user info
WHEN     llamas authService.logout()
THEN     TokenService.getToken() retorna null
AND      currentUser$ emite null
```

### S3 — Refresh de token

```gherkin
GIVEN    TokenService tiene token expirado (pero no inválido aún)
AND      endpoint POST /api/auth/refresh disponible
WHEN     llamas authService.refresh()
AND      backend retorna nuevo {accessToken: '...'}
THEN     TokenService.getToken() es el nuevo token
AND      Observable resuelve sin error
```

### S4 — Token NO persiste en localStorage

```gherkin
GIVEN    usuario logged in
WHEN     inspeccionas localStorage en DevTools
THEN     no hay clave 'token' o similar
AND      token solo vive en memoria (TokenService)
```

## Mutaciones

### M1 — Si TokenService guarda en localStorage

Si editas TokenService para usar localStorage.setItem('token', token), el token debe persistir. Verificación: logout, recargar, token debe seguir ahí (detectar falla).

### M2 — currentUser$ no emite en login

Si comentas currentUser$.next(user) en login(), currentUser$ debe quedar sin emitir. Verificación: en un test, suscribirse a currentUser$ y verificar que no emite nada.

## Garantías

- **G1:** Token vive en memoria, no localStorage
- **G2:** logout borra token y currentUser
- **G3:** refresh obtiene nuevo token
- **G4:** currentUser$ es observable reactivo
