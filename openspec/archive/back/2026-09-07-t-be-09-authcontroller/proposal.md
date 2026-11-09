# Proposal — T-BE-09 · AuthController

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-09.md` · Área: back
Fecha: 2026-09-07 · **Change retrospectivo**

---

## Qué cubre

`AuthController` (`/api/v1/auth`): `login`, `refresh`, `logout`, `me`, `register`.
Más `RegisterService` y `EmailGenerator`.

## Estado real

Cinco endpoints implementados. `AuthControllerTest` tiene 3 tests, **los 3 en ERROR**.
`logout`, `register` y `me` no tienen ninguno.

## Los tres hallazgos

**1. Falta el endpoint de revocación de sesiones por MASTER.**
`backend.md:148` lo promete: `DELETE /api/auth/sessions/{usuarioId}` — «MASTER revoca todas
las sesiones de un usuario». `CLAUDE.md` lo eleva a regla que no se rompe: «Solo `MASTER`
revoca tokens de sesión».

`AuthController` tiene cinco métodos y **ninguno es ése**. `AuthService` tampoco tiene un
método de revocación por usuario: sólo `logout(refreshToken)`, que revoca la sesión de quien
llama.

Es una capacidad que dos documentos dan por existente y que no existe. Si un dispositivo de
un veedor se pierde el día de la elección, hoy no hay forma de cortar su acceso.

**2. `GET /me` probablemente está roto.**

```java
public AuthResponse.UsuarioActual me(Authentication auth) {
    String email = auth.getName();
    Usuario u = usuarios.findByEmail(email).orElseThrow();
```

`JwtAuthFilter` construye el `Authentication` con un `UserPrincipal` como principal. Para un
principal que no es `UserDetails` ni `String`, `getName()` devuelve `principal.toString()` —
que en un record es `UserPrincipal[id=..., email=..., ...]`, **no** el email. Entonces
`findByEmail` no encuentra nada y el `orElseThrow()` produce un `500`.

Hay que **ejecutarlo** para confirmarlo: si `UserPrincipal` implementa `Principal` o
sobreescribe `toString()`, funciona. Ningún test lo cubre, así que hoy nadie lo sabe.

**3. El email se genera y puede colisionar.** `RegisterService` delega en `EmailGenerator`
con el formato «3 letras del nombre + 3 del apellido + 3 del rol @control.ttcs».

`Juan Perez` OBSERVER y `Juana Perezoso` OBSERVER producen **el mismo email**. La columna
tiene índice único, así que la segunda alta falla con un error de base de datos, no con un
mensaje útil. Con ~450 JRVs y varios veedores por cantón, las colisiones de tres letras no son
hipotéticas.

## Alcance

- Verificar las garantías de los cinco endpoints existentes.
- Confirmar o descartar que `/me` está roto.
- Decidir qué se hace con la revocación por MASTER: implementarla o retirar la promesa.
- Resolver la colisión de emails.

## Fuera de alcance

- **No** se toca `AuthService` ni `JwtService`: son T-BE-08.
- **No** se implementa el frontend de gestión de sesiones: es T-FE-10.

## Criterio de cierre

1. `AuthControllerTest` pasa, y `logout`, `me` y `register` tienen tests.
2. `/me` funciona, verificado con una petición real.
3. La revocación por MASTER existe con su test, o la promesa desaparece de `backend.md` y
   `CLAUDE.md`.
4. La colisión de emails está resuelta o declarada con su límite conocido.

## Depende de

`back/2026-09-07-fix-liquibase-rbac-order`.
