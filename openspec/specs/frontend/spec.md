# Spec — Frontend Auth & Routing (T-FE-02, T-FE-03)

## Overview

This spec covers the frontend authentication and authorization subsystem:
- **T-FE-03**: Core auth services (TokenService, AuthService, session management)
- **T-FE-02**: Routing guards and RBAC enforcement

---

## T-FE-03: AuthService y Manejo de Tokens

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

### Mutaciones (T-FE-03)

#### M1 — Si TokenService guarda en localStorage

Si editas TokenService para usar localStorage.setItem('token', token), el token debe persistir. Verificación: logout, recargar, token debe seguir ahí (detectar falla).

#### M2 — currentUser$ no emite en login

Si comentas currentUser$.next(user) en login(), currentUser$ debe quedar sin emitir. Verificación: en un test, suscribirse a currentUser$ y verificar que no emite nada.

### Garantías (T-FE-03)

- **G1:** Token vive en memoria, no localStorage
- **G2:** logout borra token y currentUser
- **G3:** refresh obtiene nuevo token
- **G4:** currentUser$ es observable reactivo

---

## T-FE-02: Routing con RBAC Guards

### S1 — Sin token, intenta acceder a /observer/home

```gherkin
GIVEN    usuario sin token en memoria
WHEN     navega a /observer/home
THEN     authGuard retorna '/login'
AND      navegador redirige a /login
```

### S2 — Token válido, rol OBSERVER, accede a /observer/home

```gherkin
GIVEN    usuario con token rol=OBSERVER
WHEN     navega a /observer/home
THEN     authGuard retorna true
AND      roleGuard retorna true
AND      componente HomeComponent carga
```

### S3 — Token válido, rol ADMIN, intenta acceder a /master/config

```gherkin
GIVEN    usuario con token rol=ADMIN
AND      ruta /master/config requiere MASTER
WHEN     navega a /master/config
THEN     authGuard retorna true
AND      roleGuard retorna false (rol no permitido)
AND      navegador redirige a /
```

### S4 — Lazy-load: feature no se carga hasta necesitarse

```gherkin
GIVEN    app inicializada
AND      rutas con loadChildren() para features
WHEN     se lista archivos cargados en browser
THEN     features/{observer,coordinator,master}/observer.routes.js no está cargado
WHEN     navega a /observer/home
THEN     archivo observer.routes.js se carga dinámicamente
```

### Mutaciones (T-FE-02)

#### M1 — Remover authGuard de una ruta

Si se quita `canActivate: [authGuard]` de /coordinator/*, debe ser accesible sin token. Verificación: intentar acceder sin token, debe cargar (detectar falla).

#### M2 — Cambiar roleGuard a permitir ADMIN en /master/*

Cambiar guard para que ADMIN pueda entrar a /master/*. Debe cargarse (luego restaurar).

### Garantías (T-FE-02)

- **G1:** Sin token → no acceso a rutas protegidas
- **G2:** Rol incorrecto → redirección
- **G3:** Lazy-load funciona (features se cargan bajo demanda)

---

## T-FE-04: HTTP Interceptors (JWT + Refresh Automático)

### S1 — JwtInterceptor agrega Authorization header

```gherkin
GIVEN    TokenService tiene token='abc123'
WHEN     llamas http.get('/api/observer/mis-actas')
AND      interceptors se ejecutan
THEN     request HTTP tiene header Authorization: Bearer abc123
```

### S2 — GET /api/observer/mis-actas sin token

```gherkin
GIVEN    TokenService token=null
WHEN     llamas http.get('/api/observer/mis-actas')
THEN     request NO tiene Authorization header (o es 'Bearer null')
AND      backend retorna 401
AND      RefreshInterceptor detecta 401
AND      llama authService.refresh()
```

### S3 — Refresh exitoso, reintento de request original

```gherkin
GIVEN    request original retorna 401
AND      refresh() retorna nuevo token
WHEN     RefreshInterceptor maneja el 401
THEN     request original se reintenta con nuevo token
AND      si reintento retorna 200, Observable emite ese 200
```

### S4 — Refresh falla (401), propaga error

```gherkin
GIVEN    request original retorna 401
AND      refresh() también retorna 401 (token inválido)
WHEN     RefreshInterceptor intenta refresh()
THEN     la cadena propaga error 401
AND      Observable del caller retorna error
```

## Mutaciones (T-FE-04)

### M1 — Remover JwtInterceptor

Si se quita JwtInterceptor, requests no tienen Authorization header. Verificación: hacer request, inspeccionar headers, no debe tener Authorization.

### M2 — RefreshInterceptor no reintenta en 401

Si se borra la lógica de reintento, 401 propaga directamente sin intentar refresh. Verificación: generar 401, debe fallar en lugar de reintentarse.

## Garantías (T-FE-04)

- **G1:** Authorization header se agrega automáticamente
- **G2:** 401 dispara refresh automático
- **G3:** Request original se reintenta con nuevo token
