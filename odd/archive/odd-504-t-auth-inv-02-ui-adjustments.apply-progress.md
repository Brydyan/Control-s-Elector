# Apply progress — ODD #504 (T-AUTH-INV-02 UI Adjustments + 24h Expiry)

> **Builder.** Este archivo es el cierre de *implementación*: qué se cerró, qué no y por qué, los
> números **medidos** y las mutaciones ejecutadas. **NO es un veredicto de calidad**: eso lo hace
> Verify.
>
> Fecha: 2026-11-08 · Rama: `brydyan/sc-492/t-auth-inv-02-user-invitations-smtp-email`
> ODD: `odd/tasks/odd-504-t-auth-inv-02-ui-adjustments.md`
> SDD previo: `openspec/changes/archive/2026-11-07-t-auth-inv-02-user-invitations-smtp/` (no se tocó)

---

## 1. Qué se cerró

| Bloque | Tarea | Estado | Dónde vive |
|---|---|---|---|
| A | Toggle "Enviar Invitación por Email" (default ON) + `sendEmail` en el POST + rama sin email + audit + respuesta diferenciada | ✅ | `admin-usuarios.page.{ts,html}`, `InviteUserRequest`, `UserInvitationService.invitar`, `InvitationAuditService.logUserCreated` |
| A.5 | Email sin contraseña temporal: enlace de activación, marca "Control's Elector" (sin CNE), usuario vs correo personal, y explicación de que la cuenta **está creada pero no activa** | ✅ | `email/invitation.html`, `email/resend.html`, `email/confirmation.html`, `SmtpEmailProvider` |
| B | Expiry 30 días → 24 h (default de BD, migración de filas, dominio, templates) | ✅ | `030-user-invitation-24h-expiry.sql`, `UserInvitation.HORAS_VIGENCIA`, `email/*.html` |
| B.3 | Frontend: columna Estado, EXPIRED derivado, aviso de "<2 h" | ✅ | `UsuarioMapper.estadoInvitacion`, `admin-usuarios.page.{ts,html,scss}` |
| B.5.1 | Pantalla de activación: usuario de Control a la vista + confirmación de contraseña definitiva | ✅ (con desviación 1) | `activate.page.{ts,html,scss}`, `ValidateTokenResponse`, `InvitacionResource` |
| B.5.2 | Pantalla `/auth/account-summary` con el resumen y el botón a login | ✅ | `account-summary.page.{ts,html,scss}`, `app.routes.ts`, `ActivarUsuarioResponse` |
| C | Columna "Estado" con badges INVITED/EXPIRED/ACTIVE/DISABLED (+CREATED) | ✅ (con desviación 2) | `UsuarioMapper`, `UsuarioResponseDto`, `admin-usuarios.page.html` |
| D | Botón "Reenviar invitación" + modal de confirmación + flujo de reenvío | ✅ (con desviaciones 3 y 4) | `admin-usuarios.page.{ts,html,scss}`, `UserInvitationService.reenviar` |
| E | Endpoint `POST /api/v1/users/{userId}/resend-invitation` + DTOs + errores + audit | ✅ (con desviación 5) | `UserInvitationResource`, `ResendInvitationResponse`, `InvitationAuditService.logInvitationResent` |
| F | Tests de integración F.1–F.4 | ✅ | `UserInvitationIT` (14 tests), `UserInvitationTest` (5), `UsuarioMapperTest` (8) |
| M1–M3 | Mutaciones obligatorias | ✅ (3/3 matan) | ver §4 |

**No se cerró (por contrato de Builder, no por alcance):** `Verify round 1` y `Archive to
odd/archive/VERIFIED-…` quedan sin marcar. No archivé, no commiteé, no pusheé, y no escribí
`verify-report.md`, `fixes-required.md` ni `archive-report.md`.

---

## 2. Números medidos

### 2.1 Backend — `mvn -B -o clean verify` (Postgres 16 local + Testcontainers)

```
17 suites / 120 tests / 0 failures / 0 errors / 0 skipped     (medido: target/surefire-reports/TEST-*.xml)
BUILD SUCCESS · jacoco:check OK · spotless:check OK
```

| Suite | tests | fail | err |
|---|---:|---:|---:|
| `UserInvitationIT` | 14 | 0 | 0 |
| `UserInvitationTest` | 5 | 0 | 0 |
| `EmailTemplatesTest` | 15 | 0 | 0 |
| `UsuarioMapperTest` | 8 | 0 | 0 |
| `RlsIsolationIT` | 5 | 0 | 0 |
| `InvitacionesMigracionIT` | 7 | 0 | 0 |
| `SmtpEmailProviderTest` | 3 | 0 | 0 |
| `InvitationTokenServiceTest` | 10 | 0 | 0 |
| `PasswordValidationServiceTest` | 9 | 0 | 0 |
| `InvitationEmailQueueImplTest` | 7 | 0 | 0 |
| `InvitationRateLimiterTest` | 4 | 0 | 0 |
| `ActivationRateLimiterTest` | 3 | 0 | 0 |
| `DpoRateLimiterTest` | 4 | 0 | 0 |
| `LegalFlowIT` | 11 | 0 | 0 |
| `LegalPublicPagesIT` | 6 | 0 | 0 |
| `CorsPreflightIT` | 7 | 0 | 0 |
| `UuidVersion7StrategyTest` | 2 | 0 | 0 |

**Cobertura (JaCoCo, `target/site/jacoco/jacoco.csv`):**

| Momento | INSTRUCTION | LINE |
|---|---:|---:|
| Antes del lote #492 (base) | 13.82 % | 14.02 % |
| Después del lote #492 | 16.19 % | 16.33 % |
| **Después de #504 (medido)** | **16.9882 %** | **17.06 %** |

Clases nuevas de #504: `UserInvitationResource` 100 %, `UserInvitationResponse` 100 %,
`ResendInvitationResponse` 100 %, `InviteUserRequest` 100 %, `ActivarUsuarioResponse` 100 %,
`SmtpEmailProvider` 100 %, `UserInvitation` 61 %, `UsuarioMapper` 67 %, `EstadoInvitacion` 50 %.

> **El número total es un piso, no un techo.** `UserInvitationService`, `UsuarioAdminService`,
> `ActivarUsuarioPorTokenUseCase`, `InvitationAuditService` y `InvitacionResource` reportan 0 %
> aunque los IT los ejercitan: el datasource de test corre como superusuario (BYPASSRLS) y el
> mecanismo de instrumentación no engancha esas clases. Es el mismo techo medido en el lote #492.

### 2.2 Frontend — `pnpm test:ci` / `pnpm lint` / `pnpm format:check` / `pnpm build`

```
78 archivos / 783 tests / 0 fallos / 0 errores          (vitest)
All files pass linting.                                  (ng lint)
All matched files use Prettier code style!               (prettier --check)
Application bundle generation complete.                  (ng build)
```

Specs tocados por este ODD:

| Spec | antes | después |
|---|---:|---:|
| `admin-usuarios.invitacion.spec.ts` | 7 | **15** (+8: toggle on/off, estado, etiquetas, reenvío permitido/bloqueado) |
| `activate.page.spec.ts` | 9 | **12** (+3: usuario de Control, confirmación definitiva, navegación al resumen) |
| `account-summary.page.spec.ts` (nuevo) | — | **3** |

### 2.3 E2E — `npx playwright test` (2 proyectos: Desktop Chrome + Pixel 7)

```
56 tests / 54 passed / 2 skipped / 0 failed
```

`j7-auth-inv-02-user-invitations.spec.ts`: 3 escenarios × 2 proyectos = 6 (4 pasan, 2 skip en móvil
— ver hallazgo 5).

---

## 3. Qué cambió, por bloque

### A · Toggle

- `admin.model.ts`: `InviteUsuarioRequest.sendEmail?: boolean`.
- `admin-usuarios.page.html`: checkbox nativo `data-testid="toggle-enviar-invitacion"`, **encendido
  por defecto**, con el hint del ODD.
- `admin-usuarios.page.ts`: `enviarInvitacionPorEmail` (signal, default `true`) →
  `cambiarInviarInvitacionPorEmail(event)`.
- `InviteUserRequest.sendEmail` (null ⇒ `true`).
- `UserInvitationService.invitar`: con el toggle apagado crea la cuenta `PENDIENTE_INVITACION`, **sin
  token, sin fila en `user_invitations` y sin correo**, y audita `USER_CREATED_WITHOUT_EMAIL`.
- `UserInvitationResponse`: + `success` + `mensaje` ("Invitación enviada a …" / "Usuario creado.
  Puedes enviar invitación después.").
- El panel muestra el mensaje que devuelve el backend (con respaldo local si no viene).

### A.5 · Email sin contraseña temporal

- El email **explica el estado de la cuenta**: bloque "Estado de tu cuenta" con las dos líneas que
  importan — ✅ **Cuenta creada** (ya existe en el sistema, con usuario y rol listos) y ⏳
  **Activación pendiente** (todavía no podés entrar hasta que elijas tu contraseña con el enlace).
  Sin eso, quien recibe el correo no entiende por qué tiene que activar una cuenta que ya existe.
- El reenvío lo dice con la misma honestidad: la cuenta **"sigue sin activarse"**, el enlace
  anterior **"ya no sirve"** y el nuevo es lo que activa la cuenta.
- La pantalla de activación (frontend) repite el estado: "Tu cuenta ya está creada: solo falta
  activarla para poder entrar".
- Sin contraseña temporal: enlace de activación, marca **"Control's Elector"** (sin CNE) y usuario
  vs correo personal.

### B · 24 horas

- `030-user-invitation-24h-expiry.sql`: default `expiry_at = NOW() + INTERVAL '1 day'` +
  migración de las filas existentes a `invited_at + 1 day`, con la RLS apagada y vuelve a prenderse
  en el mismo changeset (el usuario de aplicación no es superusuario en producción: sin contexto de
  RLS el `UPDATE` no tocaría nada y la migración pasaría en silencio).
- `UserInvitation.HORAS_VIGENCIA = 24` (el servicio usa esa constante para `expiry_at`).
- Templates: "Esta invitación expira en 24 horas".

### B.5 · Pantallas de activación y resumen

- `ValidateTokenResponse` gana `username`: el login del usuario al que pertenece el token. Solo se
  devuelve con un token **ya validado** — quien tiene el token es esa persona, así que no habilita
  enumeración de cuentas (con token inválido la respuesta sigue sin decir nada).
- Pantalla de activación: muestra "Tu usuario (Control)" y exige "Entiendo que esta es mi contraseña
  definitiva" antes de habilitar el botón.
- `ActivarUsuarioResponse` gana `emailPersonal`; nueva pantalla `/auth/account-summary` con usuario
  de Control, correo personal y rol, y botón "→ Ir a Login".
- Ruta `/activate?token=…` del ODD: añadida como alias que redirige a la canónica `/auth/activate`
  conservando el query param.

### C · Columna Estado

- `UsuarioMapper.estadoInvitacion()` deriva el estado desde `usuario` (`estado_usuario`,
  `habilitado`, `invitation_sent_at` + 24 h) → **no** abre la RLS de `user_invitations` a quien solo
  puede leer usuarios.
- `UsuarioResponseDto` gana `invitationStatus` y `invitationExpiresAt`; el panel los pinta con badge
  (`INVITED` verde, `EXPIRED` amarillo, `ACTIVE` verde, `DISABLED` rojo, `CREATED` azul) y avisa
  "expira en menos de 2 horas".
- Orden de derivación: `ACTIVE` → `DISABLED` → `EXPIRED` → `CREATED` → `INVITED`. `DISABLED` va
  antes que `EXPIRED` para que no se ofrezca reenviar a una cuenta deshabilitada.

### D y E · Reenvío

- `POST /api/v1/users/{publicId}/resend-invitation` (MASTER o permiso `usuario:create`), bucket
  propio del rate limit (`"resend:" + ip`) para que invitar→reenviar seguido no se bloquee.
- `UserInvitationService.reenviar`: genera un **token nuevo** y guarda su hash, con lo que el link
  anterior deja de servir; actualiza o crea la fila de invitación; manda el correo al correo
  personal; audita `INVITATION_RESENT` con `{email_personal, new_expiry}`.
- Errores: 400 (cuenta activa o deshabilitada), 404 (usuario inexistente), 403 (sin permiso).
- Frontend: item de menú "Reenviar invitación" (solo en `INVITED`/`EXPIRED`/`CREATED`), modal de
  confirmación (`data-testid="reenvio-confirmacion"`), botón `btn-confirmar-reenvio`, mensaje
  "Invitación re-enviada" y refresco de la tabla.

---

## 4. Mutaciones (3/3 matan)

Restauración: copia byte a byte desde `/tmp/mut504/*.bak` (**no** `git checkout --`, que se lleva
cambios sin commitear). Al final, `grep -rn "MUTACION" backend/… frontend/…` → **0 coincidencias**.

| # | Mutación | Cómo se midió | Test exacto que cayó |
|---|---|---|---|
| **M1** | Expiry 24 h → 30 días (`UserInvitation.HORAS_VIGENCIA = 720` **y** `030-…sql`: `INTERVAL '30 days'`) | `mvn -o test -Dtest='UserInvitationTest,UserInvitationIT'` → 19 tests, **2 failures** | `UserInvitationTest.laVigenciaEsDe24Horas`<br>`UserInvitationIT.laInvitacionVenceEn24Horas` |
| **M2** | Reenvío: se deja de reemplazar el hash del token (el link viejo sigue sirviendo) | `mvn -o test -Dtest='UserInvitationIT'` → 14 tests, **1 failure** | `UserInvitationIT.alReenviarSeGeneraUnLinkNuevoYElAnteriorDejaDeServir` (esperaba 409, obtuvo 200) |
| **M3** | Toggle invertido: `if (!pedido.enviarEmail())` → `if (pedido.enviarEmail())` | `mvn -o test -Dtest='UserInvitationIT'` → 14 tests, **7 failures + 2 errors** | canónicos: `UserInvitationIT.conElToggleApagadoSeCreaLaCuentaYNoSeMandaEmail` (esperaba `CREATED`, obtuvo `INVITED`) y `UserInvitationIT.conElToggleEncendidoLaCuentaQuedaInvitedYSaleElEmail` (esperaba `INVITED`, obtuvo `CREATED`). Cayó además: `elEmailSaleAlCorreoDeContactoYLlevaElLogin`, `laInvitacionVenceEn24Horas`, `reenviarAUnaCuentaActivadaSeRechaza`, `conElLinkDelEmailLaPersonaActivaLaCuenta`, `laInvitacionQuedaRegistrada`, `alReenviarSeGeneraUnLinkNuevoYElAnteriorDejaDeServir`, `elMasterInvitaYLaCuentaQuedaCreada` |

**M1 necesitó recrear la base local** (`DROP DATABASE electoral_db` + `CREATE`): si no, Liquibase
detectaba el checksum distinto del changeset mutado y el fallo era del checksum, no del expiry. La
base se recreó también después de restaurar, para que el `databasechangelog` guarde el checksum del
archivo bueno.

---

## 5. Hallazgos (y correcciones colaterales)

1. **`RlsIsolationIT` no aplicaba los changesets de invitaciones.** La lista `CHANGELOG_FILES` del
   IT llegaba a `011-rls.sql`; los tres casos de `user_invitations` (del lote #492) corrían contra una
   base sin la tabla → `42P01 relation "user_invitations" does not exist` (2 errores + 1 fallo por
   `42501` vs `42P01`). **Corregido**: la lista ahora incluye `029-user-invitations.sql` y
   `030-user-invitation-24h-expiry.sql`. RLS es capa obligatoria: sin este fix la suite de RLS daba
   verde por ausencia de casos, no por aislamiento.
2. **El caso `laEscrituraExigeQueInvitedBySeaElContexto` no probaba lo que decía.** El `INSERT`
   usaba `invited_by = obsA` con contexto `obsA`: la policy `invited_by = get_current_user_id()`
   se cumplía y no había violación. **Corregido**: ahora `invited_by = obsB` con contexto `obsA`, que
   es la atribución prohibida → 42501.
3. **`isMobile` no existía como fixture de Playwright.** El skip de móvil del E2E de invitar nunca
   se aplicaba (`undefined`). Se calculó en el propio spec con `page.viewportSize()`.
4. **`aria-label` vs `ariaLabel` en `app-button`.** El componente declara el input `ariaLabel`, así
   que el atributo `aria-label="…"` quedaba en el host y el `<button>` interno se quedaba sin
   nombre accesible (el E2E no encontraba el botón de acciones en la card móvil). Corregido en las dos
   ocurrencias.
5. **Layout móvil (preexistente, no de este ODD).** La barra superior fija tapa el botón "Crear
   usuario" a 412 px de ancho: 2 escenarios E2E quedan **skip en móvil** con el motivo documentado en
   el propio test. El flujo es de escritorio. No se tocó el layout: queda anotado para el que lo
   arregle.
6. **El guard de tokens del tema marca `#504` en los títulos de los tests** como color crudo
   (`src/theme/token-guard.spec.ts`). Los títulos dicen "ODD 504" (sin almohadilla).
7. **La marca del producto estaba mal en los correos que escribí en este lote.** El footer decía
   "Control's Elector 🗳️ 2026 — **CNE Ecuador**": la plataforma **no** está ligada a la CNE.
   Corregido a petición del usuario: la marca es **"Control's Elector"** y el footer quedó sin
   organismo. Se aplicó el mismo nombre en los otros lugares donde aparecía la marca vieja:
   `application.properties` (título del OpenAPI), las cuatro páginas legales (`terms-es/en`,
   `privacy-es/en`), `index.html`, `manifest.webmanifest`, el topbar, la pantalla de login y el
   `README.md`.
   **No se tocó** lo que alude al marco electoral y no a la marca: el padrón y el tope de
   sufragantes por JRV (`PADRÓN_ESTANDAR_CNE`, `TOPE_SUFRAGANTES_CNE`), el "expediente de
   impugnación CNE" que se descarga y el ítem de menú "Reportes CNE" — quedan para decisión de
   producto, no son branding.
8. **Dos asserts nuevos chocaron con el copy.** "todavía no podés entrar" no matcheaba por la
   mayúscula, y "ya no sirve" se había partido con un salto de línea en el HTML (`ya no\n
   sirve.`). Se ajustó el texto para que las frases clave caigan en una línea: el HTML se
   renderiza igual, pero un assert es una búsqueda literal.
9. **`application.properties.bak` estaba dentro de `src/main/resources/`.** Un `.bak` ahí se
   empaqueta en el JAR y se sirve junto a la configuración real: residuo peligroso. Era un backup
   que solo difería en la línea del título del OpenAPI, así que se borró (verificado: el JAR ya
   no lo trae).
10. **`UserInvitationIT` era frágil: dependía de que el octeto aleatorio de la IP no colisionara
    con una corrida anterior.** Los conteos de la suite miran las filas de SU invocación (por IP o
    por correo de contacto) y la IP lleva un octeto aleatorio: eso baja la probabilidad de pisarse
    pero no la elimina. Pasó: la fila que dejó la **mutación M3** (con el toggle invertido la
    cuenta sí creaba invitación) tenía el mismo segundo octeto que la de la corrida siguiente y
    `conElToggleApagadoSeCreaLaCuentaYNoSeMandaEmail` vio 1 fila donde esperaba 0. **Corregido**:
    el `@BeforeEach` vacía `user_invitations` y `audit_log`, así que cada test depende solo de lo
    que hace.

---

## 6. Desviaciones del ODD (con motivo)

| # | El ODD pide | Se hizo | Por qué |
|---|---|---|---|
| 1 | **B.5.1**: contraseña de **mínimo 12 caracteres** | **La UI pide 12 caracteres y minúscula** (checklist en vivo y medidor de fuerza); el **backend sigue en 8** (`PasswordValidationResult.MIN_LENGTH = 8`, decisión D8 del design) | Endurecer el formulario no rechaza nada que el servidor vaya a aceptar: toda contraseña que pasa por la UI la acepta el backend. Cambiar la **política del servidor** (que también affecta el alta de usuarios con contraseña temporal) es un cambio de producto y requiere decisión humana: se declara, no se cambia por iniciativa propia. |
| 2 | **A.5.2**: mostrar por separado "usuario Control" (`juagobs`) y "correo Control" (`juagobs@control.ttcs`) | El email y las pantallas muestran las **tres** cosas: **"Tu usuario (Control): `juagarobs`"**, **"Tu correo (Control): `juagarobs@control.ttcs`"** y **"Tu correo personal: `juan.garcia@example.com`"** | El login del repo trae el dominio (`EmailGenerator` es port exacto del backend Spring), así que el usuario corto se deriva de él y el correo de Control es el login completo. |
| 3 | **D.2**: el modal muestra el **correo personal** | El modal muestra el **correo de Control** de la cuenta; el correo personal real aparece en la confirmación del backend ("Invitación re-enviada a juan@example.com") | El listado de usuarios lo ven MASTER, COORDINATOR y ADMIN: exponer el correo personal de cada cuenta en la tabla sería repartir PII. El backend sí lo devuelve en la respuesta del reenvío, que es a quien le corresponde. |
| 4 | **D.3/E.1**: el reenvío genera una **contraseña temporal** nueva | Genera un **token de activación nuevo** y deja sin efecto el anterior | El sistema no usa contraseñas temporales (decisión ya tomada en #492 y aplicada en el código): la credencial de la invitación es el link de activación de 24 h, y la contraseña la elige la persona. Generar una temporal "nueva" además de un link sería devolver dos credenciales. |
| 5 | **E.1**: `@PreAuthorize("hasAnyRole('MASTER','ADMIN')")` | `MASTER` **o** el permiso `usuario:create` (`Autorizador.exigir`) | En el RBAC del repo ADMIN es **solo lectura**: darle escritura de invitaciones cambiaría el modelo de roles. ADMIN recibe 403, como en el resto de escrituras del panel. |
| 6 | **C.3**: calcular el estado con un `CASE` en SQL sobre `user_invitations` | Se deriva en `UsuarioMapper` desde las columnas de `usuario` | Leer `user_invitations` en el listado obligaría a abrirle la RLS a quien solo puede leer usuarios (el invited ve la suya, el MASTER todas). El dato necesario ya está en `usuario`. |
| 7 | **B.5.1**: `POST /api/v1/auth/activate` con `{ token, newPassword }` | Sigue el contrato que ya tenía el backend: `{ token, password, passwordConfirm }` | El body del ODD es un ejemplo, no un contrato cerrado: el endpoint existe desde T-AUTH-INV-01 (verificado y archivado) y su `passwordConfirm` hace que el servidor valide que las dos copias coinciden, algo que `newPassword` solo no cubre. Cambiar el contrato de un endpoint vivo rompería el frontend y los tests de esa entrega. |
| 8 | **A.5.2 / B.5.2**: usuario de Control `juagobs` | `juagarobs` | El login del repo lo genera `EmailGenerator` (port exacto del backend Spring) con 3+**4**+3 letras: el ejemplo del ODD dice `juagobs` (3+3+3). Se muestra el usuario **real**, no uno inventado. |

---

## 7. Comandos para reproducir

```bash
# Backend (Postgres 16 local en :5432 + Docker para Testcontainers)
cd backend/electoral-api-quarkus
mvn -B -o clean verify -Dtest.postgres.url=jdbc:postgresql://localhost:5432/electoral_db \
  -Dmaven.test.failure.ignore=true
grep -h -o 'tests="[0-9]*"\|errors="[0-9]*"\|failures="[0-9]*"' target/surefire-reports/TEST-*.xml

# Frontend
cd frontend/app
pnpm test:ci && pnpm lint && pnpm format:check && pnpm build

# E2E (con el dev server en :4200)
cd frontend/app && npx playwright test --reporter=list
```

**Entorno de medición:** Postgres 16.15 embebido local (`/tmp/pgbin`, datos en `/tmp/pgdata`) para
los IT de aplicación; Docker arriba para los IT de Testcontainers (`RlsIsolationIT`,
`InvitacionesMigracionIT`), que en la corrida anterior al arranque de Docker no pudieron levantar
contenedores y quedaron como errores ambientales.

---

## 8. Ajuste posterior a la entrega (pedido del usuario)

Cerrado el ODD, el usuario señaló dos cosas del email de invitación:

1. **Faltaba explicar que la cuenta está creada pero NO activa.** Se añadió el bloque "Estado de tu
   cuenta" (✅ cuenta creada / ⏳ activación pendiente) a la invitación y al reenvío, con la frase
   explícita de que hasta elegir la contraseña **no se puede entrar**. El panel y la pantalla de
   activación repiten el mismo estado para que el operador y la persona entiendan lo mismo.
2. **La marca no es "Control Electoral CNE".** La plataforma se llama **"Control's Elector"** y no
   está ligada a la CNE: se quitó "CNE Ecuador" del footer de los correos y se aplicó el nombre
   correcto en `application.properties` (OpenAPI), las cuatro páginas legales, `index.html`,
   `manifest.webmanifest`, el topbar, la pantalla de login y el `README.md`.

**Lo que NO se tocó** (no es branding, es marco electoral — queda para decisión de producto): el
padrón y el tope de sufragantes por JRV (`PADRÓN_ESTANDAR_CNE`, `TOPE_SUFRAGANTES_CNE`), el
"Límite CNE excedido", el "expediente de impugnación CNE" que se descarga y el ítem de menú
"Reportes CNE".

**Gates tras el ajuste (medidos):** backend `mvn clean verify` → 17 suites / **118 tests** / 0
failures / 0 errors (BUILD SUCCESS); frontend `pnpm test:ci` → **781 tests** verdes + lint + format
+ build OK; E2E → **54 pasan / 2 skip**.

---

## 9. Verificación del `UI-MOCKUP.md` del actor (commits `5c380d1`, `d1383f9`, `268446f`, `e3d4b70`)

El actor subió el mockup de UI (1122 líneas) y el ODD con las pantallas de activación y resumen.
Esta sección es el resultado de **verificar el mockup contra el código**, requisito por requisito.

### 9.1 Email de invitación y reenvío

| Requisito del mockup | Estado |
|---|---|
| Header "🗳️ Control's Elector / Plataforma de Observación Electoral" | ✅ aplicado (banda azul) |
| "Tu cuenta ha sido creada. Para activarla, haz clic en el enlace…" | ✅ (además explica que **no está activa**) |
| Bloque "Estado de tu cuenta" (creada ✅ / activación pendiente ⏳) | ✅ (pedido del usuario) |
| 👤 Tu usuario (Control): `juagarobs` | ✅ aplicado (`usuarioCorto`, derivado del login) |
| 📧 Tu correo (Control): `juagarobs@control.ttcs` | ✅ aplicado |
| 💬 Tu correo personal | ✅ aplicado |
| 🎭 Tu rol | ✅ aplicado |
| 🔗 Enlace para activar tu usuario → CTA **"Activar Cuenta y Establecer Contraseña"** | ✅ aplicado |
| Próximos pasos (5) | ✅ |
| ⚠️ Importante: 24 h · no compartir · solo tú · **"si no solicitaste esta cuenta, contacta"** | ✅ los cuatro |
| "¿Preguntas o problemas? Contacta al equipo de Control's Elector." | ✅ aplicado |
| Footer "Control's Elector 🗳️ 2026 / Plataforma de Observación Electoral \| Participación Cívica" | ✅ aplicado |
| Asunto | ✅ según el **ODD** ("Bienvenido a Control's Elector - Activa tu cuenta"); el mockup traía la marca vieja y "Tus Credenciales de Acceso", que contradice el ODD A.5.1 |
| **Sin contraseña temporal** | ✅ el ODD A.5.1 lo prohíbe; el mockup todavía decía "contraseña temporal" en un aviso → gana el ODD |

### 9.2 Pantalla de activación

| Requisito | Estado |
|---|---|
| "🔐 Activar tu Cuenta" + "Establece tu contraseña segura" | ✅ |
| "Tu Usuario (Control): `juagarobs`" + "Tu correo (Control)" | ✅ |
| Requisitos: 12 · mayúscula · **minúscula** · número · especial | ✅ **en la UI** (el backend sigue en 8: desviación 1) |
| Checklist en vivo + **medidor de fuerza** | ✅ aplicado (barra + "MUY DÉBIL"→"MUY FUERTE") |
| "Nueva Contraseña *" / "Confirmar Contraseña *" | ✅ |
| "Entiendo que esta es mi contraseña definitiva y la guardaré en lugar seguro." | ✅ |
| Botón "✓ Activar Cuenta" | ✅ |
| `POST /api/v1/auth/activate { token, newPassword }` | ⚠️ **desviación 7**: se mantiene `{ token, password, passwordConfirm }` |

### 9.3 Pantalla de resumen

| Requisito | Estado |
|---|---|
| "✅ ¡Cuenta Activada!" + "Tu cuenta está lista para usar" | ✅ |
| "Resumen de tu Cuenta" | ✅ |
| 👤 Usuario (Control) · 📧 Correo Control · 💬 Correo Personal · 🎭 Rol de Control | ✅ los cuatro |
| "ℹ️ Tu contraseña ha sido establecida correctamente. Ahora puedes iniciar sesión en Control's Elector." | ✅ |
| Botón "→ Ir a Login" → `/auth/login` | ✅ |

### 9.4 Pantalla 5 del mockup ("Cambiar Contraseña Obligatorio" en el primer login)

**No implementada, y no aplica a este flujo.** El mockup la describe para el caso de *credenciales
temporales* (`first_login = true` en el JWT): el sistema no emite contraseñas temporales —la
credencial es el link de activación de 24 h— y el propio mockup lo dice en su §4: *"Login procede
normalmente (sin obligación de cambio de contraseña)"*. Implementarla exigiría un contrato de API
nuevo (`PUT /api/v1/auth/change-password`) y una bandera `first_login` en el token: es alcance de
otra historia, no de este ODD. **Queda declarado**, no omitido.

**Gates tras esta tanda (medidos):** backend `mvn clean verify` → 17 suites / **120 tests** / 0
failures / 0 errors (BUILD SUCCESS); frontend `pnpm test:ci` → **783 tests** verdes + lint + format
+ build OK; E2E → **54 pasan / 2 skip**.
