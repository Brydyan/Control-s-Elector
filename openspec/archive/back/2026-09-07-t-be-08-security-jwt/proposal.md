# Proposal — T-BE-08 · Spring Security + JWT

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-08.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`SecurityConfig`, `JwtService`, `JwtAuthFilter`, `UserPrincipal`, `CurrentUser`, `Authz`,
`RlsInterceptor` y el modelo de sesión persistida.

## Estado real

La arquitectura implementada es **mejor** que la del task doc: JOSE (nimbus) en vez de jjwt,
`Sesion` con `refreshHash` HMAC y rotación por uso, permisos `recurso:accion` como
authorities, y el bean `@authz` para expresiones SpEL. El doc está desactualizado en su favor.

Pero **ninguna de sus garantías tiene tests**: `JwtServiceTest` cubre 3 casos de firma;
`JwtAuthFilter`, `CurrentUser`, `Authz` y `RlsInterceptor` no tienen ninguno.

## Los cinco hallazgos

**1. CORS está deshabilitado, y en producción el frontend es cross-origin.**
`SecurityConfig:43` hace `.cors(AbstractHttpConfigurer::disable)`. En producción el frontend
vive en `control.brydyan.me` y la API en `api.brydyan.me`: **son orígenes distintos**.
Además `auth.repository.ts` usa `withCredentials: true` para la cookie de refresh, lo que
exige `Access-Control-Allow-Credentials` y un origen explícito (nunca `*`).

En local no se ve: `proxy.conf.json` redirige `/api/*` a `localhost:8081`, así que todo es
mismo origen y funciona. Es §4.5 del playbook en su forma más cara — el defecto sólo aparece
el día del despliegue.

**2. El TTL del refresh no es el documentado.** `CLAUDE.md` dice «Refresh token opaco — 8h
(duración proceso electoral)». El código usa **7 días**: `app.jwt.refresh-ttl:7d`,
`REFRESH_COOKIE_MAX_AGE_SEC = 7 * 24 * 3600`, y `Sesion.expiraEn = now + 7 días`. Y el
refresh no es opaco: es un JWT firmado con `typ: "refresh"`.

Dos afirmaciones de `CLAUDE.md` sobre seguridad que el código contradice.

**3. `RlsInterceptor` puede ser un no-op.** Hace `SET LOCAL app.current_user_id` desde un
filtro de servlet — **fuera** de la transacción que después abre el repositorio, y `SET LOCAL`
sólo vive dentro de su transacción. Además captura cualquier excepción y deja pasar la
petición con un `warn`. Y construye el SQL por concatenación de cadena.

**4. El logout no revoca el access token.** `JwtAuthFilter:30-34` lo documenta: la ventana de
revocación es de 15 minutos. `CLAUDE.md` promete «revocación global (logout)». Es una
limitación razonable y está sin declarar fuera de un comentario de código.

**5. Swagger es público.** `SecurityConfig:56-65` expone `/v3/api-docs/**` y `/swagger-ui/**`
con `permitAll`, con un comentario que dice «en prod restringir por IP/gateway». No hay nada
que lo haga.

## Alcance

- Escribir las garantías de autenticación y autorización, y verificarlas por mutación.
- Resolver CORS antes de que llegue el despliegue.
- Alinear `CLAUDE.md` con el mecanismo real.

## Fuera de alcance

- **No** se reescribe el modelo de sesión. Funciona y es mejor que el documentado.
- **No** se arregla RLS aquí: se mide en `t-be-15-tests-integracion` RF-TEST-05 y se decide
  con el resultado.

## Criterio de cierre

1. Cada garantía del spec tiene su mutación ejecutada.
2. CORS resuelto, **verificado contra un origen distinto**, no razonado.
3. `CLAUDE.md` dice la verdad sobre el TTL, la naturaleza del refresh y el alcance del logout.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
