# Flujo 04 — Access Token Expirado: Renovación con Refresh Token

Escenario: access token vence cada 15 min; el interceptor lo renueva transparentemente sin interrumpir al observador.

```mermaid
sequenceDiagram
    actor OBS as Observador
    participant APP as App Angular/Ionic
    participant INT as HTTP Interceptor
    participant API as Spring Boot API
    participant PG as PostgreSQL

    OBS->>APP: Intenta enviar acta
    INT->>INT: Adjunta Access Token (expirado)
    INT->>API: POST /api/actas + Bearer {access_token_expirado}

    API-->>INT: 401 Unauthorized {code: ACCESS_TOKEN_EXPIRED}

    INT->>INT: Detecta 401, pausa requests pendientes
    INT->>INT: Lee refresh token desde localStorage

    alt Refresh token válido y no expirado
        INT->>API: POST /api/auth/refresh {refreshToken}
        API->>PG: SELECT refresh_tokens WHERE token_hash = ? AND expires_at > NOW()
        PG-->>API: Token válido {user_id, rol, jrv_id}
        API->>API: Genera nuevo Access Token JWT {exp: +15min}
        API-->>INT: 200 OK {newAccessToken}

        INT->>INT: Actualiza access token en memoria
        INT->>API: Reintenta POST /api/actas + Bearer {newAccessToken}
        API-->>APP: 201 Created
        APP-->>OBS: ✅ Acta registrada (renovación transparente)

    else Refresh token expirado o revocado
        API->>PG: SELECT refresh_tokens WHERE token_hash = ?
        PG-->>API: No encontrado o expires_at vencido
        API-->>INT: 401 Unauthorized {code: REFRESH_TOKEN_EXPIRED}

        INT->>INT: Limpia access token y refresh token
        INT->>APP: Emite evento SessionExpired
        APP-->>OBS: ⚠️ Sesión finalizada, ingresa nuevamente
        APP->>APP: Redirige a /login
    end
```

## Flujo 04b — Revocación Manual (Logout o Anomalía Detectada)

```mermaid
sequenceDiagram
    actor MASTER as Master
    participant API as Spring Boot API
    participant PG as PostgreSQL
    participant APP as App Observador

    MASTER->>API: POST /api/auth/revoke {userId} + Bearer token
    API->>API: Valida rol MASTER

    API->>PG: DELETE refresh_tokens WHERE user_id = ?
    PG-->>API: Tokens eliminados

    API-->>MASTER: 200 OK {revocados: 1}

    Note over APP,PG: Próxima vez que el observador intente renovar...

    APP->>API: POST /api/auth/refresh {refreshToken}
    API->>PG: SELECT refresh_tokens WHERE token_hash = ?
    PG-->>API: No encontrado
    API-->>APP: 401 {code: REFRESH_TOKEN_EXPIRED}
    APP->>APP: Redirige a /login
```
