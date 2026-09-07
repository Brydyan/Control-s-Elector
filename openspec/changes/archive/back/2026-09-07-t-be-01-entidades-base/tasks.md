# Tasks: 2026-09-07-t-be-01-entidades-base

Origen: `documents/task-control/backendtask/COMPLETADO-T-BE-01.md`
Rama: `verify/t-be-01-entidades-base`, sacada de `develop`.

**Change retrospectivo**: el código existe. Estas tareas **verifican**, no construyen. La
única excepción es B.3, que resuelve código muerto.

**Bloqueado por** `back/2026-09-07-fix-liquibase-rbac-order`.

---

## A · Verificar por mutación

`cp` al `.bak`, mutar, correr, **anotar el nombre del test que cayó**, restaurar con `cp`,
`rm` del `.bak`, `git status --short` limpio. **Nunca `git checkout --`.**

- [ ] **A.1** — `git status --short` limpio. Correr `UsuarioRepositoryTest` y anotar el
  resultado. Esperado: 3 tests, 0 error.

- [ ] **A.2** — RF-USR-03. Hacé que `PasswordHasher.matches` devuelva siempre `true`.
  Debe caer el test de login con contraseña incorrecta.

  **Si no cae, no existe ese test.** Escribilo: es la garantía de que una contraseña mala se
  rechaza, y hoy nada la protege.

- [ ] **A.3** — RF-USR-04. Quitá `if (!u.isHabilitado())` de `AuthService.login`.
  Debe caer el test de usuario deshabilitado.

- [ ] **A.4** — RF-USR-01. Test de email duplicado: debe fallar la inserción.

- [ ] **A.5** — RF-GEO-01. Persistir una JRV sin parroquia debe fallar.

- [ ] **A.6** — RNF-USR-01. Cambiá el mapeo para exponer el `id` interno en vez del
  `publicId`. Debe caer un test de forma del DTO.

---

## B · Los dos huecos

- [ ] **B.1** — RF-USR-02, §4.4. Buscar controles que decidan comparando contra el **nombre**
  del rol:

  ```bash
  grep -rn "getRolNombre\|getNombre().equals\|\"MASTER\"\|\"COORDINATOR\"\|\"ADMIN\"\|\"OBSERVER\"" \
       backend/electoral-api/src/main/java
  ```

  Por cada resultado, decidir si es **transporte** (meter el nombre en un claim del JWT, que
  está bien) o **decisión** (autorizar en base a esa cadena, que no).

  Si hay decisiones por nombre: anotarlo en `apply-progress.md` como hallazgo. La tabla `rol`
  tiene `nombre` editable, así que quien pueda renombrar un rol escala privilegios.

- [ ] **B.2** — RF-GEO-02. Responder si una JRV `URBANA` puede vivir en una parroquia
  `RURAL`. Si no puede, sobra uno de los dos campos: proponerlo. Si puede, documentar cuándo.
  Anotar la respuesta, no dejarla abierta.

- [ ] **B.3** — RF-USR-05. Resolver `RefreshToken`:

  1. Confirmar con `grep -rn "RefreshToken" src/main/` que **ningún** flujo la usa.
  2. Si nadie la usa: borrar la entidad y su repositorio, y añadir un changelog Liquibase que
     elimine la tabla `refresh_token`.
  3. Si alguien la usa: **parar y decirlo** — el diagnóstico de `proposal.md` sería
     incorrecto y hay que corregirlo antes de borrar nada.
  4. Corregir `CLAUDE.md`, sección Seguridad y Modelo de Dominio: el mecanismo vivo es
     `Sesion` con `refreshHash` (HMAC-SHA256) y rotación por uso.

  **Trampa**: `RefreshToken` guardaría el token **en claro**. `Sesion` guarda su HMAC. No te
  dejes llevar por que `CLAUDE.md` documente la primera: la documentada es la insegura.

  **Trampa Liquibase**: el changelog de borrado va al final, con el número siguiente. No
  edites `006-geografia.sql` en su sitio — es exactamente lo que rompió el orden de
  migraciones.

---

## Qué NO hacer en esta fase

- **No** reescribir las entidades. Funcionan.
- **No** tocar `Sesion` ni el flujo de auth: es T-BE-08 / T-BE-09.
- **No** borrar `RefreshToken` sin haber corrido el `grep` de B.3.1.
- **No** arreglar los controles por nombre que encuentres en B.1. Reportalos: cambiar un
  control de acceso es un change con su propia verificación.

---

## Compuertas antes de dar por terminada la fase

```
cd backend/electoral-api
./mvnw -B test > /tmp/after.log 2>&1; echo "exit=$?"
    UsuarioRepositoryTest: 3 tests, 0 error
    total: no baja respecto de la línea base
```

Las mutaciones de A anotadas con el nombre del test que cayó en cada una.
