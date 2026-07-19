# Flujo 03 — Autenticación del Observador (Login)

Escenario: observador ingresa credenciales y obtiene access token + refresh token.

Access token: corto plazo (15 min), JWT stateless.
Refresh token: largo plazo (8h — duración del proceso electoral), opaco, persistido en DB para revocación.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant API as Spring Boot API
    participant SEC as Spring Security
    participant PG as PostgreSQL

    OBS->>APP: Ingresa email + contraseña
    APP->>API: POST /api/v1/auth/login {email, password}

    API->>SEC: AuthenticationManager.authenticate()
    SEC->>PG: SELECT usuario WHERE email = ?
    PG-->>SEC: Usuario {id, publicId, email, rol, password_hash}
    SEC->>SEC: Verifica BCrypt hash

    alt Credenciales válidas
        SEC-->>API: Authentication exitosa

        API->>API: Genera Access Token JWT<br/>{sub: publicId, rol, perms[], exp: +15min}
        API->>API: Genera Refresh Token opaco<br/>{token_hash, exp: +8h}
        API->>PG: INSERT refresh_tokens {user_id, token_hash, expires_at}

        API->>PG: SELECT usuario con rol + permisos
        PG-->>API: Usuario completo

        API-->>APP: 200 OK<br/>{accessToken, tokenType: Bearer, expiresInSeconds,<br/>sesionId, usuario: {id, email, rol, permisos[]}}
        APP->>APP: accessToken → memoria (variable)
        APP->>APP: refreshToken en cookie httpOnly (navegador)
        APP->>API: GET /api/v1/menus/me (con Bearer token)
        API-->>APP: {menus} arbol con permisos del usuario
        APP->>APP: Renderiza sidebar según menus
        APP-->>OBS: ✅ Bienvenido, redirige a /observer o /dashboard

    else Credenciales inválidas
        SEC-->>API: BadCredentialsException
        API-->>APP: 401 Unauthorized {error, message}
        APP-->>OBS: ❌ Email o contraseña incorrectos
    end
```

## Notas de Seguridad

| Token | Almacenamiento | Duración | Seguridad |
|---|---|---|---|
| Access Token (JWT) | Memoria JS (no localStorage) | 15 min | Stateless. Expira rápido. Si robado → ventana mínima. |
| Refresh Token (opaco) | Cookie httpOnly + Secure + SameSite=Strict | 8h (proceso electoral) | No accesible desde JS (secure contra XSS). Revocable en DB. |

**RBAC a dos niveles:**
1. **JWT claim "perms":** lista de permisos en accesToken (rápido, usado en @PreAuthorize)
2. **GET /api/v1/menus/me:** arbol de menus visibles según permisos (RenderUI en FE)
