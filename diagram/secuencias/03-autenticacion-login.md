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

    OBS->>APP: Ingresa cédula + contraseña
    APP->>API: POST /api/auth/login {cedula, password}

    API->>SEC: AuthenticationManager.authenticate()
    SEC->>PG: SELECT usuario WHERE cedula = ?
    PG-->>SEC: Usuario {id, rol, jrv_asignada_id, password_hash}
    SEC->>SEC: Verifica BCrypt hash

    alt Credenciales válidas
        SEC-->>API: Authentication exitosa

        API->>API: Genera Access Token JWT<br/>{sub, rol: OBSERVER, jrv_id, exp: +15min}
        API->>API: Genera Refresh Token opaco<br/>{token_hash, exp: +8h}
        API->>PG: INSERT refresh_tokens {user_id, token_hash, expires_at}

        API-->>APP: 200 OK<br/>{accessToken, refreshToken, jrv_asignada, nombre}
        APP->>APP: accessToken → memoria (variable)
        APP->>APP: refreshToken → localStorage (persistente)
        APP-->>OBS: ✅ Bienvenido, redirige a formulario JRV

    else Credenciales inválidas
        SEC-->>API: BadCredentialsException
        API->>PG: Registra intento fallido (rate limiting)
        API-->>APP: 401 Unauthorized
        APP-->>OBS: ❌ Cédula o contraseña incorrectos
    end
```

## Notas de Seguridad

| Token | Almacenamiento | Duración | Por qué |
|---|---|---|---|
| Access Token (JWT) | Memoria JS (no localStorage) | 15 min | Si robado, ventana mínima de daño |
| Refresh Token (opaco) | localStorage | 8h (proceso electoral) | Revocable desde DB si se detecta anomalía |
