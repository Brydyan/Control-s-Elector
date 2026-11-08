# Spec — API de autenticación (retrospectivo)

Dominio: `auth-api`. Change: `2026-09-07-t-be-09-authcontroller`.

---

## RF-AUTH-01 — `POST /login` devuelve el access en el cuerpo y el refresh en cookie

GIVEN credenciales válidas
THEN la respuesta es `200` con `accessToken`, `tokenType`, `expiresIn`, `sesionId` y el
usuario
AND el refresh viaja en una cookie `HttpOnly; Secure; SameSite=Strict; Path=/`
AND el refresh **no** aparece en el cuerpo

El access va al cuerpo porque el frontend lo guarda en memoria JS; el refresh va en cookie
httpOnly porque JS no debe poder leerlo. Es la separación que hace que un XSS no se lleve la
sesión entera.

**Mutación**: añadir el refresh al cuerpo de `AuthResponse`.
Debe caer un test que compruebe la forma de la respuesta.

---

## RF-AUTH-02 — Los permisos no viajan en el cuerpo del login

GIVEN un login correcto
THEN la respuesta no expone la lista de permisos del usuario

Van en el claim `perms` del JWT, para uso de `JwtAuthFilter` y `@authz`. El frontend obtiene
su menú por `GET /api/v1/menus/me`.

---

## RF-AUTH-03 — Credenciales inválidas dan un error genérico

GIVEN un email inexistente, una contraseña incorrecta o un usuario deshabilitado
THEN los tres casos devuelven el mismo error, sin distinguir cuál ocurrió

`AuthService` lanza `AuthException("Credenciales invalidas")` en los tres. Distinguirlos
permitiría enumerar qué cuentas existen.

**Mutación**: cambiar el mensaje del caso «usuario no existe» por uno específico.
Debe caer un test que compare las respuestas de los tres casos.

---

## RF-AUTH-04 — `POST /refresh` lee la cookie o el cuerpo, y rota

GIVEN una cookie `refresh_token` válida
THEN se emite un par nuevo y la cookie se reemplaza

GIVEN el mismo refresh usado por segunda vez
THEN se rechaza

GIVEN un token que no es de tipo refresh (sin `typ: "refresh"`)
THEN se rechaza

**Mutación**: quitar la comprobación de `typ`.
Debe caer un test que intente refrescar con un **access token**. Sin esa comprobación, un
access token robado se convertiría en sesión perpetua.

---

## RF-AUTH-05 — `POST /logout` revoca la sesión y borra la cookie

GIVEN una sesión activa
WHEN se llama logout
THEN la sesión queda `revocado = true`
AND la respuesta trae la cookie con `Max-Age=0`
AND los refresh posteriores fallan

`logout` está en las rutas `permitAll` de `SecurityConfig`: se puede llamar sin access token
válido, que es lo correcto —cerrar sesión debe funcionar aunque el access haya expirado— y
requiere de todos modos poseer el refresh.

**Alcance real, que hay que declarar**: el access token vigente **sigue funcionando hasta 15
minutos**. Ver `t-be-08-security-jwt` RF-SEC-07.

**Mutación**: quitar `auth.logout(refresh)` dejando sólo el borrado de cookie.
Debe caer un test que intente refrescar después del logout.

---

## RF-AUTH-06 — `GET /me` devuelve el usuario autenticado

GIVEN una petición con access token válido
THEN la respuesta es `200` con los datos del usuario del token

**Este requisito puede estar incumplido hoy.** El método hace:

```java
String email = auth.getName();
Usuario u = usuarios.findByEmail(email).orElseThrow();
```

`JwtAuthFilter` pone un `UserPrincipal` como principal. `getName()` sobre un principal que no
es `UserDetails` ni `String` devuelve `principal.toString()`, que en un record es
`UserPrincipal[id=..., email=...]`, no el email. Entonces `findByEmail` falla y el
`orElseThrow()` da `500`.

**Se resuelve ejecutando, no razonando**: una petición real con un token válido. Si devuelve
`500`, es un defecto en el endpoint que el frontend usa para saber quién es el usuario — y no
lo cubre ningún test.

Si funciona, hay que entender **por qué** funciona y dejarlo escrito: dependerá de un detalle
de `UserPrincipal` que nadie debería tener que redescubrir.

---

## RF-AUTH-07 — Sólo MASTER crea usuarios

GIVEN un usuario que no es MASTER
WHEN llama `POST /register`
THEN recibe `403`

No hay auto-registro público: sería un vector de alta de cuentas en un sistema electoral.

**Mutación**: quitar el `@PreAuthorize` del método.
Debe caer el test de OBSERVER intentando registrar → 403.

---

## RF-AUTH-08 — El email generado es único, o el fallo es comprensible

GIVEN dos usuarios cuyos datos producen el mismo email generado
WHEN se registra el segundo
THEN el error explica la colisión, no es una violación de constraint cruda

El formato es «3 letras del nombre + 3 del apellido + 3 del rol @control.ttcs».
`Juan Perez` OBSERVER y `Juana Perezoso` OBSERVER generan el mismo.

Con ~450 JRVs y varios veedores por cantón, las colisiones de tres letras no son
hipotéticas. Y ocurren durante el alta masiva previa a la elección, que es cuando menos
tiempo hay para diagnosticar un error de base de datos.

**A decidir**: sufijo numérico en caso de colisión, email explícito como parámetro opcional,
o al menos un mensaje que diga qué pasó y cómo resolverlo.

---

## RF-AUTH-09 — MASTER puede revocar todas las sesiones de un usuario

GIVEN un MASTER
WHEN revoca las sesiones de un usuario
THEN todas las sesiones activas de ese usuario quedan revocadas y sus refresh dejan de servir

GIVEN un usuario que no es MASTER
THEN recibe `403`

**Este endpoint no existe.** `backend.md:148` lo promete como
`DELETE /api/auth/sessions/{usuarioId}`, y `CLAUDE.md` lo eleva a regla que no se rompe:
«Solo `MASTER` revoca tokens de sesión».

`AuthController` expone `login`, `refresh`, `logout`, `me` y `register`. `AuthService` sólo
tiene `logout(refreshToken)`, que revoca la sesión de quien llama.

**Consecuencia operativa**: si el dispositivo de un veedor se pierde o se compromete el día
de la elección, no hay forma de cortar su acceso. Hay que implementarlo, o retirar la promesa
de los dos documentos — pero entonces hay que decir qué se hace en ese escenario.
