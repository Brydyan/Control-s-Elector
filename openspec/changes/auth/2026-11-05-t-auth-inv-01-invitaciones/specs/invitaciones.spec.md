# Specs: T-AUTH-INV-01 — Invitaciones por Email

**Format:** Gherkin (GIVEN/WHEN/THEN)  
**Depends on:** design.md (decisiones documentadas)  
**Last Updated:** 2026-11-05

---

## Feature: Enviar Invitación por Email

```gherkin
Feature: MASTER envía invitaciones a usuarios en bulk

  Scenario: Enviar invitación a un único usuario
    GIVEN un usuario "juan@control.ttcs" con estado "PENDIENTE_INVITACION"
    AND email service está disponible
    WHEN el MASTER invita al usuario
    THEN se genera un token JWT con expiry en 24 horas
    AND el token se guarda en usuario.activation_token
    AND se envía un email a juan@control.ttcs con link de activación
    AND invitation_audit registra evento "INVITATION_SENT"
    AND la respuesta incluye {status: "processing", sentCount: 1}

  Scenario: Enviar invitación a múltiples usuarios (bulk)
    GIVEN 50 usuarios con estado "PENDIENTE_INVITACION"
    AND email service está disponible
    WHEN el MASTER selecciona todos 50 y clica "Enviar Invitación"
    THEN para cada usuario:
      - Se genera un token JWT único (no reutilizable)
      - El token se guarda en usuario.activation_token
      - Se envía email con link personalizado
    AND la respuesta retorna inmediatamente {status: "processing", sentCount: 50}
    AND la UI muestra spinner de progreso
    AND invitation_audit registra 50 eventos INVITATION_SENT

  Scenario: No se puede invitar usuario que ya está ACTIVO
    GIVEN un usuario "juan@control.ttcs" con estado "ACTIVO"
    WHEN el MASTER intenta enviar invitación
    THEN la solicitud falla con error 409 "Usuario ya está activo"
    AND no se envía email
    AND invitation_audit registra "INVITATION_REJECTED" con razón

  Scenario: Rate limiting: no más de 100 emails/minuto
    GIVEN 500 usuarios pendientes de invitación
    WHEN el MASTER selecciona todos 500
    AND clica "Enviar Invitación"
    THEN los primeros 100 emails se envían en < 1 minuto
    AND los siguientes se colan (rate limit: 100/min)
    AND después de 5 minutos, todos 500 fueron enviados
    AND el UI muestra progreso: "100/500 enviados"

  Scenario: Email falla → retry automático 3 veces con backoff exponencial
    GIVEN SMTP server timeout temporalmente
    WHEN se intenta enviar email a usuario
    THEN reintenta inmediatamente (intento 1)
    AND si falla, espera 1 segundo y reintenta (intento 2)
    AND si falla, espera 2 segundos y reintenta (intento 3)
    AND si intento 3 falla, registra error en invitation_audit
    AND MASTER puede ver "Error enviando a user@example.com" en UI
    AND MASTER puede clica "Re-intentar" para esos fallidos

  Scenario: Token JWT se genera con expiry = NOW + 24h
    GIVEN un usuario pendiente de invitación
    WHEN se genera token JWT
    THEN el token tiene:
      - sub: usuarioId
      - iat: timestamp actual
      - exp: timestamp + 86400 segundos (24 horas)
      - alg: HS256
      - firma: válida con JWT_SECRET
    AND después de 24 horas, el token está expirado
    AND frontend rechaza token expirado
```

---

## Feature: Activar Cuenta por Link

```gherkin
Feature: Usuario activa su cuenta eligiendo contraseña

  Scenario: Activación exitosa con contraseña válida
    GIVEN usuario recibió email con token válido
    AND el usuario abre /auth/activate?token=VALID_JWT
    AND el usuario está en estado "PENDIENTE_INVITACION"
    AND el token no ha expirado
    WHEN el usuario digita password "Abc123!xyz" (8+ chars, mayús, número, special)
    AND confirma password
    AND clica "Activar Cuenta"
    THEN POST /activate {token, password} es llamado
    AND backend valida token signature, expiry, estado del usuario
    AND backend hashea password con BCrypt
    AND backend actualiza: estado=ACTIVO, password_hash=..., activated_at=NOW()
    AND backend consume token: activation_token = NULL
    AND invitation_audit registra "ACTIVATION_SUCCESS"
    AND frontend muestra pantalla éxito: "¡Cuenta Activada!"
    AND usuario puede loguear con email + password creado

  Scenario: Validación de requisitos de contraseña en real-time
    GIVEN el usuario está en formulario de activación
    WHEN el usuario digita en campo password:
      - "password" (8+ chars pero sin mayús, número, special)
      - "Abc" (tiene mayús pero < 8 chars)
      - "Abc123" (tiene todo pero sin special char)
      - "Abc123!" (válido: 8+ chars, mayús, número, special)
    THEN el UI muestra checklist dinámico:
      - ✓ Mínimo 8 caracteres ← ✓ cuando >= 8
      - ✓ Una mayúscula (A-Z) ← ✓ cuando tiene
      - ✓ Un número (0-9) ← ✓ cuando tiene
      - ✓ Carácter especial ← ✓ cuando tiene
    AND el botón "Activar" está deshabilitado hasta que TODOS los requisitos estén ✓
    AND cuando password === confirmPassword, botón se habilita

  Scenario: Rechazar contraseña que no cumple requisitos
    GIVEN el usuario intenta activar con password "weak"
    WHEN clica "Activar Cuenta"
    THEN POST /activate falla
    AND backend retorna error 400: "Contraseña no cumple requisitos: falta mayúscula, número, carácter especial"
    AND password NO se guarda
    AND usuario permanece en formulario
    AND puede reintentar

  Scenario: Rechazar token expirado
    GIVEN el usuario recibió email hace 25+ horas
    AND el token expiró (JWT exp < NOW)
    WHEN el usuario abre /auth/activate?token=EXPIRED_JWT
    THEN frontend detecta que token.exp < now()
    AND muestra error: "Este link expiró. Contacta al administrador para una nueva invitación."
    AND NO muestra formulario de password
    AND no hace POST a backend
    AND invitation_audit registra "ACTIVATION_FAILED" con razón "token_expired"

  Scenario: Rechazar token inválido o no encontrado
    GIVEN un token forjado o inexistente: "invalid.jwt.here"
    WHEN el usuario abre /auth/activate?token=INVALID
    THEN frontend intenta validar JWT
    AND falla signature check o formato inválido
    AND muestra error: "Link inválido. Contacta al administrador."
    AND no muestra formulario

  Scenario: Token consumido no es reutilizable
    GIVEN un usuario activó exitosamente con token T1
    AND activation_token fue seteado a NULL
    WHEN el mismo usuario intenta /activate?token=T1 de nuevo
    THEN backend valida token (JWT válido aún)
    BUT usuario.activation_token IS NULL
    AND rechaza: "Token ya fue consumido"
    AND NO actualiza password
    AND invitation_audit registra "ACTIVATION_FAILED" con razón "token_consumed"

  Scenario: Usuario no puede activar si ya está ACTIVO
    GIVEN un usuario en estado "ACTIVO"
    AND aún tiene un token viejo en activation_token
    WHEN intenta POST /activate {token, password}
    THEN backend rechaza: "Usuario ya está activado"
    AND no cambia password
    AND invitation_audit registra "ACTIVATION_REJECTED"

  Scenario: Pantalla de éxito muestra credenciales
    GIVEN usuario activó exitosamente
    THEN pantalla muestra:
      - ✓ Bienvenido [Nombre] [Apellido]
      - Email: usuario@control.ttcs (read-only)
      - Rol: OBSERVER (read-only)
      - Botón: [Ir a Login]
    AND después de 5 segundos, redirect automático a /login
    AND usuario puede clica [Ir a Login] antes de que pasen 5 segundos

  Scenario: Contraseñas no coinciden
    GIVEN usuario digita password "Abc123!xyz"
    AND confirmar password "Abc123!xy" (diferente)
    THEN el UI muestra error inline: "Las contraseñas no coinciden"
    AND botón "Activar" permanece deshabilitado
    AND cuando usuario corrige, error desaparece y botón se habilita
```

---

## Feature: Re-enviar Invitación

```gherkin
Feature: MASTER reenvia invitación si el usuario no activó

  Scenario: Re-enviar invitación a usuario pendiente
    GIVEN un usuario "juan@control.ttcs" en estado "PENDIENTE_INVITACION"
    AND fue invitado hace 20 horas
    AND el primer email de activación se perdió o usuario no lo vio
    WHEN el MASTER abre admin panel → Usuarios
    AND busca "juan@control.ttcs"
    AND clica botón "Re-enviar Invitación"
    THEN se genera un NUEVO token JWT con expiry NOW + 24h
    AND el nuevo token reemplaza al anterior en activation_token
    AND se envía nuevo email a juan@control.ttcs
    AND invitation_audit registra "INVITATION_RESENT" con intento #2
    AND el UI muestra notificación: "Invitación reenviada"

  Scenario: No se puede reenviar si usuario ya está ACTIVO
    GIVEN un usuario "juan@control.ttcs" en estado "ACTIVO"
    WHEN el MASTER intenta "Re-enviar Invitación"
    THEN el botón está disabled (no aparece opción)
    OR si intenta, falla: "Usuario ya está activo"

  Scenario: Botón "Re-enviar" solo aparece para PENDIENTE_INVITACION
    GIVEN admin panel lista usuarios
    THEN:
      - Usuario en PENDIENTE_INVITACION: botón "Re-enviar" visible
      - Usuario en ACTIVO: botón "Re-enviar" oculto/disabled
      - Usuario BLOQUEADO: botón oculto
```

---

## Feature: LoginUseCase valida estado del usuario

```gherkin
Feature: Usuario no puede loguear hasta activar

  Scenario: Rechazo de login: usuario aún PENDIENTE_INVITACION
    GIVEN un usuario "juan@control.ttcs" en estado "PENDIENTE_INVITACION"
    AND con email + password correcto guardado en BD
    WHEN intenta POST /login {email, password}
    AND email existe y password es correcto
    THEN el backend rechaza: 403 "Usuario aún no ha activado su cuenta. Revisa tu email."
    AND no se emite JWT
    AND usuario no puede acceder al sistema
    AND invitation_audit registra "LOGIN_REJECTED" con razón "usuario_pendiente_activacion"

  Scenario: Login exitoso después de activación
    GIVEN un usuario activó exitosamente (estado = ACTIVO)
    WHEN intenta POST /login {email, password}
    AND email existe y password es correcto
    THEN backend valida estado_usuario == ACTIVO ✓
    AND emite JWT válido
    AND usuario logueado ✓
```

---

## Feature: Email Service (Abstraction)

```gherkin
Feature: EmailService permite cambiar provider sin cambiar código

  Scenario: Envío con SMTP (desarrollo)
    GIVEN app.email.provider=smtp
    AND SMTP_HOST=smtp.gmail.com, SMTP_PORT=587, etc.
    WHEN se necesita enviar email
    THEN EmailService inyecta SmtpEmailProvider
    AND se envía via Quarkus Mailer (SMTP directo)

  Scenario: Envío con SendGrid (producción escalada)
    GIVEN app.email.provider=sendgrid
    AND SENDGRID_API_KEY=xxxxx
    WHEN se necesita enviar email
    THEN EmailService inyecta SendGridEmailProvider
    AND se envía via SendGrid API
    AND código de lógica de negocio no cambia

  Scenario: Fallar en envío (simulado en test)
    GIVEN test con mock EmailService que falla
    WHEN se intenta invitar usuario
    THEN retry logic se ejecuta
    AND auditoría registra intentos
    AND test verifica que retry ocurrió exactamente 3 veces
```

---

## Feature: Auditoría (invitation_audit)

```gherkin
Feature: Toda operación de invitación es auditada

  Scenario: Auditar envío de invitación
    GIVEN se envía invitación a usuario
    WHEN POST /invitaciones exitoso
    THEN invitation_audit tiene row:
      - usuario_id: 123
      - accion: "INVITATION_SENT"
      - evento: "SUCCESS"
      - datos: {email: "juan@...", token_exp: "2026-11-06T17:40:00Z"}
      - created_at: NOW()

  Scenario: Auditar fallo de envío (con reintento)
    GIVEN SMTP falla en intento 1, intento 2, éxito en 3
    THEN invitation_audit tiene 3 rows:
      - intento 1: {accion: "INVITATION_SEND_ATTEMPT", evento: "FAILED", datos: {error: "timeout"}}
      - intento 2: {accion: "INVITATION_SEND_ATTEMPT", evento: "FAILED", datos: {error: "timeout"}}
      - intento 3: {accion: "INVITATION_SENT", evento: "SUCCESS"}

  Scenario: Auditar activación exitosa
    GIVEN usuario activa su cuenta
    THEN invitation_audit tiene row:
      - usuario_id: 123
      - accion: "ACTIVATION_SUCCESS"
      - evento: "SUCCESS"
      - datos: {activated_at: "2026-11-05T18:00:00Z"}

  Scenario: Auditar intento de activación fallida
    GIVEN usuario intenta activar con password inválido
    THEN invitation_audit tiene row:
      - accion: "ACTIVATION_ATTEMPT"
      - evento: "FAILED"
      - datos: {error: "password_weak", requisitos_faltantes: ["special_char"]}

  Scenario: Admin puede ver auditoría por usuario
    GIVEN admin panel abierto
    WHEN admin hace clic en usuario "juan@..." → "Ver Auditoría"
    THEN ve timeline:
      - 2026-11-05 17:40: INVITATION_SENT
      - 2026-11-05 17:55: ACTIVATION_SUCCESS
      - credenciales: email, rol
```

---

## Feature: Integración — Flujo Completo

```gherkin
Feature: Flujo end-to-end: invitación → activación → login

  Scenario: Flujo feliz completo
    GIVEN un usuario nuevo "observer@control.ttcs" con estado "PENDIENTE_INVITACION"
    WHEN MASTER envía invitación
    THEN usuario recibe email con link
    AND link contiene token JWT válido (24h)
    
    WHEN usuario abre email y clica link
    THEN /auth/activate?token=JWT abre formulario
    AND frontend valida token (no expirado, usuario existe)
    
    WHEN usuario digita password "Observer@2025!"
    AND confirma password
    AND clica "Activar"
    THEN POST /activate llamado
    AND backend valida token, password, estado
    AND usuario actualizado: estado=ACTIVO, password_hash=..., activation_token=NULL
    AND pantalla éxito mostrada: "Bienvenido observer"
    AND redirect a /login después de 5s
    
    WHEN usuario abre /login
    AND digita observer@control.ttcs + Observer@2025!
    THEN LoginUseCase verifica estado == ACTIVO ✓
    AND emite JWT válido
    AND usuario logueado a dashboard ✓
    AND auditoría completa: INVITATION_SENT → ACTIVATION_SUCCESS → LOGIN_SUCCESS

  Scenario: Flujo con error de email → reintento manual
    GIVEN MASTER invita 100 usuarios
    AND usuario #50 nunca recibe email (SMTP falla, reintentos agotados)
    AND auditoría muestra: "100 intentados, 99 exitosos, 1 fallido: user50@control.ttcs"
    
    WHEN el MASTER ve error en UI: "1 email falló"
    AND clica "Reintenta fallidos"
    THEN se reenvía solo a user50@control.ttcs
    AND esta vez SMTP responde
    AND email llega ✓
    
    WHEN usuario #50 activa su cuenta
    THEN flujo normal continúa
```

---

## Non-Functional Requirements (Gherkin style)

```gherkin
Feature: Performance & Load

  Scenario: Invitar 500 usuarios < 10 segundos (API response)
    GIVEN 500 usuarios en estado PENDIENTE_INVITACION
    WHEN POST /invitaciones {usuarioIds: [1..500]}
    THEN respuesta retorna en < 10 segundos
    AND status code 202 (Accepted)
    AND backend procesa emails en background

  Scenario: Email queue: 100 emails/min sin saturar SMTP
    GIVEN cola con 500 emails
    WHEN EmailQueueConsumer procesa
    THEN throughput es 100 emails/minuto
    AND no hay throttling de SMTP
    AND sin spike de latencia backend

  Scenario: Token JWT validation < 100ms
    GIVEN token JWT válido
    WHEN frontend valida token (signature + expiry)
    THEN latencia < 100ms
    AND sin llamada a BD (stateless)

  Scenario: Activación < 2 segundos
    GIVEN POST /activate {token, password}
    AND todo válido
    WHEN backend procesa
    THEN response < 2 segundos
    AND DB update, hash, audit todo incluido
```

---

**Total Scenarios:** 40+ escenarios cubiertos  
**Coverage Areas:** Invitación, Activación, Re-envío, Email, Auditoría, Login, Performance

**Last Updated:** 2026-11-05
