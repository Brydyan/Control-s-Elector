# Spec — Seguridad y JWT (retrospectivo)

Dominio: `seguridad`. Change: `2026-09-07-t-be-08-security-jwt`.

---

## RF-SEC-01 — Sin token válido no se accede a nada protegido

GIVEN una petición sin cabecera `Authorization`
THEN la respuesta es `401` con cuerpo JSON

GIVEN un token con firma inválida, expirado o malformado
THEN `401`. `JwtAuthFilter` limpia el contexto y `SecurityConfig` responde por el
`AuthenticationEntryPoint`

Rutas públicas, y sólo ésas: `/actuator/health`, `/actuator/info`,
`/api/v1/auth/{login,refresh,logout}`, y la superficie de OpenAPI/Swagger.

**Mutación**: cambiar `.anyRequest().authenticated()` por `.permitAll()`.
Debe caer el test de petición sin token → 401.

---

## RF-SEC-02 — La autorización se decide por permiso, no por nombre de rol

GIVEN un usuario autenticado
THEN sus authorities son sus permisos `recurso:accion`, tomados del claim `perms` del JWT

La decisión se toma con `@authz.has('acta:upload')` o `hasAnyRole(...)`, nunca comparando
contra `rol.nombre` con un `equals`.

**Por qué importa** (§4.4 del playbook): la tabla `rol` tiene `nombre` editable y su única
restricción es `uk_rol_nombre`. Un control que compare contra la cadena `"MASTER"` se abre
renombrando un rol — sin error y sin log.

**Estado a verificar**: `CoordinatorController` usa `hasAnyRole(...)` y `ObserverController`
usa `@authz.has(...)`. Dos mecanismos conviviendo en el mismo dominio (§4.2). Hay que
comprobar endpoint por endpoint que ninguno quedó sin ninguno de los dos.

---

## RF-SEC-03 — El secreto de firma tiene fuerza suficiente

GIVEN un `app.jwt.secret` de menos de 32 caracteres
THEN la aplicación **no arranca** (`IllegalStateException` en el constructor de `JwtService`)

Fallar al arrancar es lo correcto: un secreto débil que arranca es un sistema comprometido
que parece sano.

**Mutación**: quitar la comprobación de longitud.
Debe caer el test que arranca el contexto con un secreto corto.

---

## RF-SEC-04 — El refresh se guarda como hash, nunca en claro

GIVEN un login
THEN la fila de `sesion` guarda `HMAC-SHA256(refresh + pepper)` en base64url, no el token

Quien lea la base no puede reconstruir un refresh válido.

**Mutación**: guardar el token en claro en `refreshHash`.
Debe caer un test que compruebe que el valor almacenado no es igual al token emitido.

---

## RF-SEC-05 — El refresh rota en cada uso

GIVEN un refresh token usado una vez
WHEN se intenta usar de nuevo
THEN se rechaza: la sesión guarda ya el hash del nuevo

Acota el daño de un refresh robado a un solo uso.

**Mutación**: no actualizar `refreshHash` en `AuthService.refresh`.
Debe caer el test de reutilización.

---

## RF-SEC-06 — Una sesión revocada no emite tokens nuevos

GIVEN una sesión con `revocado = true` o expirada
WHEN se intenta refrescar
THEN se rechaza con `AuthException`

**Mutación**: quitar el `if (!sesion.activa())`.
Debe caer el test de refresh sobre sesión revocada.

---

## RF-SEC-07 — El alcance real del logout está declarado

GIVEN un logout
THEN la sesión queda revocada y ningún refresh futuro funciona
AND **el access token emitido sigue siendo válido hasta su expiración (15 min)**

`JwtAuthFilter:30-34` lo documenta en un comentario. `CLAUDE.md` promete «revocación global
(logout)», que es más de lo que ocurre.

La limitación es defendible —revocación inmediata exigiría consultar la sesión en cada
petición o mantener una caché— pero tiene que estar **declarada donde se lee la promesa**, no
sólo en un comentario del filtro.

---

## RF-SEC-08 — El TTL y la naturaleza del refresh son los documentados

GIVEN la configuración de tokens
THEN `CLAUDE.md` describe lo que el código hace

Divergencias medidas el 2026-09-07:

| `CLAUDE.md` | Código |
|---|---|
| refresh «opaco» | JWT firmado con claim `typ: "refresh"` |
| refresh de **8h** | `app.jwt.refresh-ttl:7d`, cookie `7*24*3600`, `Sesion.expiraEn` +7 días |
| access 15 min | `app.jwt.access-ttl:15m` ✅ |
| access sólo en memoria JS | el frontend lo mantiene en memoria ✅ |

El TTL del refresh es una decisión de seguridad: 8 horas se justificaba por la duración del
proceso electoral. 7 días es lo que hay. Hay que decidir cuál se quiere y que las dos fuentes
digan lo mismo.

---

## RF-SEC-09 — CORS permite el origen del frontend, con credenciales

GIVEN una petición desde `https://control.twintailcs.xyz` a `https://api.twintailcs.xyz`
THEN la respuesta trae `Access-Control-Allow-Origin` con ese origen exacto
AND `Access-Control-Allow-Credentials: true`
AND el preflight `OPTIONS` se responde correctamente

**Hoy `SecurityConfig:43` hace `.cors(AbstractHttpConfigurer::disable)`.** En producción el
frontend y la API son orígenes distintos, y `auth.repository.ts` usa `withCredentials: true`
para la cookie httpOnly de refresh: sin CORS con credenciales, **el login no funciona**.

En local no se ve porque `proxy.conf.json` redirige `/api/*` a `localhost:8081` y todo es
mismo origen. Es §4.5 del playbook: el lado permisivo es el de desarrollo, y el defecto
aparece el día del despliegue.

`Access-Control-Allow-Origin: *` **no sirve** aquí: con credenciales, el navegador exige un
origen explícito.

**Verificación**: una petición real con cabecera `Origin` distinta, comprobando las cabeceras
de respuesta. No leer la configuración: ejecutarla.

---

## RF-SEC-10 — La superficie de OpenAPI no está abierta en producción

GIVEN el entorno de producción
THEN `/v3/api-docs/**` y `/swagger-ui/**` no son accesibles sin autenticación

`SecurityConfig:53-65` los expone con `permitAll` y un comentario que dice «en prod
restringir por IP/gateway (no exponer a internet abierto)». **No hay nada que lo haga.**

Es §4.3: un comentario que describe una protección que no existe. La API expuesta documenta
cada endpoint, sus roles y su forma — es el mapa para quien quiera atacarla.

---

## RF-SEC-11 — RLS actúa durante la petición, o se declara que no

GIVEN una petición autenticada
THEN `current_setting('app.current_user_id', true)` devuelve el id interno en la misma
transacción que usan los repositorios

Tres razones para dudar de que hoy se cumpla:

1. `RlsInterceptor` ejecuta `SET LOCAL` desde un filtro de servlet, **fuera** de la
   transacción que abrirá después el repositorio; `SET LOCAL` sólo vive dentro de la suya.
2. Captura cualquier excepción y deja pasar la petición con un `warn`: si falla, no hay error,
   hay ausencia silenciosa de RLS.
3. No tiene **ningún** test.

Y `ActaService:77-82` afirma que RLS es el control de acceso vigente tras haberse quitado la
comprobación de pre-asignación de JRV.

La medición está en `t-be-15-tests-integracion` RF-TEST-05. Si RLS no actúa, es CRITICAL: no
por el hueco en sí —el permiso JWT sigue ahí— sino porque hay una decisión arquitectónica
apoyada en una defensa que nadie comprobó.

---

## RNF-SEC-01 — El SQL del interceptor no se construye concatenando

GIVEN `RlsInterceptor`
THEN el valor del usuario viaja como parámetro, no concatenado en la cadena SQL

```java
jdbcTemplate.execute("SET LOCAL app.current_user_id = '" + userId + "'");
```

Hoy `userId` es un `Long` resuelto de la base, así que no es explotable. Pero es la forma que
enseña a la siguiente persona a hacerlo con un valor que sí venga del exterior. Se corrige
porque es barato, no porque haya una vulnerabilidad viva.
