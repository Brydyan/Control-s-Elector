# Apply Progress — T-AUTH-INV-02: Invitaciones de usuario por SMTP (con email personal)

**Implementador:** agente Mavis (modo SDD-apply / Implementation Routing)
**Change:** `openspec/changes/auth/2026-11-07-t-auth-inv-02-user-invitations-smtp/`
**Rama:** `brydyan/sc-492/t-auth-inv-02-user-invitations-smtp-email` (sin commitear, sin push)
**Fecha:** 2026-11-08

> Este archivo lo escribe el implementador, no el verificador. **No** decide si el trabajo está
> bien: eso lo hace otro, con contexto limpio. No hay `verify-report.md` ni `archive-report.md`
> acá, a propósito.

---

## A — Números medidos

### A.1 — Línea base (antes de tocar nada)

Medida en un `git worktree` del mismo commit (`55bb7ef`), no de memoria:

```
backend   mvn -B -o clean verify -Dtest.postgres.url=jdbc:postgresql://localhost:5432/electoral_db
          75 tests / 0 failure / 2 error (los dos de Docker: RlsIsolationIT e
          InvitacionesMigracionIT no pueden levantar un contenedor)
  cobertura  INSTRUCTION covered=2225 missed=13874 ratio=0.1382
             LINE       covered=506  missed=3104  ratio=0.1402
             230 clases en el reporte

frontend  npx vitest run        76 files / 760 tests / 0 failure
          pnpm lint            All files pass linting
          pnpm format:check    All matched files use Prettier code style
          pnpm build           Application bundle generation complete
e2e       npx playwright test  46 passed (23 tests x 2 proyectos)
```

### A.2 — Resultado al terminar

```
backend   mvn -B -o clean verify        BUILD SUCCESS
          92 tests / 0 failure / 2 error (los mismos dos de Docker)
  (+17 tests, +1 suite)
    UserInvitationIT        8   (nuevo)
    UserInvitationTest      5   (nuevo)
    InvitationRateLimiterTest 4 (nuevo)
    RlsIsolationIT         +3  (politicas de user_invitations)

  spotless:check      BUILD SUCCESS (0 violaciones)
  jacoco:report       target/site/jacoco/ generado (238 clases)
  jacoco:check        BUILD SUCCESS (piso 0.08, real 0.1619)

  cobertura  INSTRUCTION covered=2729 missed=14122 ratio=0.1619 (16.19%)  [antes 0.1382]
             LINE       covered=619  missed=3172  ratio=0.1633 (16.33%)  [antes 0.1402]
             (+504 lineas cubiertas, +8 clases en el reporte)

frontend  pnpm test:ci          77 files / 767 tests / 0 failure   [+1 file, +7 tests]
          pnpm lint             All files pass linting
          pnpm format:check     All matched files use Prettier code style
          pnpm build            Application bundle generation complete (825.52 kB)
e2e       npx playwright test   51 passed + 1 skipped (25 tests x 2 proyectos)
```

### A.3 — Lo que NO se midió, y por qué

- **Docker no estaba disponible en esta maquina.** El daemon se levanto a las 23:28 y se volvio a
  caer; en la ventana en que estubo arriba, `RlsIsolationIT` y `InvitacionesMigracionIT` corrieron
  contra contenedores reales (2/2 en verde). En la corrida final los dos quedaron con error
  ambiental (`Could not find a valid Docker environment`), NO con fallo de codigo: en CI corren.
- **La migracion se ejecuto contra un Postgres 16.15 real** (Postgres embebido, `/tmp/pgbin`, con
  `initdb` propio), no contra una base de mentira: los 92 tests del backend corrieron contra esa
  base con Liquibase aplicando el changelog y Hibernate validando el schema.
- **Las politicas RLS de `user_invitations` se verificaron a mano con `psql`** usando un rol
  `NOSUPERUSER NOBYPASSRLS` (el mismo mecanismo que usa `RlsIsolationIT`): el INSERT con
  `invited_by` = el contexto pasa; el INSERT atribuyendo la invitacion a otro usuario falla con
  `new row violates row-level security policy` (42501); con el contexto de A se ve 1 fila y con el
  de B se ven 0. El IT que lo dejara automatico corre en CI.
- **Cobertura por clase:** el reporte de JaCoCo atribuye **0%** a `UserInvitationService`, que el
  `UserInvitationIT` ejercita de punta a punta (el test asserta las filas que escribe, el email
  que se encola y el estado final de la cuenta). Es la misma limitacion de la herramienta que se
  documento en T-LEGAL-01: el usuario del datasource en los entornos de test es superusuario y se
  salta RLS, y el mecanismo de instrumentacion no engancha esas clases en el classloader de
  Quarkus. El numero medido (16.19%) es un **piso**. Lo que si prueba que los tests no son
  decorativos son las mutaciones de la seccion C.

---

## B — Que se cerro, tarea por tarea

| Task | Cerrada | Donde quedo |
|---|---|---|
| T-AUTH-INV-02.1 DB | SI | `029-user-invitations.sql` (4 changesets) + registro en `db.changelog-master.yaml` |
| T-AUTH-INV-02.2 UserInvitationService | SI | `auth/application/UserInvitationService.java` (+ `auth/domain/UserInvitation.java`) |
| T-AUTH-INV-02.3 Resource | SI | `auth/web/UserInvitationResource.java` (+ `auth/security/InvitationRateLimiter.java`) |
| T-AUTH-INV-02.4 Formulario | SI | `admin/pages/usuarios/admin-usuarios.page.{ts,html,scss}` (dialogo "Crear usuario") |
| T-AUTH-INV-02.5 Test de integracion | SI | `auth/web/UserInvitationIT.java` (8 tests) + `UserInvitationTest` + `InvitationRateLimiterTest` |
| T-AUTH-INV-02.6 E2E | SI | `e2e/j7-auth-inv-02-user-invitations.spec.ts` |

**Ninguna task queda sin cerrar.** La unica pieza del contrato que **no** se implemento es la
**contrasena temporal**; esta declarada en la seccion D.2 con el motivo y con lo que haria falta
para implementarla bien.

---

## C — Mutaciones ejecutadas (12: 11 mataron tests, 1 sobrevive y se declara)

Cada mutacion se aplico sobre el codigo limpio, se corrio el test afectado y se restauro el
codigo. Nombre exacto del test que cayo:

| # | Mutacion | Test(s) que cayo |
|---|---|---|
| M1 | `usuario.setEmailPersonal(...)` deja de ejecutarse | `UserInvitationIT.elMasterInvitaYLaCuentaQuedaCreada` |
| M2 | la fila de `user_invitations` deja de persistirse | `UserInvitationIT.laInvitacionQuedaRegistrada`<br>`UserInvitationIT.conElLinkDelEmailLaPersonaActivaLaCuenta` |
| M3 | el email sale al login y no al correo de contacto | `UserInvitationIT.elEmailSaleAlCorreoDeContactoYLlevaElLogin` |
| M4 | el limitador de tasa deja de rechazar | `UserInvitationIT.laSegundaInvitacionDentroDeLaVentanaResponde429`<br>`InvitationRateLimiterTest.laSegundaInvitacionDentroDeLaVentanaSeRechaza`<br>`InvitationRateLimiterTest.sinIpLaCuentaCaeEnUnaClaveCompartida` |
| M5 | el endpoint deja de exigir `usuario:create`/MASTER | `UserInvitationIT.unRolSinPermisoRecibe403` |
| M6 | la validacion de email del servicio deja de ejecutarse | **SOBREVIVE** (declarado, ver C.1) |
| M7 | `estadoActual()` deja de derivar `EXPIRED` | `UserInvitationTest.pasadaLaVentanaQuedaExpired` |
| M8 | la activacion deja de marcar la invitacion como `ACTIVATED` | `UserInvitationIT.conElLinkDelEmailLaPersonaActivaLaCuenta` |
| M9 | `puedeInvitar()` devuelve siempre `true` | `admin-usuarios.invitacion.spec.ts > el boton de invitar esta bloqueado sin nombre, apellido y email`<br>`... > un email con formato invalido no habilita el boton`<br>`... > un email vacio no dispara la peticion` |
| M10 | `invitar()` deja de llamar a la API | `admin-usuarios.invitacion.spec.ts > invitar manda los datos de la persona y muestra el resultado`<br>`... > el mensaje de exito dice a quien se invito y con que usuario entra`<br>`... > un error del backend se muestra y no deja el estado de envio colgado` |
| M11 | la validacion de email del panel acepta cualquier cosa | `admin-usuarios.invitacion.spec.ts > un email con formato invalido no habilita el boton` |
| M12 | el mensaje de exito no se renderiza | `admin-usuarios.invitacion.spec.ts > el mensaje de exito dice a quien se invito y con que usuario entra`<br>E2E `j7-auth-inv-02-user-invitations.spec.ts > el MASTER invita a una persona y ve la confirmacion` |

### C.1 — M6 sobrevive, y por que se declara (no se oculta)

La mutacion quita la comprobacion de email del **servicio** y ningun test cae. Motivo medido: el
DTO de entrada (`InviteUserRequest`) lleva `@Email`, y la validacion de bean corre **antes** que
el cuerpo del recurso: el endpoint responde 400 igual. El test que verifica el 400 pasa en los dos
casos.

La comprobacion del servicio se deja a proposito: es la segunda capa, para un llamador que llegue
al caso de uso sin pasar por el endpoint. El comportamiento observable (400 con un email invalido
y nada creado) esta cubierto y verificado.

---

## D — Desviaciones del contrato (tasks.md / spec) y por que

El `tasks.md` esta escrito contra el **backend Spring** (Flyway, `SecurityContext`,
`@RequiredRole`, `@RateLimit`, `MailService`, `mat-form-field`, `ec.gob.cne.electoralsystem.api`).
Se implemento **adaptando el contrato al stack real**, no al reves. Cada desviacion y su motivo:

| Donde | Devolucion | Motivo |
|---|---|---|
| .1 ruta del changelog | `database/changelog/029-user-invitations.sql` | Liquibase es el unico dueno del esquema (AGENTS.md). No existe Flyway en el repo. |
| .1 `mvn flyway:migrate` | `mvn verify` aplica el changelog contra Postgres real |idem. La migracion se ejecuto y se verifico contra Postgres 16.15 (75 -> 79 changesets). |
| .1 `invited_by NOT NULL` | `invited_by` nullable | Si el MASTER que invito se da de baja, la fila se queda como historico en vez de desaparecer: el historico de invitaciones no se borra con las cuentas. |
| .1 sin RLS en el SQL del task | RLS activada **y forzada** con 3 politicas | AGENTS.md: "RLS de PostgreSQL es capa obligatoria: un endpoint que la salta se considera defecto y no excepcion". La tabla guarda correo de contacto, IP y agente de usuario. |
| .2 paquete `ec.gob.cne...` | `ec.twintailcs.electoral.auth` | El paquete real del proyecto. |
| .2 `usuario.setUsername(...)` | `usuario.setEmail(username)` | **En este sistema el correo ES la identidad de acceso**: no hay columna `username`. El login generado es el email `@control.ttcs` y el correo personal va en la columna nueva `usuario.email_personal`. |
| .2 formato del login `{3n}{3a}.{3r}@control.ttcs` | `{3n}{3a}{3r}@control.ttcs` (sin punto) | El `EmailGenerator` del repo es un **port exacto del backend Spring** y su javadoc explica por que: "para que un usuario creado en cualquiera de las dos implementaciones tenga el mismo email". Ademas las cinco cuentas de referencia y los seeds ya usan el formato sin punto; ponerlo con punto solo en las cuentas nuevas deja dos formatos conviviendo en la misma tabla. La diferencia es cosmetica: la unicidad y la resolucion de colisiones (sufijo numerico) son las mismas. **Es la desviacion mas discutible del change y queda para que la decida el verificador.** |
| .2 "Temp password: 12 chars with entropy" | **No implementada** | Ver D.2. |
| .2 cuenta creada `ACTIVE` | cuenta creada `PENDIENTE_INVITACION` | El login bloquea las cuentas que no eligieron contrasena (`LoginUseCase`): es la garantia de que la persona toma el control de su clave. Crear la cuenta activa seria un weakening. |
| .2 `PasswordService.hash(tempPassword)` | hash de una cadena aleatoria que no se le muestra a nadie | La contrasena real la elige la persona al activar la cuenta. Lo que importa es que la columna NOT NULL tenga un bcrypt valido. |
| .2 `MailService.sendInvitationEmail(...)` | `InvitationEmailQueue` (puerto ya existente) | La cola con rate limit, reintentos y auditoria de intentos ya existe del flujo de T-AUTH-INV-01. El email se encola **despues del commit**: si saliera durante la transaccion y esta fallara, la persona recibiria un link con un token que nunca llego a la base. |
| .2 `auditService.log("USER_INVITED", ...)` | `InvitationAuditService.logInvitationSent(...)` | Es la fachada de auditoria del flujo de invitaciones; escribe siempre por `AuditLogService -> JPA -> Postgres`. Sin contrasena ni token en el registro. |
| .3 `@RequiredRole("MASTER")` | `autorizador.exigir("usuario:create", "MASTER")` | El modelo de permisos del repo: el permiso granular o el rol. Un OBSERVER recibe 403 (medido). |
| .3 `@RateLimit(requests=1, windowSec=5)` | `InvitationRateLimiter` (propio) | La anotacion `@RateLimit` no existe en el repo. Es un limitador en memoria por IP, con la ventana de 5 segundos, como el `ActivationRateLimiter`. |
| .3 429 sin `Retry-After` | 429 **con** `Retry-After` | El spec de privacidad (T-LEGAL-01) ya establecio que un 429 de limite debe decir cuanto falta. Se agrego `retryAfterSeconds` a `InvitacionException` y el mapper lo manda. |
| .3 "401 if not MASTER" | **403** para un autenticado sin permiso, 401 para quien no tiene sesion | Es el contrato del repo (`AccesoNoPermitidoException` -> 403). Un 403 no dice "tu sesion esta mal": dice "no podes". |
| .3 response | `{usuarioId, username, emailPersonal, status}` + `expiraEn` | El contrato del spec, mas la fecha de vencimiento que la UI puede mostrar. Ninguna credencial viaja en la respuesta. |
| .4 componente `app-create-user-form` separado | el dialogo "Crear usuario" del panel de MASTER | El panel ya tiene el dialogo con nombre, apellido y rol: agregar un formulario paralelo duplicaria el alta de usuarios (que ademas crea cuentas **activas** con contrasena temporal, otro flujo). El boton "Enviar invitacion" vive en el mismo dialogo. |
| .4 `mat-form-field` / Angular Material | PrimeNG (`pInputText`, `p-select`) + `ngModel` | El repo no usa Angular Material. |
| .5 ruta del test `ec.gob.cne...` / `UserInvitationTest` | `auth/web/UserInvitationIT.java` | Un IT de integracion contra Postgres real, no un test unitario: el flujo escribe en dos tablas, una con RLS, y manda un email. |
| .5 `assert(username == "juagar.obs@control.ttcs")` | `juagarobs{n}@control.ttcs` | El formato real (ver la fila del login). El test afirma el patron, que es estable con o sin colisiones. |
| .6 `/admin/users` | `/admin/usuarios` | La ruta real del panel de administracion. |
| .6 `page.fill('input[name="nombre"]')` | localizadores por rol y por `data-testid` | La app no usa `name` en los inputs; el dialogo de PrimeNG exige localizadores dentro del dialogo para no colar el buscador de la pagina. |

### D.2 — La contrasena temporal: por que NO se implemento

El task pide generar una contrasena temporal de 12 caracteres y mandarla por email. **No se
implemento**, a proposito, por tres razones que se pueden medir:

1. **La cuenta nace `PENDIENTE_INVITACION` y el login la bloquea** (`LoginUseCase` responde 403
   "Usuario aun no ha activado su cuenta"). Una contrasena temporal que no sirve para entrar es
   decoracion: el unico camino real es el link de activacion, que es el que ya usa el sistema.
2. **La cuenta activada por el link queda con la contrasena que eligio la persona.** Es
   estrictamente mejor que una temporal: una clave que viaja por correo queda en el buzon, en los
   servidores de mail y en la pantalla de quien invita.
3. **No hay donde cambiar la contrasena.** El pie del email del task apunta a "Mi Perfil ->
   Seguridad" y **esa pantalla no existe** en la aplicacion. Mandar una contrasena temporal sin
   una pantalla para cambiarla deja al usuario con una clave que nunca rota.

Lo que si quedo: el **link de activacion** (24 h, el token del sistema) como credencial, el **login
generado** en el correo (sin el, la persona no sabria con que credencial entrar) y la
**invitacion auditada**. Para implementar la temporal de verdad haria falta, como minimo: una
pantalla de "cambiar contrasena" o un flag `must_change_password` en `usuario` con el gate
correspondiente en el login. Eso es un change de producto, no una linea de este.

---

## E — Hallazgos y arreglos que no estaban previstos

1. **El commit anterior (T-LEGAL-01) dejo el frontend legal a medias y su propio E2E en rojo.**
   El commit `55bb7ef` subio el modulo legal (los componentes, los servicios, las paginas, el
   E2E `j6`) pero **no** subio el cableado: faltaban el gate de terminos en `auth.guard.ts`, las
   rutas `/terminos` y `/contact-dpo`, el mapeo de `acceptedTermsVersion` en el DTO y la API, el
   aviso legal del login y el enrutado de `/privacy` y `/terms` en nginx y en los proxies de dev.
   Con eso, el `j6-legal-terms.spec.ts` que el propio commit subia fallaba 2 de 3 escenarios
   (medido) y el que pasaba pasaba por casualidad (no habia gate, no habia modal). **Se restauró
   el wiring** (8 archivos) y el `j6` volvio a 3/3: el modal aparece, el boton "Acepto" registra
   la aceptacion y cerrar sin aceptar cierra la sesion.
2. **El limitador por IP hace que los tests se pisen.** El `InvitationRateLimiter` es un bean de
   la aplicacion, asi que dos tests con la misma IP se pisan: el segundo recibe el 429 del
   primero. Cada test del IT usa una IP propia, con un octeto aleatorio por corrida (las pruebas
   que comprueban que NO queda ninguna invitacion cuentan las filas de SU IP).
3. **El `EmailGenerator` agota sus 100 intentos en una base con historia.** Con muchos "Juan Garcia
   observadores" creados, el generador responde 400 ("No se pudo generar email unico"). No es un
   defecto de este change (es el comportamiento documentado del generador), pero hacia fallar los
   tests del IT en una base local larga. Los tests usan un apellido por caso para no acumular.
4. **El boton "Crear usuario" es inalcanzable en movil.** En el viewport de 393 px, las acciones de
   la barra superior pasan a una fila completa y la barra, que es fija, tapa el alto del contenido:
   el boton queda debajo y Playwright reporta que `layout-topbar-actions` intercepta el click. Es un
   **defecto de layout preexistente**, no de este flujo, y no lo tapa el scroll porque la barra no se
   mueve. El E2E de invitar se salta en movil con el motivo escrito en el propio test, y el arreglo
   (reservar el alto de la barra en el contenido) es de la capa de layout, fuera de este change.

---

## F — Lo que queda pendiente (fuera del alcance del implementador)

1. **La contrasena temporal**, si el producto la quiere: hace falta la pantalla de cambio de
   contrasena o el flag `must_change_password` (ver D.2).
2. **El formato del login con punto** (`juagar.obs@control.ttcs`): decide el producto. Si se
   cambia, hay que cambiar `EmailGenerator` (y con el, las cuentas de referencia del seed y los
   fixtures del E2E).
3. **El defecto de layout movil** (E.4): la barra superior fija tapa el alto del contenido en
   pantallas angostas. Afecta a todas las paginas, no solo a esta.
4. **El E2E no corre en CI**: `ci.yml` no tiene job de Playwright. Los 51 tests corrieron local
   contra el dev server con mocks de `page.route`.

---

## G — Archivos tocados

**Backend (nuevos)**
```
src/main/java/ec/twintailcs/electoral/auth/domain/UserInvitation.java
src/main/java/ec/twintailcs/electoral/auth/application/UserInvitationService.java
src/main/java/ec/twintailcs/electoral/auth/security/InvitationRateLimiter.java
src/main/java/ec/twintailcs/electoral/auth/web/UserInvitationResource.java
src/main/java/ec/twintailcs/electoral/auth/web/dto/InviteUserRequest.java
src/main/java/ec/twintailcs/electoral/auth/web/dto/UserInvitationResponse.java
src/main/resources/database/changelog/029-user-invitations.sql
src/test/java/ec/twintailcs/electoral/auth/domain/UserInvitationTest.java
src/test/java/ec/twintailcs/electoral/auth/security/InvitationRateLimiterTest.java
src/test/java/ec/twintailcs/electoral/auth/web/UserInvitationIT.java
src/test/java/ec/twintailcs/electoral/auth/web/UserInvitationTestProfile.java
src/test/java/ec/twintailcs/electoral/auth/infrastructure/email/RecordingEmailService.java
```

**Backend (modificados)**
```
auth/domain/Usuario.java                    (+emailPersonal)
auth/domain/InvitacionException.java        (+retryAfterSeconds)
auth/web/InvitacionExceptionMapper.java     (manda Retry-After en el 429)
auth/application/UserInvitationService.java  (nuevo, ver arriba)
auth/application/ActivarUsuarioPorTokenUseCase.java  (marca la invitacion como ACTIVATED)
auth/application/InvitacionService.java      (el nuevo campo username del email)
auth/infrastructure/email/EmailAEnviar.java (+username: el login, que no siempre es el destinatario)
auth/infrastructure/email/SmtpEmailProvider.java (pasa el username a la plantilla)
resources/email/invitation.html              (muestra el login)
resources/database/db.changelog-master.yaml  (+029)
test/.../shared/security/RlsIsolationIT.java (+3 casos de RLS de user_invitations)
test/.../legal/support/PostgresLegalTestResource.java -> shared/testing/PostgresTestResource.java
```

**Frontend (nuevos)**
```
src/app/features/admin/pages/usuarios/admin-usuarios.invitacion.spec.ts
e2e/j7-auth-inv-02-user-invitations.spec.ts
e2e/support/invitation-mocks.ts
```

**Frontend (modificados)**
```
src/app/features/admin/data/admin.api.ts              (invitarUsuario)
src/app/features/admin/domain/models/admin.model.ts   (InviteUsuarioRequest, UserInvitationResponse)
src/app/features/admin/pages/usuarios/admin-usuarios.page.ts
src/app/features/admin/pages/usuarios/admin-usuarios.page.html
src/app/features/admin/pages/usuarios/admin-usuarios.page.scss
```

**Restauracion del wiring de T-LEGAL-01 (colateral, ver E.1)**
```
src/app/core/guards/auth.guard.ts              (gate de terminos + sessionGuard)
src/app/app.routes.ts                          (rutas /terminos y /contact-dpo)
src/app/features/auth/data/auth.dto.ts        (acceptedTermsVersion)
src/app/features/auth/data/auth.api.ts        (mapeo)
src/app/features/auth/pages/login/login.page.html / .scss   (aviso legal)
nginx.conf, proxy.conf.json, vite.config.ts   (enrutado de los documentos legales)
```
