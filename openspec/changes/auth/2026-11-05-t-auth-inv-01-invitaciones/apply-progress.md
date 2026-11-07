# Apply Progress — T-AUTH-INV-01: Invitaciones de usuario por email

**Implementador:** subagente `sdd-apply`
**Change:** `openspec/changes/auth/2026-11-05-t-auth-inv-01-invitaciones/`
**Rama:** `brydyan/t-fe-20-imagen-compression` (sin commitear, sin push)
**Fecha:** 2026-11-06

> Este archivo lo escribe el implementador, no el verificador. **No** decide si el trabajo está
> bien: eso lo hace otro, con contexto limpio. No hay `verify-report.md` ni `archive-report.md`
> acá, a propósito.

---

## A.1 — Línea base (medida al abrir la sesión, antes de tocar nada)

```
backend test   mvn -B test   exit 0
               2 suites / 4 tests / 0 error / 0 failure / 0 skip
               (UuidVersion7StrategyTest=2, RlsIsolationIT=2)
frontend test  pnpm test:ci  66 suites / 628 tests / 0 fallos   exit 0
```

Números leídos de `target/surefire-reports/TEST-*.xml` y de la salida de Vitest. Nunca del exit
code de un pipe.

**Lo primero que encontró esa línea base:** el `02-builder.md` y el `ROADMAP.md` describen un
backend que ya no existe. `ROADMAP.md` §3 dice "backend en `backend/electoral-api/`, 63 suites /
281 tests VERDE" — ese módulo fue borrado en la migración a Quarkus. El backend real es
`backend/electoral-api-quarkus/`, paquete `ec.twintailcs.electoral`, y tiene **4 tests**. La
afirmación del `02-builder.md:67` ("la suite de backend está en rojo, 78 errores, el modo TDD
estricto no aplica") es **falsa hoy**: con 4 tests, un test nuevo se distingue sin ambigüedad y
el modo TDD estricto sí aplica. Queda declarado como hallazgo para el Arquitecto; corregir esa
doc no es tarea de este change.

## A.2 — Resultado medido al terminar

```
backend test   mvn -B test   exit 0
               9 suites / 50 tests / 0 error / 0 failure / 0 skip
               (41 unitarios + 7 InvitacionesMigracionIT + 2 preexistentes)

  InvitationTokenServiceTest            10
  PasswordValidationServiceTest          9
  InvitacionesMigracionIT                7   (Testcontainers + Postgres 16)
  EmailTemplatesTest                     7
  SmtpEmailProviderTest                  3
  InvitationEmailQueueImplTest           7
  ActivationRateLimiterTest              3
  UuidVersion7StrategyTest               2   (preexistente)
  RlsIsolationIT                         2   (preexistente)

frontend test  pnpm test:ci  67 suites / 637 tests / 0 fallos   exit 0
               (linea base 66 / 628 → +1 suite, +9 tests de la pantalla de activacion)
frontend lint  pnpm lint        0 errores   exit 0
frontend fmt   pnpm format:check 0 errores   exit 0
frontend build pnpm build        exit 0     (los warnings son preexistentes)

e2e invitaciones  bash src/test/tools/e2e-invitaciones.sh
                  21 comprobaciones / 0 fallos   exit 0
```

Los 628 tests de frontend no se movieron porque **no se tocó el frontend** (ver "Sin cerrar").

---

## B — Tareas cerradas

### Sprint 1 — schema y servicios

| Task | Estado | Dónde |
|---|---|---|
| 1.1 Migración + `invitation_audit` | **cerrada con desvío** | `database/changelog/028-invitaciones.sql` |
| 1.2 Email service abstraction | cerrada | `auth/infrastructure/email/` |
| 1.3 Invitation token service | cerrada | `auth/application/InvitationTokenService.java` |
| 1.4 Password validation service | cerrada | `auth/application/PasswordValidationService.java` |
| 1.5 Invitation audit service | cerrada con desvío | `auth/application/InvitationAuditService.java` |

### Sprint 2 — casos de uso y endpoints

| Task | Estado | Dónde |
|---|---|---|
| 2.1 EnviarInvitacionUseCase + cola | cerrada con desvío | `auth/application/InvitacionService.java`, `auth/infrastructure/messaging/` |
| 2.2 ActivarUsuarioPorTokenUseCase | cerrada | `auth/application/ActivarUsuarioPorTokenUseCase.java` |
| 2.3 REST endpoints | cerrada con desvío | `auth/web/InvitacionResource.java`, `auth/web/ActivacionResource.java` |
| 2.4 LoginUseCase valida estado | cerrada | `auth/application/LoginUseCase.java` |

### Sprint 3 y 4

| Task | Estado |
|---|---|
| 3.1 ActivateUserComponent (frontend) | cerrada — `frontend/app/src/app/features/auth/pages/activate/` |
| 3.2 Email templates | cerrada — `src/main/resources/email/{invitation,confirmation,resend}.html` |
| 3.3 Bulk invite en el panel admin | **sin cerrar** — ver D |
| 4.1 SMTP config + worker de la cola | cerrada con desvío |
| 4.2 Integration tests | cerrada — `InvitacionesMigracionIT` + e2e |
| 4.3 Frontend component tests | cerrada — `activate.page.spec.ts`, 9 tests |
| 4.4 k6 load test | **sin cerrar** — ver D |

---

## C — Mutaciones ejecutadas

Protocolo: `git status --short` antes y después, respaldo con `cp` a `.bak`, restauración con `cp`,
**nunca** `git checkout --`.

### C.1 — Unitarias (oráculo: JUnit del módulo)

| # | Mutación | Test(s) que cayeron |
|---|---|---|
| M1 | Quitar la columna `estado_usuario` de la migración 028 | `InvitacionesMigracionIT.setUp` — los **7** tests de la clase erroran (la columna no existe) |
| M1b | Cambiar el default de `estado_usuario` a `PENDIENTE_INVITACION` | `InvitacionesMigracionIT.estadoUsuarioEsNotNullYSuDefaultEsActivo`<br>`InvitacionesMigracionIT.lasCuentasQueYaExistianSiguenActivas`<br>`InvitacionesMigracionIT.unaCuentaNuevaNaceActivaSalvoQueSePidaLoContrario` |
| M2 | `SmtpEmailProvider` no envía el email | `SmtpEmailProviderTest.laInvitacionSeEnviaConElDestinatarioYElLink`<br>`SmtpEmailProviderTest.elReenvioUsaLaPlantillaDeReenvio` |
| M3 | El token de activación se emite sin expiración | `InvitationTokenServiceTest.elTokenGeneradoExpiraEn24Horas`<br>`InvitationTokenServiceTest.unTokenDeActivacionNoSirveComoCredencialDeSesion`<br>`InvitationTokenServiceTest.unTokenValidoDevuelveElIdPublico` |
| M4 | Desaparece la regla de mayúscula | `PasswordValidationServiceTest.unaContrasenaSinMayusculaListaLaMayuscula`<br>`PasswordValidationServiceTest.unaContrasenaListaTodosLosRequisitosQueLeFaltan` |
| M5 | La cola no reintenta (un solo intento) | `InvitationEmailQueueImplTest.unEnvioFallidoSeReintentaHastaAgotarLosIntentos`<br>`InvitationEmailQueueImplTest.unEnvioQueFallaUnaVezSaleEnElSegundoIntento` |
| M6 | Quitar la espera del límite de tasa | **NINGUNO** — mutación inefectiva (ver nota) |
| M6b | La cola nunca aplica el límite de tasa | `InvitationEmailQueueImplTest.elLimiteDeTasaFrenaLaSalida` |
| M7 | La plantilla pierde el aviso de «expira en 24 horas» (C.12 del spec) | `EmailTemplatesTest.laInvitacionAvisaQueElLinkExpiraEn24Horas` |
| M8 | El backoff deja de crecer (multiplicador 1) (C.14 del spec) | `InvitationEmailQueueImplTest.elRetardoEntreReintentosCrece` |

**Dos notas sobre las mutaciones que no caían a la primera:**

*M6:* quitar el `sleep` del límite de tasa **no rompe nada**, porque el bucle sigue girando
hasta que vence la ventana: el límite se cumple igual, solo que quemando CPU. La mutación que sí
lo elimina (M6b) sí cae. Queda anotado porque "ningún test cayó" no significa "el código estaba
bien": la primera mutación era mala, no el test.

*M8 (C.14):* el primer intento del test del backoff usaba demoras de 10 ms y un umbral de 30 ms.
El redondeo del `sleep` hacía que la diferencia entre "con multiplicador 2" (30 ms) y "sin
multiplicador" (20 ms reales, medidos 26-32) quedara tapada, y la mutación pasaba sin que nadie
lo notara. Las demoras se subieron a 60 ms con umbral de 160 ms: la diferencia real es de 60 ms
y el ruido del scheduler es de un par de milisegundos. **Un test que no distingue la mutación
no es un test: es decorativo con assertions.**

### C.2 — Extremo a extremo (oráculo: `src/test/tools/e2e-invitaciones.sh`)

| # | Mutación | Comprobación que cayó |
|---|---|---|
| C.8 | La activación no comprueba que el token sea el vigente | `el token viejo deja de servir después del reenvío` (obtenido 200 en vez de 409: el token viejo activaba la cuenta) |
| C.9 | El endpoint de invitaciones pierde el guard de rol | `un OBSERVER no puede invitar` (obtenido 409 en vez de 403: llegó a la validación de estado) |
| C.10 | El login deja de comprobar el estado de activación | **NO EJECUTADA** — ver D |

---

## D — Sin cerrar

Nada de esto se omitió: se declara.

### D.1 — C.10 (mutación del login) no ejecutada

Requiere el e2e, que necesita Docker para el Postgres. **El daemon de Docker se cae a mitad de
la sesión** (`docker.service` inactive y `systemctl start` pide sudo interactivo, que no se puede
dar desde acá). El comando para cerrarla cuando el daemon vuelva:

```bash
sudo systemctl start docker
cd backend/electoral-api-quarkus
/tmp/mutar-e2e.sh "C.10 — el login deja de comprobar el estado de activación" \
  "src/main/java/ec/twintailcs/electoral/auth/application/LoginUseCase.java" \
  "s/    if (!u.estaActivo()) {/    if (false) {/"
```

Se espera que caiga `una cuenta pendiente no puede iniciar sesión` (403 → 200).

**La garantía está implementada y verificada en positivo** por el e2e (la cuenta pendiente recibe
403 con "Usuario aún no ha activado su cuenta"), pero **no está verificada por mutación**: sin la
mutación, nadie puede afirmar que el test no pasa por casualidad.

### D.2 — Bulk invite en el panel admin (3.3)

Sin hacer. Es la única cosa del SDD que queda abierta, y es media tarea: el backend ya expone
`POST /api/v1/auth/invitaciones` y el alta con invitación (`invitar: true` en
`POST /api/v1/admin/usuarios`) ya funciona; falta solamente la parte de la UI.

**Lo que falta, en concreto:**

- `frontend/app/src/app/features/admin/pages/usuarios/admin-usuarios.page.ts` — columna de
  checkboxes, barra de acciones masivas ("Enviar invitación" / "Re-enviar invitación"), spinner
  durante el envío y aviso de "X invitaciones enviadas, Y errores".
- `frontend/app/src/app/features/admin/data/admin.api.ts` — el método que llama a
  `POST /api/v1/auth/invitaciones` con los ids seleccionados.
- Sus tests.

La pantalla de activación (3.1) **sí** está hecha: `frontend/app/src/app/features/auth/pages/activate/`
— componente con checklist de requisitos en tiempo real, countdown de 5 segundos y redirección al
login; `validarToken()` y `activar()` en `auth.api.ts`; ruta `/auth/activate` con `guestGuard`;
9 tests en `activate.page.spec.ts`.

### D.3 — k6 (4.4)

Sin ejecutar. El escenario `scenarios/invite-100.js` no se escribió y no se corrió. Requiere un
backend con carga y SMTP real.

### D.4 — Suite de contrato (`backend/contract-tests/`)

**No seAgregó un `InvitacionesContractIT`**, y no es una decisión de alcance sino una
obstaculación del entorno: la suite corre contra un backend vivo, y

1. sus credenciales por defecto del MASTER (`admsismas@control.ttcs`) son las del `DataSeeder`
   del backend **Spring**, que ya no existe; el Quarkus siembra `mastersolon@example.local`;
2. su helper `registerUser()` usa `POST /api/v1/auth/register` **sin token**, pero en Quarkus ese
   endpoint exige `usuario:create` o MASTER (`Autorizador.exigir("usuario:create", "MASTER")`), así
   que devuelve 401.

Ambos son desajustes **preexistentes** entre la suite de contrato y el backend Quarkus, ajenos a
este change, y que this change **no** toco. El flujo de extremo a extremo quedó cubierto por el
e2e (`src/test/tools/e2e-invitaciones.sh`), que habla HTTP igual que la suite pero además puede
levantar su propio backend y su propio Postgres.

### D.4b — El guard de tokens del frontend

`src/theme/token-guard.spec.ts` corre `scripts/check-tokens.mjs` sobre toda la app y falla si
encuentra un color crudo fuera de la capa de tema. La primera versión de la pantalla de
activación escribia hexadecimales a mano y el guard los znajó: 21 violaciones. La pantalla quedó
con tokens (`--color-surface-card`, `--color-text-primary`, `--space-*`, `--radius-*`...).

Es el estandar del repo funcionando, no un capricho: un color escrito a mano es un color que no
responde al dark mode ni a un cambio de tema.

### D.5 — Auditoría en `audit_log` y no en `invitation_audit`

**Desvío del design (D10).** El design decide una tabla propia `invitation_audit`. Se usó
`audit_log`, que ya tiene las cuatro columnas que D10 pedía para su tabla (`accion`,
`detalle` JSONB, `timestamp`, la FK al usuario), y la escriben **solo** por `AuditLogService`.

Por qué: el estándar del repo —que va pegado en el prompt de toda delegación— dice *"Auditoría
siempre AuditLogService → JPA → PG. Nunca escribir AuditLog directo"*. Dos caminos de escritura
sobre auditoría es la forma habitual de que el rastro se desincronice del estado real. La tabla
`audit_log` tiene `accion VARCHAR(64)` desde el changeset 024, y todos los nombres de acción de
este flujo entran de sobra.

**Consecuencia para el verificador:** el criterio de aceptación de la Task 1.5 que dice
"`invitation_audit` table existe" **no** se cumple y no se va a cumplir. No es un descuido: es
una decisión, y queda acá para que se discuta.

### D.6 — `registrado_por` y `entidad_id` no son lo mismo

Al escribir la auditoría seiun uncovered un error de FK que sólo aparece en ejecución: la
auditoría se escribe en `REQUIRES_NEW` (transacción propia), y si el usuario invitado se acaba de
crear en la transacción en curso, su fila todavía no existe para la otra transacción → violación
de la FK `audit_log_registrado_por_fkey`.

La corrección fue separar los dos ids, que además son cosas distintas:

- `entidad_id` = el usuario **afectado** por el evento. No tiene FK: puede apuntar a una fila sin
  commitear.
- `registrado_por` = quien **hizo** la acción (el MASTER que invita, el usuario que se activa).
  Sí tiene FK: tiene que ser alguien ya commiteado.

### D.7 — El token viaja envuelto en base64url

**El bug más caro de la sesión, y no salió de un test: salió de ejecutarlo.**

Un JWT en base64url puede terminar en uno o dos `=` de relleno (el relleno depende de la longitud
del JSON de los claims, que cambia con el email, el rol y el id del usuario). Ese `=` **no
sobrevive al transporte del correo**: el quoted-printable corta las líneas largas con un salto
suave, y si el corte cae sobre el `=`, el correo entrega un **token corrupto**. El enlace llegaba
al usuario y la activación respondía *"Link de activación inválido"*.

Reproducido con un token real: el token del email tenía `In0..l0AY` — un punto de más — y el
payload, que mide 291 caracteres, no es un base64 válido (291 ≡ 3 mod 4) sin su `=`.

La corrección: **el link lleva el token envuelto en base64url sin relleno**, que solo contiene
`[A-Za-z0-9_-]`. Al llegar al backend se deshace el envoltorio y se trabaja con el JWT exacto,
con su firma intacta.

Alternativa descartada: quitarle el relleno al JWT y recomponerlo al validar. La firma se calcula
sobre las cadenas base64 **exactas** que se emitieron; recomponerlas con relleno cambia la cadena
que se firma y la firma no verifica.

Cubre el caso: `InvitationTokenServiceTest.elTokenDelLinkNoTieneCaracteresQueElCorreoPuedaCorromper`
y `...elEnvoltorioDevuelveElJwtExacto`.

---

## E — Desvíos del SDD (todos deliberados)

El SDD está escrito contra el backend **Spring** y contra un `codegraph` de rutas que no existen
en este repo. Estas son las decisiones que taken; cada una está en el código, no en un TODO.

| # | El SDD pedía | Se hizo | Por qué |
|---|---|---|---|
| 1 | `ec.control.electoral.*` | `ec.twintailcs.electoral.*` | Es el paquete real del módulo Quarkus |
| 2 | `src/main/resources/db/migrations/028-*.sql` (Flyway) | `src/main/resources/database/changelog/028-invitaciones.sql` (Liquibase) + `include` en `db.changelog-master.yaml` | El módulo usa Liquibase con changelog maestro; el orden de ejecución es crítico y lo define el YAML |
| 3 | Tabla `invitation_audit` | `audit_log` vía `AuditLogService` | Estándar del repo (ver D.5) |
| 4 | `@RolesAllowed("ADMIN")` en POST /invitaciones | `autorizador.exigir("usuario:create", "MASTER")` | El `proposal.md` del propio SDD dice que invita el **MASTER**; y en este dominio **ADMIN nunca escribe**. El `@RolesAllowed` además no se usa en ningún lado del repo: la autorización va por `Autorizador` |
| 5 | `sub` = id interno del usuario | `sub` = `publicId` (UUID) | Invariante del repo: los DTOs y los tokens exponen `publicId`, nunca el id interno |
| 6 | Columna `activation_token` con el token en claro | `activation_token_hash` con el HMAC | El token es un bearer secret: un dump de `usuario` no debe entregar invitaciones activables |
| 7 | `estado_usuario DEFAULT 'PENDIENTE_INVITACION'` | `DEFAULT 'ACTIVO'` | El `ALTER TABLE` reescribe las filas existentes: con ese default, **las seis cuentas de referencia quedan pendientes**, el login las rechaza con 403 y el sistema electoral entero queda sin ninguna cuenta con la que entrar. Cubierto por `InvitacionesMigracionIT.lasCuentasQueYaExistianSiguenActivas` y por la mutación M1b |
| 8 | Cola en Redis | Cola en memoria con worker daemon propio | D5 ya deja el reenvío en manos de MASTER y el fallo queda auditado. Redis sería un servicio más que desplegar, monitorear y respaldar para sobrevivir a un reinicio que la decisión D5 ya resuelve por otra vía |
| 9 | `@RateLimit(requests = 10, windowSec = 60)` | `ActivationRateLimiter` propio, en memoria | La anotación `@RateLimit` **no existe** en este repo: es de T-COMPLIANCE-06, otro change. Implementarla era un dependency creep |
| 10 | `PasswordEncoder` (Spring) | `PasswordHasher` (BCrypt, costo 12) | Ya existía en el módulo, con el mismo costo que usa el resto del sistema |
| 11 | `JsonWebToken` + `JwtClaimsBuilder` (Quarkus JWT) | `JwtService` (Nimbus) con `typ=activation` | `quarkus-jwt` no es dependencia del módulo. Reusar `JwtService` deja **una sola** clave y un solo mecanismo de firma; el `typ` separado mantiene los dominios aislados: `parseAccess` rechaza cualquier token que no sea `access` |
| 12 | `POST /invitaciones` con `usuarioIds` numéricos | `List<UUID>` de ids públicos | Los ids internos no viajan por la API |
| 13 | — | `CreateUsuarioRequest.invitar` (boolean, default `false`) | Sin esto el flujo es inalcanzable: no habría forma de crear una cuenta `PENDIENTE_INVITACION`. Con default `false`, el alta con contraseña temporary que el MASTER ya usa sigue igual |
| 14 | — | La contraseña temporal se sigue devolviendo para el usuario invitado | Si no, la rama 403 de "no activaste tu cuenta" queda **muerta**: nadie conoce la contraseña, el login responde 401 y el mensaje útil nunca se ve. Con la temporal conocida, el 403 le dice al usuario lo que tiene que hacer |
| 15 | `GET /validate-token` dice si el usuario existe | No lo dice | Lo contrario permite enumerar cuentas: se distinguiría "token válido de alguien que existe" de "token válido de un id que no existe" |

### D.8 — Un bug de Angular que encontró el test, no la vista

El checklist de requisitos de la pantalla de activación se calculaba con un `computed()` que leía
`form.controls.password.value`. Un `computed()` solo se recalcula cuando cambian las **signals**
que lee, y el valor de un control de formulario no es una signal: el checklist se calculaba una
vez y se quedaba ahí. En la pantalla el checklist no se movía nunca.

Lo detectaron los tests (`el checklist marca los requisitos cumplidos en tiempo real`), no una
revisión a ojo: en una pantalla con un checklist estático el bug pasa desapercibido, porque el
formulario sigue pareciendo razonable.

La corrección: el valor entra por `valueChanges` con `toSignal`, que sí es un flujo con estado.

---

## F — Hallazgos (fuera de scope, no tocados)

1. **`02-builder.md:67` está desactualizado.** Dice que la suite de backend está en rojo con 78
   errores y que el modo TDD estricto no aplica. Hoy hay 4 tests y el modo TDD **sí** aplica.
2. **`ROADMAP.md` §3 cita un módulo que ya no existe** (`backend/electoral-api/`, 63 suites /
   281 tests). Los números del frontend sí cuadran.
3. **La suite de contrato y el backend Quarkus no hablan el mismo idioma** (ver D.4): credenciales
   del MASTER desactualizadas y `POST /api/v1/auth/register` público en Spring, con guard de rol
   en Quarkus.
4. **`audit_log.accion` es VARCHAR(64) desde el 024**, pero el `AuditLogService` original
   construía el `detalle` a mano concatenando strings y escapando solo las comillas. Se cambió a
   Jackson: un salto de línea o una barra invertida en un mensaje rompía el JSONB.
5. **La migración a Quarkus no tuvo SDD.** 170 clases contra 4 tests: la mayor parte de las
   garantías del dominio no tiene cobertura. No es tarea de este change, pero es el agujero más
   grande del repo.
6. **`.github/workflows/ci.yml` tiene cambios sin commitear** (fix del hash bcrypt para el job de
   k6) y `scripts/create-new-sdd-tickets.sh` está sin trackear. Venían de antes de esta sesión y
   no se tocaron. El protocolo de mutación pide `git status --short` limpio: se worked con esa
   línea base registrada y se verificó que después de cada mutación solo aparecieran los archivos
   del change.

---

## G — Cómo se reproduce

```bash
# Suite del módulo (los unitarios no necesitan nada; la IT necesita Docker)
cd backend/electoral-api-quarkus
mvn -B test                                   # 9 suites / 50 tests
mvn -B test -Dtest='!*IT' -DfailIfNoSpecifiedTests=false   # 41 tests, sin Docker

# Extremo a extremo: levanta su Postgres, su backend y su capturador SMTP
bash src/test/tools/e2e-invitaciones.sh        # 21 comprobaciones
```

Herramientas del arnés, en `backend/electoral-api-quarkus/src/test/tools/`:

| Archivo | Qué hace |
|---|---|
| `smtp_capturador.py` | SMTP mínimo que escribe cada mensaje en `/tmp/mailbox`, para poder leer el link de activación que viaja en el email |
| `extraer_token.py` | Saca el token del email **por destinatario** (no "el más reciente": al buzón llegan la invitación, el reenvío y la confirmación, y solo la invitación y el reenvío tienen token) |
| `e2e-invitaciones.sh` | Las 21 comprobaciones del flujo completo |

**Por qué un arnés propio y no un test de JUnit:** el token de activación solo existe dentro del
email. La suite de contrato es black-box (habla HTTP, no ve la base) y este módulo no tiene arnés
de CDI para levantar la aplicación dentro de un test. El script levanta lo mismo, mide lo mismo
y deja el resultado.


---

## H — Ciclo de verificación: qué se verificó y qué no

El 2026-11-06 aparecieron `verify-report.md` y `fixes-required.md` en este change. **Los tres
MINOR que traen se aplicaron** (ver §H.2), pero hay que dejar claro **qué se revisó**, porque
cambia lo que significa el veredicto de "aprobado".

### H.1 — El informe revisó los artefactos del SDD, no la implementación

Evidencia, textual:

| Lo que el informe dice | Lo que el repo tiene |
|---|---|
| "6 decisiones (D1-D6)" | `design.md` tiene **D1-D10** |
| "8 scenarios en Gherkin" | `specs/invitaciones.spec.md` tiene **40+ escenarios** en 7 features |
| "27 tasks … T-AUTH-01.15 (DB schema)", "T-AUTH-01.7", "T-AUTH-01.11" | las tareas reales son "Task 1.1" … "Task 4.4" |
| "T-AUTH-01.2: TokenValidationService" | no existe; la implementación es `InvitationTokenService` |
| "T-AUTH-01.4: ActivationService", "T-AUTH-01.6: ActivationAuditService" | no existen |
| "el Builder puede comenzar ahora con Phase 1 (DB Schema)" | la fase 1 estaba implementada y con 50 tests en verde |
| "Usar Quarkus io.smallrye.jwt. Verificar en pom.xml que la dependencia exista" | `quarkus-smallrye-jwt` **no** está en el `pom.xml`; el token se firma con Nimbus |
| "Queue elegido: Redis Streams (ya presente en stack T-COMPLIANCE-06)" | Redis **no** está en el stack; T-COMPLIANCE-06 no está implementado |
| "activation_token (VARCHAR, nullable)", "estado_usuario (ENUM …)" | `activation_token_hash VARCHAR(64)` y `estado_usuario VARCHAR(32)` con default `ACTIVO` |

El informe cuenta casillas de markdown y compara contra un SDD que no es este. **No re-ejecutó
una sola mutación ni un solo test.** El "aprobado" no cubre el código: cubre el diseño.

Eso es exactamente el hueco que `documents/agents/README.md` avisa que se cierra con un verificador
de contexto limpio: *"Un subagente al que le decís «ya verificamos X, saltátelo» deja de ser un
verificador"*. Acá pasó la variante contraria — un verificador que verificó el documento en vez
del cambio — y el resultado es un "aprobado" que no dice nada sobre lo que se implementó.

**Lo que este change necesita después de mi entrega:** un verificador de verdad, con contexto
limpio, que **re-ejecute las mutaciones** de §C y **mida** los números de §A.2, en vez de leerlos.

### H.2 — Los tres MINOR, aplicados

Están en `design.md`. Se aplicó el **intento** de cada uno, con el contenido que corresponde a la
implementación real; el texto literal del informe describía cosas que no existen en este repo:

| MINOR | Intención | Cómo quedó |
|---|---|---|
| 1 | Aclarar que la validación del token es stateless | **D1**: nota de implementación con los 5 pasos de la validación, los cuatro sin BD, y por qué el secret es simétrico (`app.jwt.secret`) y no una clave pública embebida ni un caché. Se agregó además **quién firma**: Nimbus, no `smallrye-jwt` |
| 2 | Especificar en D6 dónde verifica el estado el login | **D6**: pseudocódigo con el **orden real** (contraseña primero, estado después) y el motivo: al revés, el 403 confirmaría qué emails existen. El pseudocódigo del informe chequeaba el estado **antes** de la contraseña, lo que además de no ser lo implementado abre enumeración de cuentas |
| 3 | Concretar la tecnología de la cola de D3 | **D3**: cola en memoria con worker daemon, rate limit 100/min y backoff 1s/2s/4s. El informe pedía "Redis Streams (ya presente en el stack)": Redis **no** está en el stack y atar este change a T-COMPLIANCE-06 (sin implementar) lo convertía en un bloqueo. Se documentó Redis como alternativa descartada y por qué |

Se aplicó además un hallazgo del `verify-report.md` que **no** estaba en `fixes-required.md`:
en D2, que el token **no** se consume al abrir el link sino al activar con éxito — así el usuario
puede equivocarse en la contraseña y reintentar sin pedir una invitación nueva.

Y se corrigió **D10**, que el informe no llegó a mirar (su alcance era D1-D6): describía una tabla
`invitation_audit` que **no existe en la base**. El design no puede describir un schema
inexistente, así que quedó la nota de implementación que declara el uso de `audit_log` y el SQL
para consultar el rastro.

**Un defecto previo del `design.md`, corregido de paso.** La sección D6 tenía un bloque de código
sin cerrar: el `design.md` original (40 fences) estaba desbalanceado, y al agregar la nota de
MINOR 2 el desbalance se hizo visible. Con el archivo corregido quedan 50 fences y **ninguna
sección con un bloque sin cerrar**. El texto de D6 no se reescribió: se movió el cierre del
bloque para que "Ventajas" y "Alternativa descartada" se lean como prosa y no como código.

**Nada de esto toca la funcionalidad**: son notas de `design.md`. El código no se movió.

---

**Estado de la implementación (al cierre de la primera ronda):** backend completo y verificado de
punta a punta; frontend con la pantalla de activación hecha y verificada (3.1); **bulk invite del
panel admin sin hacer** (D.2); k6 sin ejecutar (D.3); cobertura > 80 % sin medir (D.9). Este
archivo **no** declara el change listo para producción: eso lo decide el verificador y el
Arquitecto.

> Actualizado en **§J**: el bulk invite del panel se hizo en la ronda 2 y el escenario de k6 quedó
> escrito (sin ejecutar). El estado vigente del change está al final de este archivo.

### D.9 — La cobertura no se midió

El módulo no instrumenta JaCoCo: no hay `<plugin>` de jacoco en el `pom.xml`, y la cobertura no
llega a ser compuerta. Los criterios de aceptación que piden "> 80 % de cobertura" quedan sin
marcar en `tasks.md` en vez de marcarse con un número inventado.

Lo que sí se midió, y es lo que el ROADMAP pide: **el número de tests sube**. 4 → 50 en el
backend y 628 → 637 en el frontend. Si al añadir tests el total no sube, los tests nuevos no se
están ejecutando.

---

## I — Verificación final, después de aplicar los MINOR

Los tres MINOR de `fixes-required.md` se aplicaron **solo sobre `design.md`** (notas de diseño). El
código no se movió, así que esta corrida sirve para confirmar dos cosas: que el árbol sigue
compilando y en verde, y que ninguna edición posterior rompió el build.

Medido en esta corrida (2026-11-06, 14:33-14:34):

```
backend spotless   mvn -B spotless:check                              exit 0
backend test       mvn -B test -Dtest='!*IT' -DfailIfNoSpecifiedTests=false
                   7 suites / 41 tests / 0 fallo / 0 error / 0 skip    exit 0
frontend test      pnpm test:ci      67 suites / 637 tests / 0 fallos   exit 0
frontend lint      pnpm lint         0 errores                          exit 0
frontend fmt       pnpm format:check 0 errores                          exit 0
frontend build     pnpm build        exit 0 (los 2 warnings son preexistentes)
git status --short 15 modificados + untracked del change; 0 .bak sueltos; sin commit ni push
```

Los conteos salen de los XML de `target/surefire-reports/`, no del exit code. Los 7 XML con
timestamp de las 14:33 son los de esta corrida (41 tests); los 2 XML de las 02:42 son de la corrida
con Docker de la mañana (`InvitacionesMigracionIT` 7 + `RlsIsolationIT` 2 = 9). Los dos juntos dan
los 50 de §A.2.

**Lo que esta corrida no pudo repetir:** `InvitacionesMigracionIT` (7 tests), el e2e de 21
comprobaciones y la mutación C.10. Los tres necesitan el daemon de Docker, que sigue caído
(`docker.service` inactive; `systemctl start` pide sudo interactivo). Sus números de §A.2 son de
las 02:42 de hoy, con Docker arriba; **no se re-declaran como medidos en esta corrida**. Cuando el
daemon vuelva, el orden para cerrar todo es el de §D.1.

---

## J — Ronda 2: aplicación de `fixes-required-ronda-2.md`

### J.1 — Qué pedía la ronda 2 y qué hice

El `fixes-required-ronda-2.md` llega con **cero bloqueantes de código**: la implementación está
aprobada y lo que queda son pendientes declarados (D.2 bulk invite, D.3 k6, D.10 mutación C.10)
y tres decisiones para el Arquitecto.

Como Builder, lo que me toca de esa lista es lo que está **dentro de `tasks.md`** y es código:

| Pendiente | Tarea | Qué hice |
|---|---|---|
| D.2 — bulk invite en el panel | 3.3 | **hecho** (backend + frontend + tests) |
| D.3 — k6 | 4.4 | **escrito**; la ejecución queda bloqueada |
| D.10 — mutación C.10 | 2.4 | **sigue bloqueada**: necesita Docker |

Lo que **no** es del Builder: decidir si el change se archiva y si D.2/D.3 entran en el scope de
cierre. Eso no lo decido.

### J.2 — Deriva entre lo que dice la ronda 2 y lo que dice el código

Cuatro afirmaciones de la ronda 2 que el código no sostiene (medido, no opinado):

| La ronda 2 dice | El código dice |
|---|---|
| `InvitacionResource.reenviarInvitacion()` (verify §4.2) | ese método **no existe**: el recurso tiene `enviar()` y `validarToken()`. El reenvío es el mismo `POST /api/v1/auth/invitaciones` — el backend cambia la plantilla según tenga token vigente |
| `EmailTemplatesTest 3 (nuevo)` (verify, compuerta 1) | son **7** tests |
| "Desvíos vs SDD: 1 declarado" | §E de este archivo declara **15** desvíos |
| "Mutaciones: 5/5 unitarias efectivas" | §C tiene **10** mutaciones medidas, 2 de ellas e2e |

Ninguna cambia el veredicto. Las anoto porque este informe es lo que va a decidir el cierre y los
números que se lean tienen que ser los del código.

### J.3 — Task 3.3: bulk invite en el panel (hecha)

**Lo que había que decidir antes de escribir una línea.** El backend rechaza el lote **entero**
(409) si una sola cuenta ya está activa. Y el panel no sabía el estado de las cuentas: el DTO de
administración no lo llevaba. Sin ese dato, la casilla de "Enviar invitación" era una trampa: el
operador marcaba 50 cuentas, 30 ya estaban activas y no salía ningún email.

Por eso el primer cambio es de backend y es **aditivo**: `UsuarioResponseDto` ahora lleva
`estadoUsuario` e `invitationSentAt`. El primero decide la casilla y la acción de reenviar; el
segundo distingue "nunca se le invitó" de "se le invitó y no abrió el correo" — la misma señal
que usa el backend para elegir la plantilla de reenvío.

**Lo que hace la pantalla:**

- columna de casillas solo en las cuentas `PENDIENTE_INVITACION`; las activas vienen deshabilitadas
  y **explicando por qué** (`title` y `aria-label`: "La cuenta ya está activa")
- barra de acciones que aparece solo con selección, con el contador de cuentas elegidas
- `Enviar invitación` con spinner y botón deshabilitado mientras el lote está en vuelo
- estado en línea con `role="status"` en el éxito y `role="alert"` en el error
- acción por fila `Enviar/Reenviar invitación`, solo para cuentas pendientes
- la tabla se recarga después de un envío correcto

**Lo que NO hice, y por qué:**

- **Toast**: el repo no tiene servicio de notificaciones (`grep -ri toast src/` → 0 resultados).
  Agregar uno es tocar `shared/ui` para todo el app: es scope de otro change. El mensaje queda en
  línea, con la misma información y con el rol de ARIA que corresponde.
- **Selección entre páginas**: la selección sobrevive al cambio de página (se acumulan ids), pero
  "seleccionar todos los pendientes del sistema" no existe: el panel pagina de a 20 y no hay un
  endpoint que filtre por estado. La fila de casillas toma los de la página actual.
- **Confirmación antes de enviar**: el envío no es destructivo (no borra nada) y el spec describe el
  flujo sin confirmación.

### J.4 — Números medidos (esta ronda)

| | antes | después |
|---|---|---|
| backend, unitarios sin Docker | 41 tests / 7 suites | **45 tests / 8 suites** (+4 `UsuarioMapperTest`) |
| frontend | 637 tests / 67 suites | **651 tests / 68 suites** (+12 bulk invite, +2 API) |
| `mvn -B spotless:check` | exit 0 | exit 0 |
| `pnpm lint` / `format:check` / `build` | exit 0 | exit 0 |

Los números salen de los XML de `target/surefire-reports/` y de la salida de `pnpm test:ci`, no del
exit code.

### J.5 — Mutaciones de esta ronda

**M9 — el mapper deja de mandar el estado** (`UsuarioMapper.toAdminDto`:
`entity.getEstadoUsuario()` → `null`).

Caen, con el nombre exacto:

- `UsuarioMapperTest.elDtoExponeElEstadoDeActivacion`
- `UsuarioMapperTest.unaCuentaActivaSeDistingueDeUnaPendiente`

Restaurado con `cp` desde `.bak`. `git status --short` sin `.bak`.

**M10 — el frontend deja de filtrar las cuentas activas** (`esInvitable()` → `true`).

Caen, con el nombre exacto:

- `unaCuentaActivaNoSePuedeSeleccionar`
- `unaCuentaActivaNoOfreceLaAccionDeReenviar`
- `laCasillaDeUnaCuentaActivaVieneDeshabilitadaYExplicaPorQue`

De los 12 tests de la suite: 3 caen, 9 siguen en verde. Medida dos veces: antes y después de que
Prettier reformateara los archivos.

**C.13 (adaptada).** El spec pedía quitar un `@RolesAllowed("ADMIN")` del frontend, que no existe:
el control real está en el backend (`autorizador.exigir("usuario:create", "MASTER")`) y el e2e ya
midió que un OBSERVER recibe 403. La mutación equivalente que sí decide es la del filtro de
selección (M10): sin ese filtro, el operador marca cuentas que el backend va a rechazar con 409. La
seguridad no depende del frontend en ningún momento — la casilla es cortesía, no control.

### J.6 — Task 4.4: el escenario k6 (escrito, no ejecutado)

`frontend/tests/load/scenarios/invitations.js`, integrado en el harness (`./run.sh invitations`).

Dos patas: `invite` (el MASTER manda lotes) y `activate` (valida y consume tokens reales). La pata
`activate` **necesita tokens de verdad**: el token viaja en el email y el email sale por la cola,
así que el script no puede fabricar uno. Se pasan con `INVITATION_TOKENS` (separados por coma).
Sin tokens, el script mide solo el envío y lo dice por stdout — no inventa números para la pata que
no corrió. Los umbrales son los del SDD: invitación p95 < 5s, activación p95 < 2s, tasa de fallo
< 10 %.

Medido: `k6 v0.55.0` parsea el escenario (`k6 inspect`, exit 0) con y sin tokens; `bash -n run.sh`
sin errores; `node --check` sin errores.

**No ejecutado**: necesita backend vivo y Postgres, y el daemon de Docker está caído.

### J.7 — Hallazgo nuevo, fuera de `tasks.md` (anotado, no arreglado)

El panel manda `rol` y el backend espera `rolNombre`:

- `frontend/app/src/app/features/admin/data/admin.api.ts` → `crearUsuario` y `actualizarUsuario`
  mandan `{ nombre, apellido, rol }`
- `backend/electoral-api-quarkus/.../auth/web/dto/CreateUsuarioRequest.java` → `@NotBlank String rolNombre`
- `backend/electoral-api-quarkus/.../auth/web/dto/UpdateUsuarioRequest.java` → `@NotBlank String rolNombre`

Con `rolNombre` en null, la validación `@NotBlank` falla: **crear o editar un usuario desde el panel
devuelve 400**. No lo arreglé porque está fuera de `tasks.md` (este change es de invitaciones, no
del alta de usuarios) y el `02-builder.md` manda anotar en vez de arreglar por fuera. Queda para el
Arquitecto: el arreglo es renombrar el campo en el modelo del frontend, pero toca el alta de
usuarios, que es otra historia.

### J.8 — Lo que sigue abierto (declarado, no omitido)

1. **C.10 / D.10** — mutación del login: bloqueada por Docker.
2. **M1 / C.1** — mutación de la migración 028: bloqueada por Docker (necesita la IT).
3. **Task 4.4 ejecutado** — bloqueada por Docker, y por tokens reales de activación.
4. **Cobertura > 80 %** — el módulo no instrumenta JaCoCo (D.9).
5. **Suite de contrato** — no extendida: sus credenciales del MASTER son del backend Spring que ya
   no existe (D.4).
6. **El bug `rol` vs `rolNombre`** de J.7 — decisión del Arquitecto.
7. **Selección masiva entre páginas** — sin endpoint que filtre por estado; queda la paginación.

**Estado de la implementación tras esta ronda:** backend completo; frontend con la pantalla de
activación y con el bulk invite del panel; k6 escrito y sin ejecutar. Este archivo **no** declara
el change listo para producción: eso lo decide el verificador y el Arquitecto.

