# T-AUTH-INV-01: Tasks — Sistema de Invitaciones por Email

**SDD Reference:** `openspec/changes/auth/2026-11-05-t-auth-inv-01-invitaciones/design.md`  
**Priority:** Alta  
**Estimated Total:** 8-12 puntos → 3 sprints  
**Status:** READY FOR IMPLEMENTATION

---

## SPRINT 1: Backend Infrastructure & Services (4-5 puntos)

### Task 1.1: Database Migration — Usuario Invitations Schema
**Effort:** 1 punto | **Dependencies:** None | **Type:** Schema change

**Files to create/modify:**
- `src/main/resources/db/migrations/028-usuario-invitaciones.sql` (NEW)

**Work:**
```sql
-- Add columns to usuario table
ALTER TABLE usuario ADD COLUMN (
    activation_token VARCHAR(500) UNIQUE NULL,
    activated_at TIMESTAMPTZ NULL,
    invitation_sent_at TIMESTAMPTZ NULL,
    estado_usuario VARCHAR(32) NOT NULL DEFAULT 'PENDIENTE_INVITACION'
);

-- Create invitation_audit table
CREATE TABLE IF NOT EXISTS invitation_audit (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    accion VARCHAR(64) NOT NULL,
    evento VARCHAR(32) NOT NULL,
    datos JSONB,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Create indexes
CREATE INDEX idx_usuario_estado_usuario ON usuario(estado_usuario);
CREATE INDEX idx_usuario_activation_token ON usuario(activation_token);
CREATE INDEX idx_invitation_audit_usuario_id ON invitation_audit(usuario_id);
```

**Acceptance Criteria:**
- [ ] Migration runs without errors
- [ ] Columns exist in usuario table
- [ ] invitation_audit table exists with correct schema
- [ ] Indexes created for performance
- [ ] Rollback script prepared (no data loss)

---

### Task 1.2: Email Service Abstraction Layer
**Effort:** 1.5 puntos | **Dependencies:** None | **Type:** Service layer

**Files to create:**
- `src/main/java/ec/control/electoral/auth/infrastructure/email/EmailService.java` (Interface)
- `src/main/java/ec/control/electoral/auth/infrastructure/email/SmtpEmailProvider.java` (Implementation)
- `src/main/java/ec/control/electoral/auth/infrastructure/email/SendGridEmailProvider.java` (Future implementation)
- `src/main/java/ec/control/electoral/auth/infrastructure/email/EmailTemplate.java` (Enum)

**Work:**
```java
// Interface
public interface EmailService {
    void sendInvitationEmail(String email, String userName, String activationLink, String token);
    void sendActivationConfirmation(String email, String userName);
    void sendResendInvitation(String email, String userName);
}

// Implementation (SMTP)
@ApplicationScoped
public class SmtpEmailProvider implements EmailService {
    @Inject
    Mailer mailer;
    
    @ConfigProperty(name = "app.email.from")
    String fromEmail;
    
    // Methods...
}

// Enum for template selection
public enum EmailTemplate {
    INVITATION("email/invitation.html"),
    CONFIRMATION("email/confirmation.html"),
    RESEND("email/resend.html");
}
```

**Acceptance Criteria:**
- [ ] EmailService interface defined with 3 methods
- [ ] SmtpEmailProvider implements interface
- [ ] Configuration properties in application.properties
- [ ] Mock implementation available for tests
- [ ] No hardcoded SMTP credentials (use env vars)

---

### Task 1.3: Invitation Token Service
**Effort:** 1 punto | **Dependencies:** None | **Type:** Service

**Files to create:**
- `src/main/java/ec/control/electoral/auth/application/service/InvitationTokenService.java`

**Work:**
```java
@ApplicationScoped
public class InvitationTokenService {
    @ConfigProperty(name = "app.jwt.secret")
    String jwtSecret;
    
    @ConfigProperty(name = "app.invitation.token.expiry.hours")
    Long expiryHours; // 24
    
    // generateToken(usuarioId) → JWT with sub, exp, iat
    // validateToken(token) → usuarioId or throw exception
    // isTokenExpired(token) → boolean
    // consumeToken(usuarioId) → set activation_token = null
}
```

**Acceptance Criteria:**
- [ ] JWT token generation with 24h expiry
- [ ] JWT validation (signature, expiry, algorithm)
- [ ] parseToken() returns usuarioId
- [ ] Token refresh/regeneration not needed (not self-service)
- [ ] Unit tests: valid token, expired token, invalid signature

---

### Task 1.4: Password Validation Service
**Effort:** 0.5 puntos | **Dependencies:** None | **Type:** Service

**Files to create:**
- `src/main/java/ec/control/electoral/auth/application/service/PasswordValidationService.java`

**Work:**
```java
@ApplicationScoped
public class PasswordValidationService {
    public ValidationResult validate(String password) {
        // Requirements:
        // - Length >= 8
        // - At least 1 uppercase letter
        // - At least 1 number
        // - At least 1 special character
        
        return new ValidationResult(isValid, missingRequirements);
    }
}
```

**Acceptance Criteria:**
- [ ] Validates all 4 requirements
- [ ] Returns detailed feedback (which requirements fail)
- [ ] Unit tests for edge cases (empty, 7 chars, no special char, etc.)

---

### Task 1.5: Audit Service & Infrastructure
**Effort:** 1 punto | **Dependencies:** Task 1.1 (Schema) | **Type:** Service

**Files to create:**
- `src/main/java/ec/control/electoral/auth/application/service/InvitationAuditService.java`

**Work:**
- Log all invitation events to invitation_audit table
- Events: INVITATION_SENT, INVITATION_FAILED, ACTIVATION_SUCCESS, ACTIVATION_FAILED, TOKEN_EXPIRED
- Include: usuarioId, accion, evento, datos (JSONB with error details)

**Acceptance Criteria:**
- [ ] Audit table populated correctly
- [ ] All 5 events logged
- [ ] No PII in logs (email masked if needed)
- [ ] Query tests: can retrieve audit history by usuario_id

---

## SPRINT 2: Backend Use Cases & REST Endpoints (3-4 puntos)

### Task 2.1: EnviarInvitacionUseCase
**Effort:** 2 puntos | **Dependencies:** Task 1.2-1.5 | **Type:** Use case

**Files to create:**
- `src/main/java/ec/control/electoral/auth/application/usecase/EnviarInvitacionUseCase.java`
- `src/main/java/ec/control/electoral/auth/application/port/InvitationEmailQueue.java` (Port for async)
- `src/main/java/ec/control/electoral/auth/infrastructure/messaging/InvitationEmailQueueImpl.java` (Adapter)

**Work:**
```java
// Pseudo-flow
@Transactional
public EnviarInvitacionResponse execute(List<Long> usuarioIds) {
    List<Usuario> usuarios = validarUsuariosEnEstado(usuarioIds, PENDIENTE_INVITACION);
    
    List<InvitacionFailure> failures = new ArrayList<>();
    for (Usuario usuario : usuarios) {
        try {
            String token = invitationTokenService.generateToken(usuario.getId());
            usuario.setActivationToken(token);
            usuario.setInvitationSentAt(now);
            usuarioRepository.persist(usuario);
            
            // Publish async event to queue
            invitationQueue.enqueueWithRetry(
                new SendInvitationEvent(usuario.getEmail(), token, usuario.getNombre())
            );
            
            auditService.log(usuario.getId(), "INVITATION_SENT", usuario.getEmail());
        } catch (Exception e) {
            failures.add(new InvitacionFailure(usuario.getId(), e.getMessage()));
            auditService.log(usuario.getId(), "INVITATION_FAILED", e.getMessage());
        }
    }
    
    return new EnviarInvitacionResponse(usuarios.size(), failures);
}
```

**Acceptance Criteria:**
- [ ] Validates usuario state before sending
- [ ] Generates unique activation token per user
- [ ] Persists token to DB
- [ ] Publishes to async queue (email not sent synchronously)
- [ ] Rate limiting: max 100 emails/minute
- [ ] Retry logic: 3 attempts with exponential backoff (1s, 2s, 4s)
- [ ] Audit logged for each attempt
- [ ] Returns success count + failures list
- [ ] Integration tests with mock email queue

---

### Task 2.2: ActivarUsuarioPorTokenUseCase
**Effort:** 1.5 puntos | **Dependencies:** Task 1.2-1.4 | **Type:** Use case

**Files to create:**
- `src/main/java/ec/control/electoral/auth/application/usecase/ActivarUsuarioPorTokenUseCase.java`

**Work:**
```java
// Pseudo-flow
@Transactional
public ActivarUsuarioResponse execute(String token, String password) {
    // 1. Validate token
    Long usuarioId = invitationTokenService.validateToken(token);
    Usuario usuario = usuarioRepository.findById(usuarioId);
    
    // 2. Check estado
    if (!usuario.getEstadoUsuario().equals(PENDIENTE_INVITACION)) {
        auditService.log(usuarioId, "ACTIVATION_FAILED", "Already activated");
        throw new BusinessException("Usuario ya activado");
    }
    
    // 3. Check token expiry
    if (invitationTokenService.isTokenExpired(token)) {
        auditService.log(usuarioId, "ACTIVATION_FAILED", "Token expired");
        throw new BusinessException("Token expirado. Solicita una nueva invitación");
    }
    
    // 4. Validate password
    ValidationResult pwdValidation = passwordValidationService.validate(password);
    if (!pwdValidation.isValid()) {
        throw new BusinessException("Contraseña no cumple requisitos: " + pwdValidation.missingRequirements);
    }
    
    // 5. Update usuario
    usuario.setPassword(hashPassword(password));
    usuario.setEstadoUsuario(ACTIVO);
    usuario.setActivatedAt(now);
    usuario.setActivationToken(null); // Consume token
    usuarioRepository.persist(usuario);
    
    // 6. Audit
    auditService.log(usuarioId, "ACTIVATION_SUCCESS", "Usuario activado");
    
    return new ActivarUsuarioResponse(usuario.getId(), usuario.getNombre(), usuario.getEmail());
}
```

**Acceptance Criteria:**
- [ ] Token validation succeeds for valid JWT
- [ ] Rejects expired token
- [ ] Rejects if usuario already ACTIVO
- [ ] Password validation applied
- [ ] Usuario state updated to ACTIVO
- [ ] Token set to null (consumed)
- [ ] Audit logged
- [ ] Cannot reuse same token
- [ ] Unit + integration tests

---

### Task 2.3: REST Endpoints
**Effort:** 1 punto | **Dependencies:** Task 2.1-2.2 | **Type:** REST API

**Files to create/modify:**
- `src/main/java/ec/control/electoral/auth/api/rest/InvitacionResource.java` (NEW)
- `src/main/java/ec/control/electoral/auth/api/rest/ActivacionResource.java` (NEW)

**Work:**
```java
@Path("/api/v1/auth")
@ApplicationScoped
public class InvitacionResource {
    @POST @Path("/invitaciones")
    @RolesAllowed("ADMIN")
    public Response enviarInvitaciones(List<Long> usuarioIds) {
        // Call EnviarInvitacionUseCase
    }
    
    @GET @Path("/validate-token/{token}")
    public Response validateToken(@PathParam("token") String token) {
        // Light validation: token valid? user exists? expires when?
    }
}

@Path("/api/v1/auth")
@ApplicationScoped
public class ActivacionResource {
    @POST @Path("/activate")
    public Response activateUser(ActivateUserRequest req) {
        // Call ActivarUsuarioPorTokenUseCase
    }
}
```

**Acceptance Criteria:**
- [ ] POST /invitaciones: Admin only, returns {successCount, failures}
- [ ] GET /validate-token/{token}: Public, returns {valid, usuarioId, expiresAt}
- [ ] POST /activate: Public, expects {token, password, passwordConfirm}
- [ ] Proper HTTP status codes (200, 400, 401, 403, 404, 409)
- [ ] Error responses have clear messages
- [ ] Request validation (password != null, token format)
- [ ] Rate limiting on activate endpoint (max 10 attempts/min per IP)

---

### Task 2.4: Update LoginUseCase — Check User State
**Effort:** 0.5 puntos | **Dependencies:** Task 1.1 | **Type:** Bug fix/Enhancement

**Files to modify:**
- `src/main/java/ec/control/electoral/auth/application/usecase/LoginUseCase.java`

**Work:**
- Before issuing JWT, verify usuario.getEstadoUsuario() == ACTIVO
- If PENDIENTE_INVITACION, throw BusinessException("Usuario pendiente de activar")
- Audit failed login attempts

**Acceptance Criteria:**
- [ ] PENDIENTE_INVITACION users cannot login
- [ ] Error message clear
- [ ] Failed attempt audited

---

## SPRINT 3: Frontend & Email Templates (2-3 puntos)

### Task 3.1: ActivateUserComponent
**Effort:** 1.5 puntos | **Dependencies:** Task 2.3 (REST endpoints ready) | **Type:** Frontend

**Files to create:**
- `src/app/modules/auth/components/activate-user/activate-user.component.ts` (NEW)
- `src/app/modules/auth/components/activate-user/activate-user.component.html` (NEW)
- `src/app/modules/auth/components/activate-user/activate-user.component.scss` (NEW)

**Work:**
```typescript
// Component flow:
// 1. Read token from URL query param
// 2. Call GET /validate-token/{token}
// 3. If valid, show password form
// 4. If invalid/expired, show error + link to contact admin
// 5. Form: password, passwordConfirm with real-time validation
// 6. Display password requirements checklist (dynamic)
// 7. On submit: POST /activate {token, password}
// 8. On success: show confirmation screen + countdown to redirect to login
// 9. On error: show error message + retry button
```

**HTML structure:**
```html
<!-- Step 1: Validate Token -->
<div *ngIf="isLoading">
  <mat-spinner></mat-spinner>
  <p>Validando tu invitación...</p>
</div>

<!-- Step 2: Invalid/Expired Token -->
<div *ngIf="isInvalidToken">
  <mat-card>
    <h2>⚠️ Link Expirado</h2>
    <p>Tu link de activación expiró. Contacta al administrador para una nueva invitación.</p>
  </mat-card>
</div>

<!-- Step 3: Password Form -->
<div *ngIf="isFormReady">
  <form [formGroup]="activateForm">
    <mat-form-field>
      <input type="password" formControlName="password" 
             (input)="onPasswordChange()">
      <mat-error>Requerido</mat-error>
    </mat-form-field>
    
    <!-- Password Requirements Checklist -->
    <div class="requirements">
      <div [ngClass]="{'met': requirements.length}">
        ✓ Mínimo 8 caracteres
      </div>
      <div [ngClass]="{'met': requirements.uppercase}">
        ✓ Una letra mayúscula
      </div>
      <!-- ... more requirements -->
    </div>
    
    <mat-form-field>
      <input type="password" formControlName="passwordConfirm">
      <mat-error *ngIf="!passwordsMatch">Contraseñas no coinciden</mat-error>
    </mat-form-field>
    
    <button mat-raised-button color="primary" (click)="onActivate()">
      Activar Cuenta
    </button>
  </form>
</div>

<!-- Step 4: Success Screen -->
<div *ngIf="isSuccess">
  <mat-card>
    <h2>✅ ¡Cuenta Activada!</h2>
    <p>Tu cuenta está lista. Serás redirigido en {{ redirectCountdown }}s...</p>
    <p>O <a routerLink="/login">inicia sesión ahora</a></p>
  </mat-card>
</div>
```

**Acceptance Criteria:**
- [ ] Component loads from route `/auth/activate?token=...`
- [ ] Validates token on init (calls GET /validate-token)
- [ ] Shows proper error if token invalid/expired
- [ ] Password form reactive with validators
- [ ] Requirements checklist updates in real-time
- [ ] Disables submit button if password invalid
- [ ] POST /activate on form submit
- [ ] Shows success screen with countdown to redirect
- [ ] Redirects to login after countdown
- [ ] Error handling: shows error message, allows retry
- [ ] Unit tests for validators, error states

---

### Task 3.2: Email Templates
**Effort:** 0.5 puntos | **Dependencies:** Task 1.2 | **Type:** Email templates

**Files to create:**
- `src/main/resources/email/invitation.html` (NEW)
- `src/main/resources/email/confirmation.html` (NEW)
- `src/main/resources/email/resend.html` (NEW)

**Work:**
```html
<!-- invitation.html -->
<!DOCTYPE html>
<html>
<head>
  <meta charset="UTF-8">
  <style>
    body { font-family: Arial, sans-serif; background: #f5f5f5; }
    .container { max-width: 600px; margin: 20px auto; background: white; padding: 20px; }
    .button { background: #4CAF50; color: white; padding: 10px 20px; text-decoration: none; border-radius: 4px; }
    .footer { border-top: 1px solid #ddd; margin-top: 20px; font-size: 12px; color: #666; }
  </style>
</head>
<body>
  <div class="container">
    <h1>Bienvenido a Control Electoral</h1>
    <p>Hola {{ userName }},</p>
    <p>Tu cuenta ha sido creada como <strong>{{ userRole }}</strong>.</p>
    <p>Haz clic en el botón abajo para activar tu cuenta:</p>
    <p>
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

**Acceptance Criteria:**
- [ ] invitation.html: includes activation link, expiry warning
- [ ] confirmation.html: success message, login link
- [ ] resend.html: same as invitation (re-send scenario)
- [ ] All templates have proper HTML structure
- [ ] Variables templated ({{ userName }}, etc.)
- [ ] Mobile-responsive

---

### Task 3.3: Update Admin Panel — Invitation Actions
**Effort:** 1 punto | **Dependencies:** Task 2.1-2.3 | **Type:** Frontend

**Files to modify:**
- `src/app/modules/admin/components/usuarios-list/usuarios-list.component.ts`
- `src/app/modules/admin/components/usuarios-list/usuarios-list.component.html`

**Work:**
- Add checkbox column to usuarios list
- Add bulk actions toolbar: "Enviar Invitación", "Re-enviar Invitación"
- Call POST /invitaciones with selected usuarioIds
- Show progress spinner during sending
- Show success/failure toast notifications
- Handle per-user failures (show which emails failed)

**Acceptance Criteria:**
- [ ] Multi-select checkboxes on list
- [ ] Bulk action toolbar appears when users selected
- [ ] POST /invitaciones called with selected IDs
- [ ] Progress spinner shown during processing
- [ ] Toast notifications: "X invitaciones enviadas, Y errores"
- [ ] Failed emails listed for retry
- [ ] Table refreshes after success
- [ ] "Re-enviar" action available for PENDIENTE_INVITACION users

---

## SPRINT 4: Integration & Testing (1-2 puntos)

### Task 4.1: SMTP Configuration & Email Queue Consumer
**Effort:** 0.5 puntos | **Dependencies:** Task 1.2, Task 2.1 | **Type:** Infrastructure

**Files to modify/create:**
- `src/main/resources/application.properties` (add email config)
- `src/main/java/ec/control/electoral/auth/infrastructure/messaging/EmailQueueConsumer.java` (NEW)

**Work:**
```properties
# application.properties
quarkus.mailer.host=smtp.gmail.com
quarkus.mailer.port=587
quarkus.mailer.username=${SMTP_USERNAME}
quarkus.mailer.password=${SMTP_PASSWORD}
quarkus.mailer.ssl=true

app.email.from=noreply@control.electoral.ec
app.email.retry.max-attempts=3
app.email.retry.initial-delay=1000
app.email.retry.backoff-multiplier=2

# Consumer settings
app.email.queue.batch-size=10
app.email.queue.poll-interval=5000
```

**Acceptance Criteria:**
- [ ] Configuration externalised to env vars
- [ ] No credentials in code
- [ ] Email queue consumer implemented
- [ ] Retry logic: 3 attempts, exponential backoff
- [ ] Rate limiting: max 100 emails/minute
- [ ] Consumer tested with mock SMTP

---

### Task 4.2: Integration Tests
**Effort:** 1 punto | **Dependencies:** All tasks above | **Type:** Tests

**Files to create:**
- `src/test/java/ec/control/electoral/auth/InvitationIntegrationTest.java` (NEW)
- `src/test/java/ec/control/electoral/auth/ActivationIntegrationTest.java` (NEW)

**Test scenarios:**
```
InvitationIntegrationTest:
  ✓ Send invitation to single user
  ✓ Send invitation to bulk users (10)
  ✓ Fail gracefully if user not in PENDIENTE_INVITACION state
  ✓ Fail gracefully if email send fails (retry, then log)
  ✓ Email queue processes messages
  ✓ Rate limiting: > 100 emails/min rejected

ActivationIntegrationTest:
  ✓ Activate with valid token + password
  ✓ Reject expired token
  ✓ Reject invalid password (too short, no uppercase, etc.)
  ✓ Reject already activated user
  ✓ Cannot reuse token
  ✓ Usuario state changes to ACTIVO
  ✓ User can login after activation
  ✓ Failed activation attempts audited
```

**Acceptance Criteria:**
- [ ] All scenarios tested end-to-end
- [ ] Database verified after each test
- [ ] Audit table verified
- [ ] Mock email provider works
- [ ] Tests isolated (no test order dependencies)
- [ ] Coverage > 80%

---

### Task 4.3: Frontend Component Tests
**Effort:** 0.5 puntos | **Dependencies:** Task 3.1 | **Type:** Tests

**Work:**
- Unit tests for password validation logic
- Tests for requirements checklist updates
- Tests for form submission
- Mock API calls

**Acceptance Criteria:**
- [ ] Password validator tests pass
- [ ] Requirements checklist tests pass
- [ ] Form submission tests pass
- [ ] API error handling tested
- [ ] Coverage > 80%

---

## ROLLOUT STRATEGY

### Phase 1: Soft Launch (Internal Testing)
- Deploy to staging
- Admins test with 10-20 test users
- Verify email delivery
- Check activation flows

### Phase 2: Limited Rollout (Observer Testing)
- Deploy to pre-production
- 50-100 test observers use feature
- Gather feedback
- Monitor for errors/edge cases

### Phase 3: Full Production
- Deploy to production
- Use for next electoral cycle
- Monitor audit logs
- Collect feedback for improvements

---

## SUCCESS CRITERIA

```
✅ All tasks completed:
  [ ] Sprint 1: DB + services ready
  [ ] Sprint 2: Backend APIs tested
  [ ] Sprint 3: Frontend component + templates ready
  [ ] Sprint 4: Integration tests pass, coverage > 80%

✅ Load testing (k6):
  [ ] Bulk invite 500 users: p95 < 5s
  [ ] Email queue: 100 emails/min without failures
  [ ] Activate endpoint: 10 concurrent activations < 2s

✅ Security:
  [ ] JWT tokens: 24h expiry, signed
  [ ] Passwords: hashed with bcrypt, salt
  [ ] Audit: all events logged
  [ ] Rate limiting: email + activate endpoint

✅ Documentation:
  [ ] API docs (Swagger/OpenAPI)
  [ ] Email template preview available
  [ ] Runbook for troubleshooting
  [ ] Admin guide for bulk invitations
```

---

## RISK REGISTER

| Risk | Probability | Impact | Mitigation |
|------|-------------|--------|-----------|
| Email delivery fails | Medium | High | Mock mode for testing, retry logic, audit trail |
| Token collision | Very Low | Critical | Use UUID v4 in JWT, unique constraint on DB |
| Rate limiting too strict | Medium | Medium | Monitor, adjust limits based on actual usage |
| Password validation too strict | Medium | Low | Clearly document requirements in UI |
| Database migration rollback | Low | High | Test rollback script in staging first |

---

## DEPENDENCIES & BLOCKERS

- Quarkus mailer dependency already available? → Check pom.xml
- SMTP credentials ready (Gmail, SendGrid, or internal)? → Config
- Admin panel for bulk actions → Existing? Or add in Sprint 3?

---

**Next Step:** Start Sprint 1 (DB migration + services)  
**Estimated Timeline:** 2-3 weeks with 1-2 devs  
**Last Updated:** 2026-11-05
