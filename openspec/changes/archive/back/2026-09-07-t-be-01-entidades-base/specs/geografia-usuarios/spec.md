# Spec — Geografía y usuarios (retrospectivo)

Dominio: `geografia-usuarios`. Change: `2026-09-07-t-be-01-entidades-base`.
Autoridad: el código y el esquema Liquibase.

---

## RF-GEO-01 — La jerarquía geográfica es completa y obligatoria

GIVEN una JRV
THEN pertenece a una parroquia, que pertenece a un cantón, que pertenece a una provincia
AND ninguna de esas referencias admite `null`

La jerarquía decide qué cargos aplican a cada mesa (`JRV_CARGO`, `CLAUDE.md`): una JRV sin
parroquia no puede saber si le corresponden vocales de junta parroquial rural.

**Mutación**: hacer `nullable = true` la FK de `Jrv` a `Parroquia`.
Debe caer un test que intente persistir una JRV huérfana.

---

## RF-GEO-02 — La zona de la JRV y el tipo de parroquia distinguen urbano de rural

GIVEN una parroquia
THEN tiene tipo `URBANA` o `RURAL`

GIVEN una JRV
THEN tiene zona `URBANA` o `RURAL`

De aquí salen Concejal Urbano vs Concejal Rural y los vocales de junta parroquial, que sólo
existen en parroquias rurales.

**Escenario que hay que fijar**: ¿puede una JRV con zona `URBANA` estar en una parroquia
`RURAL`? Hoy nada lo impide. Si es imposible en la realidad, sobra uno de los dos campos; si
es posible, hay que documentar cuándo. Un dato duplicado que puede contradecirse acaba
contradiciéndose.

---

## RF-USR-01 — El email de usuario es único

GIVEN dos usuarios
THEN no comparten email (`ix_usuario_email` es UNIQUE en `001-initial-schema.sql`)

`AuthService.login` resuelve por `findByEmail` y espera como mucho un resultado.

**Mutación**: quitar el índice único en una migración de prueba.
Debe caer el test de email duplicado.

---

## RF-USR-02 — El rol es una entidad, no una cadena

GIVEN un usuario
THEN su rol es una FK a la tabla `rol`, no un `VARCHAR`

`005-fks.sql` migró `usuario.rol VARCHAR(32)` a `usuario.rol_id BIGINT NOT NULL` con FK.

**Lo que hay que verificar, no asumir** (§4.4 del playbook): que ningún control de acceso
compare contra `rol.nombre`. La columna `nombre` es editable y su única restricción es
`uk_rol_nombre`. Si existe un `getRolNombre().equals("MASTER")` en una decisión de
autorización, quien pueda renombrar un rol puede escalar privilegios en dos pasos.

`AuthService` usa `u.getRolNombre()` para meterlo en el claim `rol` del JWT — eso es
transporte, no decisión. Hay que revisar dónde se **decide** con ese valor.

**Búsqueda**: `grep -rn "getRolNombre\|getNombre().equals\|\"MASTER\"\|\"ADMIN\"" src/main/`

---

## RF-USR-03 — La contraseña nunca se guarda en claro

GIVEN un usuario
THEN `password_hash` contiene un hash BCrypt, nunca la contraseña

**Mutación**: hacer que `PasswordHasher.matches` devuelva `true` siempre.
Debe caer el test de login con contraseña incorrecta. Si no cae, no hay ningún test que
pruebe que una contraseña mala se rechaza.

---

## RF-USR-04 — Un usuario deshabilitado no entra

GIVEN un usuario con `habilitado = false`
WHEN intenta hacer login
THEN se rechaza con el mismo mensaje genérico que unas credenciales inválidas

El mensaje genérico es deliberado: no debe revelar si la cuenta existe.

**Mutación**: quitar el `if (!u.isHabilitado())` de `AuthService.login`.
Debe caer el test de usuario deshabilitado.

---

## RF-USR-05 — `RefreshToken` o `Sesion`, pero no las dos

GIVEN el mecanismo de refresh y revocación
THEN existe **un solo** modelo persistente para él

Estado medido el 2026-09-07:

| | `RefreshToken` | `Sesion` |
|---|---|---|
| Entidad JPA | existe | existe |
| Tabla | `refresh_token` (`006-05`) | `sesion` (`001-04`) |
| ¿La usa `AuthService`? | **no** | sí |
| Guarda | el token en claro (`token` UNIQUE) | HMAC-SHA256 del token |
| ¿La documenta `CLAUDE.md`? | **sí, como el mecanismo** | no |

`RefreshToken` guardaría el token **en claro** en la base; `Sesion` guarda sólo su HMAC. La
entidad muerta es además la menos segura de las dos, y es la que está documentada.

**Resolución exigida**: borrar `RefreshToken` y su tabla, o dejar escrito por qué convive.
Y corregir `CLAUDE.md` en cualquiera de los dos casos.

---

## RNF-USR-01 — Los DTOs exponen `publicId`, nunca el `id` interno

GIVEN cualquier respuesta de la API que incluya un usuario
THEN el identificador que viaja es el `publicId` (UUIDv7), no el `Long` secuencial

Es convención del proyecto (`CLAUDE.md`): el `Long` es predecible y enumerar usuarios por id
es una fuga de información.

**Mutación**: cambiar el mapeo `@Mapping(target = "id", source = "publicId")` para que exponga
el id interno. Debe caer un test que compruebe la forma del DTO.
