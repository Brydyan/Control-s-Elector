# T-AUTH-INV-01: Tasks — Sistema de Invitaciones por Email

**SDD Reference:** 
- `proposal.md` (qué, por qué, fuera de alcance)
- `design.md` (decisiones D1-D10 con alternativas)
- `specs/invitaciones.spec.md` (40+ escenarios Gherkin)

**Priority:** Alta  
**Estimated Total:** 8-12 puntos  
**Sprints:** 3 (1 semana cada uno)  
**Status:** READY FOR IMPLEMENTATION

---

## SPRINT 1: Backend Infrastructure & Services (4-5 puntos)

**Objetivo:** Servicios base, schema DB, abstracción email. Al terminar: poder generar tokens y enviar emails.

### Task 1.1: Database Migration — Usuario Invitations Schema
**Effort:** 1 punto | **Dependencies:** None | **Type:** Schema

**Files:**
- `src/main/resources/db/migrations/028-usuario-invitaciones.sql` (NEW)

**What:**
```sql
ALTER TABLE usuario ADD COLUMN (
    activation_token VARCHAR(500) UNIQUE NULL,
    activated_at TIMESTAMPTZ NULL,
    invitation_sent_at TIMESTAMPTZ NULL,
    estado_usuario VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE_INVITACION'
);

CREATE TABLE invitation_audit (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    accion VARCHAR(64) NOT NULL,
    evento VARCHAR(32) NOT NULL,
    datos JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_usuario_estado_usuario ON usuario(estado_usuario);
CREATE INDEX idx_usuario_activation_token ON usuario(activation_token);
CREATE INDEX idx_invitation_audit_usuario_id ON invitation_audit(usuario_id);
```

**Mutación:**
- [ ] **C.1** Quitá `estado_usuario` column de la migración y ejecutá `./mvnw liquibase:update`. ¿Falla?
  Si **no falla**, tenés un problema: Task 2.4 (LoginUseCase) va a fallar. Anotá si falla y en dónde.

**Acceptance Criteria:**
- [ ] Migration ejecutada sin errores
- [ ] `psql -c "\d usuario"` muestra 4 nuevas columnas
- [ ] `invitation_audit` table existe con estructura correcta
- [ ] Indexes creados: `\d+ usuario` lista 3 indexes nuevos
- [ ] Rollback testeable: `./mvnw liquibase:rollback -Dliquibase.rollbackCount=1`

---

### Task 1.2: Email Service Abstraction Layer
**Effort:** 1.5 puntos | **Dependencies:** None | **Type:** Service

**Files:**
- `src/main/java/ec/control/electoral/auth/infrastructure/email/EmailService.java` (Interface, NEW)
- `src/main/java/ec/control/electoral/auth/infrastructure/email/SmtpEmailProvider.java` (NEW)
- `src/main/java/ec/control/electoral/auth/infrastructure/email/EmailTemplate.java` (Enum, NEW)

**What:**
```java
// EmailService.java — Interface
public interface EmailService {
    void sendInvitationEmail(String email, String userName, String fullName, String activationLink);
    void sendActivationConfirmation(String email, String userName);
}

// SmtpEmailProvider.java — Implementation
@ApplicationScoped
public class SmtpEmailProvider implements EmailService {
    @Inject Mailer mailer;
    @ConfigProperty(name = "app.email.from") String fromEmail;
    
    @Override
    public void sendInvitationEmail(String email, String userName, String fullName, String activationLink) {
        // Render template + send via Quarkus Mailer
    }
}

// EmailTemplate.java — Enum
public enum EmailTemplate {
    INVITATION("email/invitation.html"),
    CONFIRMATION("email/confirmation.html");
}
```

**Mutación:**
- [ ] **C.2** Quitá `implements EmailService` de `SmtpEmailProvider` y ejecutá `./mvnw clean compile`. ¿Falla?
  Debe fallar (Task 2.1 va a inyectar EmailService y si no existe, no compila). Anotá error.

**Acceptance Criteria:**
- [ ] Interfaz `EmailService` con 2 métodos
- [ ] `SmtpEmailProvider` implementa interfaz
- [ ] `@ApplicationScoped` en provider
- [ ] Inyección por interfaz: `@Inject EmailService emailService`
- [ ] `application.properties` tiene: `quarkus.mailer.*`, `app.email.from`
- [ ] No hay credenciales SMTP en código (solo env vars)
- [ ] Compile sin warnings

---

### Task 1.3: Invitation Token Service
**Effort:** 1 punto | **Dependencies:** None | **Type:** Service

**Files:**
- `src/main/java/ec/control/electoral/auth/application/service/InvitationTokenService.java` (NEW)

**What:**
```java
@ApplicationScoped
public class InvitationTokenService {
    @Inject JsonWebToken jwt; // Quarkus JWT
    @ConfigProperty(name = "mp.jwt.verify.issuer") String issuer;
    
    // generateToken(usuarioId) → JWT string, exp = NOW + 24h
    public String generateToken(Long usuarioId) {
        return new JwtClaimsBuilder()
            .subject(usuarioId.toString())
            .issuer(issuer)
            .issuedAt(Instant.now().getEpochSecond())
            .expiresAt(Instant.now().plus(24, ChronoUnit.HOURS).getEpochSecond())
            .build()
            .sign(key);
    }
    
    // validateToken(token) → usuarioId or throw
    public Long validateToken(String token) {
        // Verify signature + expiry, return sub
    }
    
    // isExpired(token) → boolean
    public boolean isExpired(String token) {
        // Compare exp claim vs NOW
    }
}
```

**Mutación:**
- [ ] **C.3** En `generateToken`, quitá `.expiresAt(...)` y ejecutá test `InvitationTokenServiceTest.tokenExpiredAfter24h()`. 
  ¿Cae el test? Anotá nombre del test. Si no cae, C.3 es decorativo y hay que rehacerlo.

**Unit Tests (ejecutar y anotar en apply-progress.md):**
- [ ] `testGenerateToken_SetsExpiry24h` — token exp = NOW + 86400s
- [ ] `testValidateToken_ValidToken_ReturnsUserId` — valid JWT → usuarioId
- [ ] `testValidateToken_ExpiredToken_ThrowsException` — expired JWT → exception
- [ ] `testValidateToken_InvalidSignature_ThrowsException` — forged JWT → exception

**Acceptance Criteria:**
- [ ] Token generado con estructura JWT válida
- [ ] Token signature verificable con secret compartido
- [ ] `sub` claim = usuarioId
- [ ] `exp` claim = NOW + 86400 segundos (24h)
- [ ] `validateToken()` parsea y verifica
- [ ] Rejecha token expirado
- [ ] Rejecha signature inválida
- [ ] Tests pasan

---

### Task 1.4: Password Validation Service
**Effort:** 0.5 puntos | **Dependencies:** None | **Type:** Service

**Files:**
- `src/main/java/ec/control/electoral/auth/application/service/PasswordValidationService.java` (NEW)
- `src/main/java/ec/control/electoral/auth/application/dto/PasswordValidationResult.java` (NEW)

**What:**
```java
@ApplicationScoped
public class PasswordValidationService {
    public PasswordValidationResult validate(String password) {
        // Requirements:
        // 1. Length >= 8
        // 2. At least 1 uppercase [A-Z]
        // 3. At least 1 digit [0-9]
        // 4. At least 1 special char [!@#$%^&*]
        
        return new PasswordValidationResult(isValid, missingRequirements);
    }
}

public class PasswordValidationResult {
    public boolean isValid;
    public List<String> missing; // ["uppercase", "special_char"]
}
```

**Mutación:**
- [ ] **C.4** Quitá regex `HAS_UPPERCASE` del validation y ejecutá test `testPassword_MissingUppercase_Invalidates()`.
  ¿Cae? Anotá test name. Si no, requirements no están siendo verificados.

**Unit Tests:**
- [ ] `testValid_ValidPassword_Returns true`
- [ ] `testInvalid_TooShort_ListsMissing` — "password" (7 chars) → missing length
- [ ] `testInvalid_NoUppercase_ListsMissing` — "abc123!" → missing uppercase
- [ ] `testInvalid_NoDigit_ListsMissing` — "Abc!xyz" → missing digit
- [ ] `testInvalid_NoSpecial_ListsMissing` — "Abc1xyz" → missing special

**Acceptance Criteria:**
- [ ] 4 requisitos implementados
- [ ] Retorna PasswordValidationResult con `isValid` + lista de missing
- [ ] Tests pasan
- [ ] No aceptá: "password", "weak", "ABC", "123456789"
- [ ] Acepta: "Abc123!x", "Observer@2025", "Pass$word1"

---

### Task 1.5: Invitation Audit Service & Infra
**Effort:** 1 punto | **Dependencies:** Task 1.1 (schema) | **Type:** Service

**Files:**
- `src/main/java/ec/control/electoral/auth/application/service/InvitationAuditService.java` (NEW)

**What:**
```java
@Transactional
@ApplicationScoped
public class InvitationAuditService {
    @Inject InvitationAuditRepository repository;
    
    public void log(Long usuarioId, String accion, String evento, Map<String, Object> datos) {
        InvitationAudit audit = new InvitationAudit(usuarioId, accion, evento, datos);
        repository.persist(audit);
    }
    
    // Convenience methods:
    public void logInvitationSent(Long usuarioId, String email) { ... }
    public void logActivationSuccess(Long usuarioId) { ... }
    public void logActivationFailed(Long usuarioId, String reason) { ... }
    public void logInvitationFailed(Long usuarioId, String email, String error) { ... }
}
```

**Events to support:**
- INVITATION_SENT, INVITATION_FAILED, INVITATION_RESENT
- ACTIVATION_SUCCESS, ACTIVATION_FAILED, ACTIVATION_ATTEMPTED
- LOGIN_REJECTED_PENDING_ACTIVATION

**Mutación:**
- [ ] **C.5** Quitá `@Transactional` de `InvitationAuditService` y ejecutá test `testAuditLogged_AfterInvitationSent()`.
  ¿Falla? (Debería fallar: sin transacción, cambios no persisten). Anotá error.

**Acceptance Criteria:**
- [ ] INSERT a invitation_audit para cada evento
- [ ] Datos incluyen timestamp, usuarioId, razón
- [ ] Transaccional: si falla, no se persiste
- [ ] Query: `SELECT * FROM invitation_audit WHERE usuario_id=X ORDER BY created_at DESC`
- [ ] No tiene PII expuesta (email en `datos` JSONB si necesario)

---

## End of SPRINT 1 — Gate Checks

**Qué NO hacer en Sprint 1:**
- ❌ Escribir REST endpoints aún (Task 2.3)
- ❌ Implementar retry logic en email queue (Task 2.1, async)
- ❌ Escribir componente Angular (Task 3.1)
- ❌ Tests de integración (Task 4.2)

**Compuertas antes de dar por terminada Sprint 1:**

```bash
# 1. Schema migrado
psql -c "SELECT column_name FROM information_schema.columns WHERE table_name='usuario'" \
  | grep -E "activation_token|estado_usuario|activated_at"
# Esperado: 3 filas (3 nuevas columnas)

# 2. invitation_audit existe
psql -c "\dt invitation_audit"
# Esperado: "invitation_audit" table listed

# 3. Services compiladas
./mvnw clean compile
# Esperado: BUILD SUCCESS, no compilation errors

# 4. Tests unitarios pasan
./mvnw test -Dtest=InvitationTokenServiceTest
./mvnw test -Dtest=PasswordValidationServiceTest
./mvnw test -Dtest=InvitationAuditServiceTest
# Esperado: Tests PASSED

# 5. Verificar interfaces inyectables
grep -r "@Inject EmailService" src/
# Esperado: (vacío, aún no hay consumidores, pero interfaz existe)

# Línea base números:
# - Archivos nuevos: 5 (schema + 4 services)
# - Lines of code (services): ~250-300 LOC
# - Test cases: 12+ casos cubiertos
```

Si compuertas fallan: arreglá antes de avanzar a Sprint 2.

---

## SPRINT 2: Backend Use Cases & REST Endpoints (3-4 puntos)

**Objetivo:** Use cases que coordinan servicios, REST API, validación. Al terminar: invitación y activación funcionan end-to-end.

### Task 2.1: EnviarInvitacionUseCase
**Effort:** 2 puntos | **Dependencies:** Task 1.2-1.5 | **Type:** Use case

**Files:**
- `src/main/java/ec/control/electoral/auth/application/usecase/EnviarInvitacionUseCase.java` (NEW)
- `src/main/java/ec/control/electoral/auth/application/port/InvitationEmailQueue.java` (Port, NEW)
- `src/main/java/ec/control/electoral/auth/infrastructure/messaging/InvitationEmailQueueImpl.java` (Adapter, NEW)

**What:**
```java
@Transactional
@ApplicationScoped
public class EnviarInvitacionUseCase {
    @Inject UsuarioRepository usuarioRepository;
    @Inject InvitationTokenService tokenService;
    @Inject InvitationEmailQueue emailQueue;
    @Inject InvitationAuditService auditService;
    
    public EnviarInvitacionResponse execute(List<Long> usuarioIds) {
        // 1. Validate: all users in PENDIENTE_INVITACION state
        // 2. For each user: generate token, save to DB, enqueue email
        // 3. Return: {sentCount, failures: [{userId, reason}]}
        
        // Pseudo:
        List<InvitacionFailure> failures = new ArrayList<>();
        for (Long userId : usuarioIds) {
            try {
                Usuario usuario = usuarioRepository.findById(userId);
                if (!usuario.getEstadoUsuario().equals("PENDIENTE_INVITACION")) {
                    failures.add(new InvitacionFailure(userId, "Already active"));
                    continue;
                }
                
                String token = tokenService.generateToken(userId);
                usuario.setActivationToken(token);
                usuario.setInvitationSentAt(Instant.now());
                usuarioRepository.persist(usuario);
                
                // Publish to async queue (with rate limiting, retry)
                emailQueue.enqueue(new SendInvitationEvent(usuario.getEmail(), token, usuario.getNombre()));
                auditService.logInvitationSent(userId, usuario.getEmail());
            } catch (Exception e) {
                failures.add(new InvitacionFailure(userId, e.getMessage()));
                auditService.logInvitationFailed(userId, usuario.getEmail(), e.getMessage());
            }
        }
        
        return new EnviarInvitacionResponse(usuarioIds.size() - failures.size(), failures);
    }
}
```

**Email Queue:**
- Implementar con Redis queue o similar (no SMTP directo)
- Rate limit: 100 emails/min
- Retry: 3x exponential backoff (1s, 2s, 4s)
- Consumer must run async (background service)

**Mutación:**
- [ ] **C.6** Quitá la línea `usuario.setActivationToken(token)` de `execute()` y ejecutá test `testInvitationSent_TokenSavedToDB()`.
  ¿Cae? Anotá nombre del test. Si no, el token no está siendo guardado.

- [ ] **C.7** En EmailQueue, eliminá rate limiting (quitá `if (count >= 100) sleep()`). Ejecutá load test `k6 run scenarios/invite-100.js`.
  ¿Aumenta latencia de email? Debería. Documentá: "Sin rate limit, SMTP latency subió XX%".

**Unit Tests:**
- [ ] `testSingleUserInvite_GeneratesToken`
- [ ] `testBulkInvite_100Users_AllProcessed`
- [ ] `testRejectAlreadyActive_SkipsUser`
- [ ] `testEmailEnqueued_NotSentSynchronously`
- [ ] `testAuditLogged_PerUser`

**Integration Tests:**
- [ ] `testEndToEnd_Invite50_CheckDatabase` — verifica 50 filas con activation_token, invitation_sent_at
- [ ] `testRetryLogic_EmailFails_Requeued` — simula fallo SMTP, verifica retry ocurre

**Acceptance Criteria:**
- [ ] Valida estado usuario antes de invitar
- [ ] Genera token único por usuario
- [ ] Guarda token en DB (activation_token)
- [ ] Enqeuea email async (no bloquea)
- [ ] Rate limiting: 100 emails/min
- [ ] Retry: 3x exponential backoff
- [ ] Auditoría: cada intento logged
- [ ] Retorna {successCount, failures}
- [ ] Tests pasan, coverage > 80%

---

### Task 2.2: ActivarUsuarioPorTokenUseCase
**Effort:** 1.5 puntos | **Dependencies:** Task 1.2-1.4 | **Type:** Use case

**Files:**
- `src/main/java/ec/control/electoral/auth/application/usecase/ActivarUsuarioPorTokenUseCase.java` (NEW)

**What:**
```java
@Transactional
@ApplicationScoped
public class ActivarUsuarioPorTokenUseCase {
    @Inject UsuarioRepository usuarioRepository;
    @Inject InvitationTokenService tokenService;
    @Inject PasswordValidationService passwordValidator;
    @Inject InvitationAuditService auditService;
    @Inject PasswordEncoder passwordEncoder; // BCrypt
    
    public ActivarUsuarioResponse execute(String token, String password) {
        // 1. Validate token (signature, expiry)
        Long usuarioId = tokenService.validateToken(token);
        
        Usuario usuario = usuarioRepository.findById(usuarioId);
        
        // 2. Check estado
        if (!usuario.getEstadoUsuario().equals("PENDIENTE_INVITACION")) {
            auditService.logActivationFailed(usuarioId, "already_activated");
            throw new BusinessException("Usuario ya está activado");
        }
        
        // 3. Check token NOT consumed
        if (usuario.getActivationToken() == null) {
            auditService.logActivationFailed(usuarioId, "token_consumed");
            throw new BusinessException("Token ya fue consumido");
        }
        
        // 4. Validate password
        PasswordValidationResult validation = passwordValidator.validate(password);
        if (!validation.isValid()) {
            auditService.logActivationFailed(usuarioId, "password_weak: " + validation.getMissing());
            throw new BusinessException("Contraseña no cumple requisitos");
        }
        
        // 5. Update usuario
        usuario.setPassword(passwordEncoder.encode(password));
        usuario.setEstadoUsuario("ACTIVO");
        usuario.setActivatedAt(Instant.now());
        usuario.setActivationToken(null); // Consume token
        usuarioRepository.persist(usuario);
        
        // 6. Audit
        auditService.logActivationSuccess(usuarioId);
        
        return new ActivarUsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail());
    }
}
```

**Mutación:**
- [ ] **C.8** Quitá línea `usuario.setActivationToken(null)` de `execute()` y ejecutá test `testActivationConsumed_CantReuseToken()`.
  ¿Cae? Anotá test name. Si no, token reutilización no está siendo prevenida.

**Unit Tests:**
- [ ] `testValidToken_ValidPassword_Activates`
- [ ] `testExpiredToken_Rejects`
- [ ] `testWeakPassword_Rejects_WithMissing`
- [ ] `testAlreadyActive_Rejects`
- [ ] `testTokenConsumed_Rejects`

**Integration Tests:**
- [ ] `testActivation_UserCanLoginAfter` — full flow: token → activate → login

**Acceptance Criteria:**
- [ ] Valida JWT token (signature, expiry)
- [ ] Rechaza si usuario ya ACTIVO
- [ ] Rechaza si token ya consumido
- [ ] Valida password (8+, mayús, número, especial)
- [ ] Hashea password con BCrypt
- [ ] Actualiza: estado=ACTIVO, password_hash, activated_at, activation_token=NULL
- [ ] Auditoría logged
- [ ] Tests pasan

---

### Task 2.3: REST Endpoints
**Effort:** 1 punto | **Dependencies:** Task 2.1-2.2 | **Type:** REST

**Files:**
- `src/main/java/ec/control/electoral/auth/api/rest/InvitacionResource.java` (NEW)
- `src/main/java/ec/control/electoral/auth/api/rest/ActivacionResource.java` (NEW)

**What:**
```java
@Path("/api/v1/auth/invitaciones")
@ApplicationScoped
public class InvitacionResource {
    @Inject EnviarInvitacionUseCase useCase;
    
    @POST
    @RolesAllowed("ADMIN")
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response sendInvitaciones(SendInvitacionesRequest req) {
        // req = {usuarioIds: [1, 2, 3, ...]}
        EnviarInvitacionResponse response = useCase.execute(req.getUsuarioIds());
        return Response.accepted(response).build();
    }
    
    @GET
    @Path("/validate-token/{token}")
    @Produces(MediaType.APPLICATION_JSON)
    public Response validateToken(@PathParam("token") String token) {
        // Public endpoint: validate JWT structure + expiry
        // Return: {valid: true/false, usuarioId: X, expiresAt: timestamp}
        // Does NOT validate if user exists (avoid user enumeration)
    }
}

@Path("/api/v1/auth/activate")
@ApplicationScoped
public class ActivacionResource {
    @Inject ActivarUsuarioPorTokenUseCase useCase;
    
    @POST
    @Consumes(MediaType.APPLICATION_JSON)
    @Produces(MediaType.APPLICATION_JSON)
    public Response activateUser(ActivateUserRequest req) {
        // req = {token: JWT, password: "...", passwordConfirm: "..."}
        // Frontend validates password === passwordConfirm
        // Backend validates again
        
        ActivarUsuarioResponse response = useCase.execute(req.getToken(), req.getPassword());
        return Response.ok(response).build();
    }
}
```

**Rate Limiting:**
- Endpoint `/activate`: max 10 attempts / min / IP (prevent brute-force)
- Endpoint `/invitaciones`: role-restricted (ADMIN)

**Mutación:**
- [ ] **C.9** Quitá `@RolesAllowed("ADMIN")` de InvitacionResource y ejecutá test `testInvitaciones_NonAdmin_Forbidden()`.
  ¿Falla? (Debería fallar: non-admin ahora puede invitar, test expects 403). Anotá error.

**Unit Tests:**
- [ ] `testInvitaciones_AdminOnly_NonAdminGets403`
- [ ] `testInvitaciones_ValidRequest_Returns202Accepted`
- [ ] `testValidateToken_ValidToken_ReturnsInfo`
- [ ] `testValidateToken_ExpiredToken_InvalidFalse`
- [ ] `testActivate_ValidRequest_Returns200`
- [ ] `testActivate_WeakPassword_Returns400`

**Integration Tests:**
- [ ] `testActivateRateLimiting_10AttemptsPerMin` — 11th attempt fails

**Acceptance Criteria:**
- [ ] POST /invitaciones: ADMIN only, returns 202
- [ ] GET /validate-token: public, returns JWT info
- [ ] POST /activate: public, returns 200 on success
- [ ] Error responses have clear messages
- [ ] Rate limiting implemented + tested
- [ ] HTTP status codes correct (202, 200, 400, 401, 403, 409)
- [ ] Tests pasan

---

### Task 2.4: Update LoginUseCase — Check User State
**Effort:** 0.5 puntos | **Dependencies:** Task 1.1, 2.3 | **Type:** Enhancement

**Files:**
- `src/main/java/ec/control/electoral/.../auth/application/usecase/LoginUseCase.java` (MODIFY)

**What:**
```java
// In LoginUseCase.execute():
Usuario usuario = usuarioRepository.findByEmail(email);
if (usuario == null || !passwordEncoder.matches(password, usuario.getPassword())) {
    throw new UnauthorizedException("Email o contraseña inválidos");
}

// NEW: Check estado_usuario
if (!usuario.getEstadoUsuario().equals("ACTIVO")) {
    auditService.log(usuario.getId(), "LOGIN_REJECTED_PENDING_ACTIVATION");
    throw new ForbiddenException("Usuario aún no ha activado su cuenta. Revisa tu email.");
}

// Continue: generate JWT
```

**Mutación:**
- [ ] **C.10** Quitá la línea de verificación `estado_usuario` y ejecutá test `testLogin_PendingInvitation_Fails()`.
  ¿Cae? Anotá test name. Si no, PENDIENTE_INVITACION users están logueando (bug!).

**Unit Tests:**
- [ ] `testLogin_PendingInvitation_Rejected` — user in PENDIENTE_INVITACION cannot login
- [ ] `testLogin_Active_Succeeds` — user in ACTIVO can login

**Acceptance Criteria:**
- [ ] PENDIENTE_INVITACION users cannot login
- [ ] Error message clear: "Usuario aún no ha activado"
- [ ] Auditoría logged
- [ ] Tests pasan

---

## End of SPRINT 2 — Gate Checks

**Qué NO hacer en Sprint 2:**
- ❌ Frontend component (Task 3.1)
- ❌ Email templates (Task 3.2)
- ❌ Integration tests full (Task 4.2)
- ❌ Load testing (k6)

**Compuertas antes de dar por terminada Sprint 2:**

```bash
# 1. Use cases compiladas
./mvnw clean compile
# Esperado: BUILD SUCCESS

# 2. Tests unitarios pasan
./mvnw test -Dtest=*InvitacionUseCase
./mvnw test -Dtest=*ActivacionUseCase
./mvnw test -Dtest=*LoginUseCase
# Esperado: Tests PASSED

# 3. REST endpoints compiladas
grep -r "@Path" src/main/java/ec/control/electoral/auth/api/rest/
# Esperado: 2 resources (Invitacion, Activacion)

# 4. Verificar inyecciones
./mvnw dependency:tree | grep -i jwt
# Esperado: JWT library present

# 5. Database estado
psql -c "SELECT COUNT(*) FROM usuario WHERE estado_usuario='PENDIENTE_INVITACION';"
# Esperado: (depends on seed, but column exists)

# Línea base números:
# - Archivos nuevos: 6 (2 use cases + 2 resources + ports/adapters)
# - Lines of code: ~400-500 LOC
# - Test cases: 20+ casos cubiertos
# - Endpoints: 3 (/invitaciones, /validate-token, /activate)
```

Si compuertas fallan: arreglá antes de avanzar a Sprint 3.

---

## SPRINT 3: Frontend & Email Templates (2-3 puntos)

**Objetivo:** UI para activar cuenta, templates email, admin panel. Al terminar: usuario puede clickear link y activar.

### Task 3.1: ActivateUserComponent
**Effort:** 1.5 puntos | **Dependencies:** Task 2.3 | **Type:** Frontend

**Files:**
- `src/app/modules/auth/components/activate-user/activate-user.component.ts` (NEW)
- `src/app/modules/auth/components/activate-user/activate-user.component.html` (NEW)
- `src/app/modules/auth/components/activate-user/activate-user.component.scss` (NEW)

**What:**
- Route: `/auth/activate?token=JWT_HERE`
- Step 1: Read token from query param
- Step 2: Call GET /validate-token/{token} → check if valid
- Step 3: If invalid/expired, show error + contact admin link
- Step 4: If valid, show password form with real-time validation
- Step 5: Password requirements checklist (✓ 8+ chars, ✓ uppercase, ✓ number, ✓ special)
- Step 6: On submit: POST /activate {token, password}
- Step 7: On success: show confirmation screen + countdown to redirect
- Step 8: Redirect to /login

**Mutación:**
- [ ] **C.11** Quitá validación de `password !== passwordConfirm` en el component y ejecutá test `testForm_DifferentPasswords_SubmitDisabled()`.
  ¿Falla? Anotá test name. Si no, form no está validando que passwords coincidan.

**Unit Tests (Angular):**
- [ ] `testTokenValidation_Valid_ShowsForm`
- [ ] `testTokenValidation_Expired_ShowsError`
- [ ] `testPasswordRequirements_DynamicChecklist` — cada requisito se marca ✓/✗ en real-time
- [ ] `testPasswordsMatch_Validation`
- [ ] `testFormSubmission_CallsAPI`
- [ ] `testSuccessScreen_CountdownTo5s`

**Acceptance Criteria:**
- [ ] Component loads from /auth/activate?token=...
- [ ] Validates token on init (GET /validate-token)
- [ ] Shows error if invalid/expired
- [ ] Password form reactive + validators
- [ ] Requirements checklist updates in real-time
- [ ] Submit button disabled until ALL requirements + passwords match
- [ ] POST /activate on form submit
- [ ] Shows success screen with countdown
- [ ] Redirects to /login after 5s
- [ ] Error handling: shows message, allows retry
- [ ] Tests pasan

---

### Task 3.2: Email Templates
**Effort:** 0.5 puntos | **Dependencies:** Task 1.2 | **Type:** HTML templates

**Files:**
- `src/main/resources/email/invitation.html` (NEW)
- `src/main/resources/email/confirmation.html` (NEW)
- `src/main/resources/email/resend.html` (NEW)

**What:**
```html
<!-- invitation.html -->
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    body { font-family: Arial, sans-serif; background: #f5f5f5; }
    .container { max-width: 600px; margin: 20px auto; background: white; padding: 20px; border-radius: 8px; }
    .button { background: #4CAF50; color: white; padding: 12px 24px; text-decoration: none; border-radius: 4px; }
    .footer { border-top: 1px solid #ddd; margin-top: 20px; font-size: 12px; color: #666; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Bienvenido a Control Electoral</h1>
    <p>Hola {{ userName }},</p>
    <p>Tu cuenta ha sido creada como <strong>{{ userRole }}</strong>.</p>
    <p>Haz clic en el botón abajo para activar tu cuenta:</p>
    <p style="text-align: center;">
      <a href="{{ activationLink }}" class="button">Activar Cuenta</a>
    </p>
    <p><small>Este link expira en 24 horas. Si tienes problemas, contacta al administrador.</small></p>
    <div class="footer">
      <p>© 2026 Control Electoral. Todos los derechos reservados.</p>
    </div>
  </div>
</body>
</html>
```

**Mutación:**
- [ ] **C.12** Quitá texto "expira en 24 horas" del template y ejecutá test `testEmailTemplate_Contains24hWarning()`.
  ¿Falla? Anotá test name. Si no, template no está siendo validado.

**Acceptance Criteria:**
- [ ] 3 templates: invitation, confirmation, resend
- [ ] Variables templated: {{ userName }}, {{ activationLink }}, etc.
- [ ] Mobile-responsive (media queries)
- [ ] Invitation: includes link + expiry warning
- [ ] Confirmation: success message + login link
- [ ] Resend: similar to invitation
- [ ] Tests: template rendering, variable substitution

---

### Task 3.3: Update Admin Panel — Bulk Invitation Actions
**Effort:** 1 punto | **Dependencies:** Task 2.3 | **Type:** Frontend

**Files:**
- `src/app/modules/admin/components/usuarios-list/usuarios-list.component.ts` (MODIFY)
- `src/app/modules/admin/components/usuarios-list/usuarios-list.component.html` (MODIFY)

**What:**
- Add checkbox column to usuario list
- Add bulk actions toolbar: appears when users selected
- Actions: "Enviar Invitación", "Re-enviar Invitación"
- Call POST /invitaciones with selected usuarioIds
- Show progress spinner during sending
- Show success/failure toast notifications
- Handle per-user failures (list which emails failed)

**Mutación:**
- [ ] **C.13** Quitá `@RolesAllowed("ADMIN")` check en frontend (no del backend!) y vuelve a compilar.
  ¿El botón aparece? (Debería aparecer igual; la validación real es en backend). Si no aparece, hay validación frontend incorrecta.

**Acceptance Criteria:**
- [ ] Multi-select checkboxes on list
- [ ] Bulk toolbar appears when users selected
- [ ] POST /invitaciones called with selected IDs
- [ ] Progress spinner shown during processing
- [ ] Success toast: "X invitaciones enviadas, Y errores"
- [ ] Failed emails listed for retry
- [ ] Table refreshes after success
- [ ] "Re-enviar" action available for PENDIENTE_INVITACION users

---

## End of SPRINT 3 — Gate Checks

**Qué NO hacer en Sprint 3:**
- ❌ Tests de carga (k6) — lo hace Sprint 4
- ❌ Integración full con BD (ya hecha en Sprint 2)
- ❌ SMTP real (mock en dev)

**Compuertas antes de dar por terminada Sprint 3:**

```bash
# 1. Frontend compila
cd frontend
npm run build
# Esperado: BUILD SUCCESS

# 2. Tests Angular pasan
npm run test:ci -- --watch=false
# Esperado: Tests PASSED

# 3. Verificar templates
ls -la src/main/resources/email/*.html
# Esperado: 3 archivos (invitation, confirmation, resend)

# 4. Rutas configuradas
grep -r "/auth/activate" src/app/
# Esperado: ruta encontrada en routing module

# Línea base números:
# - Componentes nuevos: 1 (ActivateUserComponent)
# - Modificaciones: 1 (usuarios-list)
# - Templates email: 3
# - Test cases (Angular): 15+ casos
```

---

## SPRINT 4: Integration Testing & Load Testing (1-2 puntos)

**Objetivo:** SMTP configurado, tests de integración pasan, k6 valida performance.

### Task 4.1: SMTP Configuration & Email Queue Consumer
**Effort:** 0.5 puntos | **Dependencies:** Task 1.2, 2.1 | **Type:** Infrastructure

**Files:**
- `src/main/resources/application.properties` (MODIFY)
- `src/main/java/ec/control/electoral/auth/infrastructure/messaging/EmailQueueConsumer.java` (NEW)

**What:**
```properties
# application.properties
quarkus.mailer.host=smtp.gmail.com
quarkus.mailer.port=587
quarkus.mailer.username=${SMTP_USERNAME}
quarkus.mailer.password=${SMTP_PASSWORD}
quarkus.mailer.ssl=true

app.email.from=noreply@control.electoral.ec
app.email.retry.max-attempts=3
app.email.retry.initial-delay-ms=1000
app.email.retry.backoff-multiplier=2

app.email.queue.batch-size=10
app.email.queue.poll-interval-ms=5000
app.email.queue.rate-limit=100
app.email.queue.rate-limit-window-seconds=60
```

**EmailQueueConsumer:**
- Polls queue every 5s
- Processes batch of 10 emails
- Rate limit: 100/min
- Retry: 3x exponential backoff (1s, 2s, 4s)
- Audits each attempt

**Mutación:**
- [ ] **C.14** Quitá `app.email.retry.backoff-multiplier=2` y ejecutá consumer. ¿Aumenta latencia en reintentos?
  (Debería: sin backoff, reintentos son inmediatos). Anotá efecto.

**Acceptance Criteria:**
- [ ] Configuración externalizada a env vars (no en código)
- [ ] No hay credentials en código
- [ ] Consumer procesa queue async
- [ ] Retry: 3x con exponential backoff
- [ ] Rate limit: 100 emails/min
- [ ] Auditoría: cada intento logged

---

### Task 4.2: Integration Tests
**Effort:** 1 punto | **Dependencies:** All Sprint 2-3 tasks | **Type:** Tests

**Files:**
- `src/test/java/ec/control/electoral/auth/InvitationIntegrationTest.java` (NEW)
- `src/test/java/ec/control/electoral/auth/ActivationIntegrationTest.java` (NEW)

**Scenario Tests:**

**InvitationIntegrationTest:**
- [ ] Send invitation to single user
- [ ] Send invitation to bulk users (50)
- [ ] Fail gracefully: user not PENDIENTE_INVITACION
- [ ] Fail gracefully: email send fails, retry, then log
- [ ] Email queue processes messages correctly
- [ ] Rate limiting: > 100/min rejected (or queued, not instant)

**ActivationIntegrationTest:**
- [ ] Activate with valid token + password
- [ ] Reject expired token
- [ ] Reject invalid password
- [ ] Reject already activated user
- [ ] Token consumed after use (cannot reuse)
- [ ] User state changes PENDIENTE_INVITACION → ACTIVO
- [ ] User can login after activation
- [ ] Failed activation audited

**Mutación:**
- [ ] **C.15** En test `testActivationTokenConsumed_CantReuse()`, intenta /activate 2x con mismo token.
  ¿Falla segundo intento? Anotá error code + mensaje. Si no falla, token no está siendo consumido.

**Acceptance Criteria:**
- [ ] All scenarios tested end-to-end (from REST call to DB state)
- [ ] Database verified after each test
- [ ] Audit table verified
- [ ] Mock email provider works
- [ ] Tests isolated (no test order dependencies)
- [ ] Coverage > 80% (use JaCoCo)

---

### Task 4.3: Frontend Component Tests
**Effort:** 0.5 puntos | **Dependencies:** Task 3.1 | **Type:** Tests

**Scenario Tests:**

**ActivateUserComponentTest:**
- [ ] Component loads from route with token param
- [ ] Calls GET /validate-token on init
- [ ] Shows form if token valid
- [ ] Shows error if token expired
- [ ] Password validation: requirements checklist updates real-time
- [ ] Submit button disabled until ALL requirements met
- [ ] POST /activate called on submit
- [ ] Success screen shown after successful activation
- [ ] Countdown timer works (5s → redirect)
- [ ] Error handling: retry possible

**Mutación:**
- [ ] **C.16** Quitá validador `passwordConfirm === password` del form y ejecutá test `testPasswordMismatch_SubmitDisabled()`.
  ¿Falla? Anotá test name. Si no, form validation no es completo.

**Acceptance Criteria:**
- [ ] Coverage > 80%
- [ ] All scenarios tested
- [ ] Mocks for HTTP calls (HttpClientTestingModule)
- [ ] Tests isolated

---

### Task 4.4: k6 Load Testing
**Effort:** 0.5 puntos | **Dependencies:** All tasks + k6 installed | **Type:** Load test

**Files:**
- `frontend/tests/load/scenarios/invitations.js` (NEW or extend existing)

**What:**
```javascript
// Simulate: 100 VUs inviting users + 100 VUs activating
// Baseline: invite 10 users, activate 10 users
// Pico: 100 VUs concurrent invites + activates

export let options = {
  vus: 100,
  duration: '5m',
  thresholds: {
    'http_req_duration{endpoint:invite}': ['p95 < 5s'],
    'http_req_duration{endpoint:activate}': ['p95 < 2s'],
    'http_req_failed': ['rate < 0.1'],
  },
};

export default function() {
  // User 1: POST /invitaciones {usuarioIds: [...]}
  // User 2-100: GET /validate-token + POST /activate
}
```

**Load Test Success Criteria:**
- [ ] Invite endpoint p95 < 5s
- [ ] Activate endpoint p95 < 2s
- [ ] Failure rate < 10%
- [ ] Email queue: 100 emails/min without bottleneck
- [ ] DB pool: < 80% utilization

**Acceptance Criteria:**
- [ ] k6 scenario executes without errors
- [ ] Metrics collected and analyzed
- [ ] Performance acceptable

---

## End of SPRINT 4 — Final Gate Checks

**Compuertas antes de dar por terminada Sprint 4 (GO LIVE CHECKLIST):**

```bash
# 1. Build success
./mvnw clean build -DskipTests
npm run build
# Esperado: BUILD SUCCESS

# 2. All tests pass
./mvnw clean verify
npm run test:ci
# Esperado: All tests PASSED, coverage > 80%

# 3. Load test success
cd frontend/tests/load
./run.sh baseline
./run.sh pico
# Esperado: p95 < 5s, p99 < 8s, failures < 10%

# 4. Database schema
psql -c "SELECT column_name FROM information_schema.columns WHERE table_name='usuario';"
# Esperado: activation_token, activated_at, invitation_sent_at, estado_usuario

# 5. Email templates exist
ls -la src/main/resources/email/
# Esperado: 3 HTML files

# 6. REST endpoints active (check Swagger)
curl http://localhost:8080/q/openapi
| grep -i "invitacion\|activate"
# Esperado: endpoints listed

# Línea base números:
# - Total files: 20+ (backend + frontend + tests)
# - Total LOC: 1500+ LOC
# - Test cases: 50+ escenarios cubiertos
# - Test coverage: > 80%
# - Performance: p95 < 5s, p99 < 8s
```

---

## Qué NO hacer en TODO el proyecto

- ❌ Enviar emails reales en CI/CD (mock only)
- ❌ Hardcodear credenciales (env vars siempre)
- ❌ Tests sin mutaciones (cada garantía tiene mutación)
- ❌ Agregar features fuera del scope (solo lo en proposal.md)

---

## Risk Register

| Risk | Prob | Impact | Mitigation |
|------|------|--------|-----------|
| Email delivery fails | Medium | High | Mock, retry logic, audit trail |
| Token collision | Very Low | Critical | UUID v4, unique constraint |
| Rate limit too strict | Medium | Medium | Monitor load tests, adjust |
| Password validation confusing | Medium | Low | Clear checklist UI + docs |
| DB migration rollback issue | Low | High | Test rollback in staging first |
| Race condition: 2 activations simultaneous | Medium | Medium | activation_token = NULL prevents both |

---

## Próximos pasos

1. **Confirmar inicio Sprint 1** con equipo
2. **Task 1.1 first:** DB migration — critical path bloqueador
3. **Daily standups:** Mutaciones + gate checks no son opcionales
4. **Escalate early** si gate checks fallan

---

**Total Effort:** 8-12 puntos  
**Total Sprints:** 3 (1 semana c/u)  
**Start Date:** 2026-11-06  
**Target Completion:** 2026-11-27  
**Launch Window:** 2026-11-28+

**Last Updated:** 2026-11-05
