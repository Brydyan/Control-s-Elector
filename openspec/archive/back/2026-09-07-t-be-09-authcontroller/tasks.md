# Tasks: 2026-09-07-t-be-09-authcontroller

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-09.md`
Rama: `verify/t-be-09-authcontroller`, sacada de `develop`.

**Change retrospectivo**, con dos huecos reales que sí se cierran: `/me` (B.1) y la
revocación por MASTER (C.1).

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación lo que existe

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Línea base. `AuthControllerTest`: 3 tests,
  esperado 0 error.

- [ ] **A.2** — RF-AUTH-01. Añadí el refresh al cuerpo de `AuthResponse`.
  Debe caer un test que compruebe la forma de la respuesta de login.

  Si no existe, escribilo: la separación access-en-cuerpo / refresh-en-cookie es lo que hace
  que un XSS no se lleve la sesión entera.

- [ ] **A.3** — RF-AUTH-03. Cambiá el mensaje del caso «usuario no existe» por uno
  específico. Debe caer un test que compare las tres respuestas (email inexistente,
  contraseña mala, usuario deshabilitado).

- [ ] **A.4** — RF-AUTH-04. Quitá la comprobación de `typ: "refresh"` en `AuthService.refresh`.
  Debe caer un test que intente refrescar con un **access token**.

  Sin esa comprobación, un access token robado se convierte en sesión perpetua.

- [ ] **A.5** — RF-AUTH-05. Quitá `auth.logout(refresh)`, dejando sólo el borrado de cookie.
  Debe caer un test que intente refrescar después del logout.

- [ ] **A.6** — RF-AUTH-07. Quitá el `@PreAuthorize` de `register`.
  Debe caer el test de OBSERVER intentando registrar → 403.

---

## B · `/me`: confirmarlo o arreglarlo

- [ ] **B.1** — RF-AUTH-06. **Ejecutalo**, no lo razones:

  ```bash
  # 1. login para obtener un access token
  curl -s -X POST http://localhost:8081/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"...","password":"..."}'

  # 2. llamar /me con ese token
  curl -i http://localhost:8081/api/v1/auth/me -H "Authorization: Bearer <access>"
  ```

  - **Si devuelve 500**: es un defecto. `auth.getName()` sobre un principal `UserPrincipal`
    devuelve su `toString()`, no el email, así que `findByEmail(...).orElseThrow()` explota.
    Arreglalo tomando el usuario del propio `UserPrincipal` (por `publicId`), no por email.
    Escribí el test que lo cubre — hoy no hay ninguno.
  - **Si devuelve 200**: funciona por algún detalle de `UserPrincipal`. **Dejá escrito cuál**
    en un comentario del método: es la clase de cosa que alguien rompe en un refactor sin
    saber que era la pieza que lo sostenía.

  En los dos casos, escribí el test.

---

## C · La revocación que dos documentos prometen

- [ ] **C.1** — RF-AUTH-09. Confirmá primero que no existe:

  ```bash
  grep -rn "sessions\|revocarSesiones\|revocarTodas" \
       backend/electoral-api/src/main/java/ec/twintailcs/electoral/auth
  ```

  Si no existe, decidí **una** y hacela completa:

  - **Implementarla**: `DELETE /api/v1/auth/sessions/{usuarioPublicId}` con
    `@PreAuthorize("hasRole('MASTER')")`, un método en `AuthService` que marque `revocado` en
    todas las sesiones de ese usuario, auditoría, y tests (MASTER revoca / no-MASTER → 403 /
    los refresh del usuario dejan de servir). **O**
  - **Retirar la promesa** de `backend.md:148` y de la regla de `CLAUDE.md` «Solo `MASTER`
    revoca tokens de sesión` — y escribir qué se hace si el dispositivo de un veedor se
    pierde el día de la elección.

  Anotá la decisión y su motivo. La segunda opción es legítima, pero deja un escenario
  operativo sin respuesta y eso tiene que ser explícito.

  **Recordá el alcance real**: revocar la sesión corta los refresh, pero el access token
  vigente sobrevive hasta 15 minutos (`t-be-08-security-jwt` RF-SEC-07). Si eso no es
  aceptable para este caso de uso, es una decisión aparte.

---

## D · La colisión de emails

- [ ] **D.1** — RF-AUTH-08. Reproducila: registrar `Juan Perez` OBSERVER y después
  `Juana Perezoso` OBSERVER. Anotá qué error sale exactamente.

- [ ] **D.2** — Decidí **una**:
  - sufijo numérico automático ante colisión;
  - permitir email explícito opcional en la petición;
  - al menos, capturar la violación de constraint y devolver un mensaje que diga qué pasó y
    cómo resolverlo.

  **Por qué no se deja para después**: las altas se hacen en bloque los días previos a la
  elección. Un error de base de datos sin explicación, en ese momento, cuesta horas.

---

## Qué NO hacer en esta fase

- **No** tocar `AuthService.login/refresh/logout` ni `JwtService`: son T-BE-08. Aquí se muta
  y se restaura.
- **No** implementar el frontend de gestión de sesiones: es T-FE-10.
- **No** cerrar C.1 eligiendo «retirar la promesa» sin escribir qué se hace con un
  dispositivo perdido. Retirar una capacidad es válido; dejar el escenario sin respuesta, no.
- **No** dar por bueno `/me` porque el código parece correcto. Ejecutalo.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    AuthControllerTest: 3 tests + los nuevos de logout, me y register, 0 error
    total = línea base + los añadidos (contados)

curl -i http://localhost:8081/api/v1/auth/me -H "Authorization: Bearer <access>"
    → 200, no 500
```

Las cinco mutaciones de A anotadas con el nombre del test que cayó en cada una.
