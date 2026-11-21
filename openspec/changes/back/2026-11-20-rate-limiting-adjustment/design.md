# Design — Rate Limiting: motor distribuido y ajuste de políticas (sc-521)

Reemplaza el alcance de `odd/tasks/rate-limiting-post-caching-adjustment.md`.

## Contexto: qué estaba mal

| Problema | Consecuencia |
|---|---|
| `@RateLimited` sin `@Nonbinding`: el interceptor solo se ligaba a métodos con `limit=1, windowSeconds=1` | ArC eliminaba el interceptor por no usado. **Ningún endpoint estaba limitado** (verificado en `generated-bytecode.jar`) |
| `checkUserLimit` / `checkGlobalLimit` sin llamadores; propiedades `app.ratelimit.*` sin leer | USER_LIMIT no se aplicaba en ningún lado |
| GET + SET no atómico en Redis; excepciones tragadas sin log | Conteo corto bajo concurrencia; caídas de Redis invisibles |
| IP tomada del primer valor de `X-Forwarded-For` | El cliente lo escribe: límites por IP evadibles; `AuthResource.ipDe()` auditaba `"sistema"` |
| Sin Redis desplegado en producción | Todo chequeo fallaba y se permitía |
| `PUT /user/update-personal-email` verifica contraseña sin límite | Oráculo para adivinar la contraseña con un token robado |

Producción: backend en Railway (3 réplicas, imagen nativa), frontend en Cloudflare Pages, Neon, R2, DNS y proxy en Cloudflare.

## Decisiones

| # | Decisión | Motivo |
|---|---|---|
| D1 | Redis en la red privada de Railway | Contadores compartidos entre réplicas con latencia < 1 ms. Un Redis externo suma 10-50 ms por request |
| D2 | Si Redis falla: `local-fallback` (login, reauth, actas, DPO) o `allow` (lecturas). Ninguna política bloquea | Una caída de Redis no puede tumbar el login ni el envío de actas el día de la elección |
| D3 | IP confiable = `CF-Connecting-IP` **solo** con el header secreto `X-Origin-Auth` válido. Sin secreto, las políticas por IP no se aplican | Sobre la IP de Cloudflare todos compartirían contador. El dominio `*.up.railway.app` permitiría falsificar `CF-Connecting-IP` |
| D4 | Login limitado por cuenta en `LoginUseCase`: `login-account-ip` (email+IP, 5/5m) y `login-account` (email, 30/15m); solo cuentan fallos; chequeo antes de BCrypt | Frena adivinar contraseñas sin que un atacante pueda dejar afuera al observador real. Los recintos comparten IP (NAT) |
| D5 | export-data se queda con su límite en base (`user_export_log`) | Auditado y sobrevive reinicios; dos reglas para lo mismo confunden |
| D6 | `GET /observer/jrvs` y `/jrv/{id}/cargos` exigen `acta:read` | Declara la matriz sin quitar acceso: el coordinador usa `/observer/jrvs` |
| D7 | Métricas Micrometer en la interfaz de management (puerto 9180); health se queda en el principal | El puerto de management no se publica. 9000 choca con RustFS en CI |
| D8 | `@RateLimited` como interceptor ArC; `user-global` como `@ServerRequestFilter` | La API es nativa: ArC resuelve bindings al compilar. Un filtro que lee la anotación por reflexión puede fallar en nativo |
| D9 | USER_LIMIT se queda en 1000/min (no 2000) | Un observador real usa 10-50 req/min. El 45% de reducción por caché no tenía medición detrás |

## Diseño

```
common/web/             RateLimited (@Nonbinding value = politica)
                        RateLimitInterceptor (ArC, PLATFORM_BEFORE+100: antes de @Transactional)
                        UserGlobalRateLimitFilter (@ServerRequestFilter)
                        ClientAddressResolver + ClientIpConfig (app.client-ip.*)
                        RateLimitExceededExceptionMapper -> 429
common/application/     RateLimiter (puerto), RateLimitPolicy, RateLimitDecision,
  ratelimit/            RateLimitConfig (@ConfigMapping app.ratelimit), RateLimitPolicies
auth/application/       LoginAttemptGuard (politicas key=custom del login)
common/infrastructure/  ResilientRateLimiter (timeout + fallback + metricas)
  ratelimit/            RedisRateLimitBackend (Lua INCR+PEXPIRE atomico)
                        LocalRateLimitBackend (memoria acotada a 10.000 contadores)
```

- Claves: `rl:v1:<politica>:<sha256(identidad)>`. Sin datos personales en claro; prefijo separado del caché (`cache:`).
- Toda clave `rl:*` tiene TTL → compatible con `maxmemory-policy volatile-ttl`.
- 429 con `Retry-After`, `RateLimit-Limit`, `RateLimit-Remaining` y el shape de `ApiErrorResponse`. CORS expone esos headers.
- Redis fuera del readiness (`quarkus.redis.health.enabled=false`): su caída ya está tolerada.

## Políticas

| Política | Endpoint | Permiso | Clave | Límite | Falla |
|---|---|---|---|---|---|
| `login-account-ip` | `POST /auth/login` (caso de uso) | público | email+IP, solo fallos | 5 / 5m | local |
| `login-account` | `POST /auth/login` (caso de uso) | público | email, solo fallos | 30 / 15m | local |
| `login-ip` | `POST /auth/login` | público | IP | 100 / 5m | local |
| `reauth` | `PUT /user/update-personal-email` | autenticado | usuario | 5 / 15m | local |
| `contact-dpo` | `POST /legal/contact-dpo` | público | IP | 1 / 30s | local |
| `observer-jrvs` | `GET /observer/jrvs`, `GET /observer/jrv/{id}/cargos` | `acta:read` | usuario | 60 / 1m | allow |
| `observer-actas` | `POST /observer/actas` | `acta:upload` | usuario | 50 / 1m | local |
| `config-read` | `GET /configuracion/{cargos,partidos,candidatos}` | autenticado | usuario | 120 / 1m | allow |
| `admin-usuarios` | `GET /admin/usuarios` | `usuario:read`, MASTER | usuario | 60 / 1m | allow |
| `user-global` | todo endpoint autenticado | — | usuario | 1000 / 1m | allow |

Variables: `APP_RATELIMIT_<POLITICA>_LIMIT` / `_WINDOW`. Ver `.env.example`.

## Tareas de infraestructura (sin código)

1. Railway: servicio Redis en la red privada (128 MB, `maxmemory-policy volatile-ttl`) y `REDIS_HOSTS`.
2. Cloudflare: Transform Rule que agrega `X-Origin-Auth: <secreto>` hacia `api.brydyan.me`; el mismo valor en `APP_ORIGIN_AUTH_SECRET`.
3. Railway: borrar el dominio `*.up.railway.app` del backend (verificar antes que nada lo use).

El código se puede desplegar antes que estas tareas: sin Redis protege por réplica; sin secreto las políticas por IP no se aplican.

## Verificación

- Unit (sin Docker): `RateLimitPolicyTest`, `LocalRateLimitBackendTest`, `ResilientRateLimiterTest`, `RateLimitedAnnotationsTest` (toda `@RateLimited` nombra una política existente con key `ip`/`user`).
- IT (Testcontainers): `RedisRateLimitBackendIT` (atomicidad con 50 hilos, TTL, convivencia con `cache:*`), `RateLimitingIT` (login, NAT, atacante vs. observador, IP falsificada, reauth, 403/429 por endpoint, `user-global`).
- Bytecode: `RateLimitInterceptor_Bean` generado y ligado a los 8 recursos anotados.

## Fuera de alcance

Migrar `ActivationRateLimiter`, `InvitationRateLimiter` y `PasswordChangeRateLimiter` a Redis (hoy en memoria y ahora solo por IP confiable); quitar el parámetro ignorado de `ContextoSolicitud.ip(String)`; destino de las métricas; `BYPASSRLS` en Neon; desajuste `confirmPassword`/`password` en `update-personal-email` (frontend vs. backend).
