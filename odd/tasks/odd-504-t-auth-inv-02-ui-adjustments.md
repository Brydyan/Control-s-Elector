# ODD #504: T-AUTH-INV-02 UI Adjustments + 24h Expiry

## Contexto

SDD #492 (T-AUTH-INV-02: User Invitations + SMTP) fue diseñada pero UI/UX analysis reveló:
1. **Falta toggle** para opcional envío de email (crear usuario sin invitar aún)
2. **Falta acción resend** en tabla usuarios (re-enviar invitación)
3. **Expiry time incorrecto**: SDD dice 30 días, debería ser 24 horas
4. **Falta API endpoint** para resend-invitation

Este ODD documenta ajustes antes de pasar a Builder.

---

## Criticidad

🟡 **ALTO** — Mejora UX critical, pero lista para implementación.

**Por qué importante:**
- 24h expiry = fuerza al usuario activar rápido (mejor seguridad)
- Toggle = flexibility en bulk user creation
- Resend = soporte operacional (invitación perdida, etc)

---

## Alcance (A-F Tasks)

### A · Toggle "Enviar Invitación por Email"

- [ ] **A.1** — En form crear usuario, añadir field:
  ```
  ☑ Enviar Invitación por Email (default: ON)
  ℹ️ Si desactivas, solo se crea el usuario sin email
  ```

- [ ] **A.2** — Frontend envía `sendEmail: boolean` en POST body:
  ```json
  POST /api/v1/users/invite
  {
    nombre: "Juan",
    apellido: "García",
    emailPersonal: "juan@example.com",
    rol: "OBSERVER",
    sendEmail: true  // ← nuevo field
  }
  ```

- [ ] **A.3** — Backend:
  - Si `sendEmail=true`: genera temp password + envía SMTP
  - Si `sendEmail=false`: crea usuario, NO envía email
  - Log auditoría: `USER_CREATED[_WITHOUT_EMAIL]`

- [ ] **A.4** — Response diferenciada:
  ```json
  {
    success: true,
    message: sendEmail 
      ? "Invitación enviada a juan@example.com"
      : "Usuario creado. Puedes enviar invitación después."
  }
  ```

---

### B · Cambiar Expiry de 30 días a 24 horas

- [ ] **B.1** — Backend: `user_invitations` table
  - Cambiar default expiry: `30 DAYS` → `1 DAY` (24 horas)
  - Migrate existing records: `expiry_at = created_at + INTERVAL '1 day'`

- [ ] **B.2** — Email template actualizar:
  ```
  Antes: "Esta contraseña expira en 30 días"
  Después: "Esta invitación expira en 24 horas"
  ```

- [ ] **B.3** — Frontend: Validación en tabla usuarios
  - Status EXPIRED = invitedAt + 24h < NOW
  - Toast warning si <2h para expirar

- [ ] **B.4** — Tests:
  ```java
  @Test void invitationExpiresAfter24Hours() {
    User invited = inviteUser("juan@example.com");
    Instant expiry = invited.getInvitationExpiresAt();
    assertEquals(24, ChronoUnit.HOURS.between(NOW, expiry));
  }
  ```

---

### C · Tabla Usuarios + Columna Estado

- [ ] **C.1** — Añadir columna "Estado" a tabla:
  ```
  Nombre | Email | Rol | Estado | Acciones
  ```

- [ ] **C.2** — Estados renderizados:
  - 🟢 **INVITED**: invitedAt < 24h y no logueado
  - 🟡 **EXPIRED**: invitedAt + 24h < NOW y no logueado
  - 🟢 **ACTIVE**: has logueado at least once
  - 🔴 **DISABLED**: disabled_at IS NOT NULL

- [ ] **C.3** — SQL query estado:
  ```sql
  CASE
    WHEN ui.activated_at IS NOT NULL THEN 'ACTIVE'
    WHEN ui.expired_at < NOW() AND ui.activated_at IS NULL THEN 'EXPIRED'
    WHEN u.disabled_at IS NOT NULL THEN 'DISABLED'
    ELSE 'INVITED'
  END AS invitation_status
  ```

---

### D · Botón "Re-enviar Invitación"

- [ ] **D.1** — En tabla acciones, botón "📧 Re-enviar Invitación"
  - Visible solo si: `invitation_status IN ('INVITED', 'EXPIRED')`
  - Hidden si: `invitation_status IN ('ACTIVE', 'DISABLED')`

- [ ] **D.2** — Modal confirmatorio:
  ```
  ¿Re-enviar invitación a juan.garcia@example.com?
  
  ⚠️ Se generará una nueva contraseña temporal.
  La anterior será invalidada.
  Nueva invitación expira en 24 horas.
  
  [Cancelar] [✓ Re-enviar por Email]
  ```

- [ ] **D.3** — Flujo post-click:
  ```
  Frontend: POST /api/v1/users/{userId}/resend-invitation
  Backend:
    1. Valida status = INVITED o EXPIRED
    2. Genera NUEVA temporaryPassword
    3. Actualiza user_invitations:
       - invited_at = NOW
       - expired_at = NOW + 24 HOURS
       - previous password invalidated
    4. Envía SMTP con NUEVAS credenciales
    5. Log auditoría: INVITATION_RESENT
  Response: { newInvitationSentAt, expiresAt }
  Frontend: Toast "✅ Invitación re-enviada a juan@example.com"
  Tabla: auto-refresh estado
  ```

- [ ] **D.4** — Test:
  ```java
  @Test void resendInvitationCreatesNewPassword() {
    User invited = inviteUser("juan@example.com");
    String oldPassword = invited.getTemporaryPassword();
    
    resendInvitation(invited.getId());
    
    String newPassword = invited.getTemporaryPassword();
    assertNotEquals(oldPassword, newPassword);
    assertTrue(newPassword.matches(TEMP_PASSWORD_REGEX));
  }
  
  @Test void resendInvitationReseXpiryTo24h() {
    User invited = inviteUser("juan@example.com", clock.instant());
    clock.advanceBy(Duration.ofHours(20));
    
    resendInvitation(invited.getId());
    
    Instant newExpiry = invited.getInvitationExpiresAt();
    assertEquals(24, ChronoUnit.HOURS.between(clock.instant(), newExpiry));
  }
  ```

---

### E · Backend Endpoint: Resend Invitation

- [ ] **E.1** — Endpoint definition:
  ```java
  @PostMapping("/{userId}/resend-invitation")
  @PreAuthorize("hasAnyRole('MASTER','ADMIN')")
  public ResponseEntity<ResendInvitationResponse> resendInvitation(
    @PathVariable UUID userId,
    @RequestHeader("User-Agent") String userAgent,
    HttpServletRequest request
  ) {
    // ... implementation
  }
  ```

- [ ] **E.2** — Request/Response DTOs:
  ```java
  // No request body needed (info from path)
  
  @Data
  class ResendInvitationResponse {
    boolean success;
    String message;
    Instant newInvitationSentAt;
    Instant expiresAt;
    String emailPersonal;
  }
  ```

- [ ] **E.3** — Error handling:
  ```
  400 BAD_REQUEST: User status not INVITED/EXPIRED
  404 NOT_FOUND: User not found
  403 FORBIDDEN: Caller is not MASTER/ADMIN
  500 INTERNAL_SERVER_ERROR: SMTP failure (log, retry async)
  ```

- [ ] **E.4** — Audit logging:
  ```
  action: "INVITATION_RESENT"
  usuario_id: invited user
  target_usuario: invited user
  ip_address: from request
  user_agent: from header
  metadata: { email_personal, new_expiry }
  ```

---

### F · Integration Tests

- [ ] **F.1** — Crear usuario SIN email:
  ```java
  @Test void createUserWithoutSendingEmail() {
    CreateUserRequest req = new CreateUserRequest()
      .nombre("Juan")
      .apellido("García")
      .emailPersonal("juan@example.com")
      .rol("OBSERVER")
      .sendEmail(false);  // ← key difference
    
    User user = userService.createUser(req);
    
    assertThat(user.getInvitationStatus()).isEqualTo("CREATED");
    verify(mailService, never()).sendInvitationEmail(any());  // No email sent
  }
  ```

- [ ] **F.2** — Crear usuario CON email:
  ```java
  @Test void createUserWithEmail() {
    CreateUserRequest req = new CreateUserRequest()
      .nombre("Juan")
      .emailPersonal("juan@example.com")
      .sendEmail(true);
    
    User user = userService.createUser(req);
    
    assertThat(user.getInvitationStatus()).isEqualTo("INVITED");
    verify(mailService, times(1)).sendInvitationEmail(
      argThat(email -> email.getTo().contains("juan@example.com"))
    );
  }
  ```

- [ ] **F.3** — Re-enviar invitación:
  ```java
  @Test void resendInvitationGeneratesNewPassword() {
    User invited = createAndInviteUser("juan@example.com");
    String oldPassword = invited.getTemporaryPassword();
    
    UserInvitationResponse response = userService.resendInvitation(invited.getId());
    
    User updated = userRepository.findById(invited.getId()).get();
    assertThat(updated.getTemporaryPassword()).isNotEqualTo(oldPassword);
    assertThat(response.getExpiresAt())
      .isCloseTo(NOW.plus(24, ChronoUnit.HOURS), within(1, ChronoUnit.SECONDS));
  }
  ```

- [ ] **F.4** — Expiry validation:
  ```java
  @Test void invitationStatusExpiredAfter24Hours() {
    User invited = createAndInviteUser("juan@example.com");
    clock.advanceBy(Duration.ofHours(25));  // past 24h
    
    UserInvitationStatus status = userService.getInvitationStatus(invited.getId());
    
    assertThat(status).isEqualTo(UserInvitationStatus.EXPIRED);
  }
  ```

---

## Mutaciones

- [ ] **M1** — Expiry time. Cambiar `+ INTERVAL '1 day'` a `+ INTERVAL '30 days'`.
  Mutación: test de 24h expiry debe fallar. Restaura.

- [ ] **M2** — Resend password invalidation. Quitar línea que invalida old password.
  Mutación: test debe detectar que ambas passwords funcionan (bad). Restaura.

- [ ] **M3** — Toggle behavior. Cambiar `if (sendEmail)` a `if (!sendEmail)`.
  Mutación: test de "crear sin email" envía email de todas formas. Restaura.

---

## Compuertas

```bash
# ¿24h expiry configurado?
SELECT expiry_at - invited_at FROM user_invitations LIMIT 1;
# Esperado: INTERVAL '1 day' (o 86400 seconds)

# ¿Resend endpoint accesible?
curl -X POST https://api.controls.brydyan.me/api/v1/users/{id}/resend-invitation \
  -H "Authorization: Bearer $TOKEN"
# Esperado: 200 si status INVITED/EXPIRED, 400 si ACTIVE

# ¿Frontend muestra estado?
# Navega a /master/usuarios
# Esperado: columna "Estado" con íconos 🟢/🟡/🔴

# ¿Toggle en form?
# Abre /master/usuarios → Crear Usuario
# Esperado: checkbox "Enviar Invitación por Email" visible

# ¿Tests pasan?
cd backend/electoral-api-quarkus
./mvnw -B test -Dtest=UserInvitationTest,UserInvitationResendTest
# Esperado: todos pasen (6+ tests new)
```

---

## Diferencias vs SDD #492

| Aspecto | SDD #492 | ODD #504 |
|---------|----------|----------|
| **Expiry** | 30 días | **24 horas** |
| **Toggle Email** | No | ✅ Sí |
| **Resend Invitación** | No | ✅ Sí |
| **API endpoint** | Crear solo | + Resend |
| **Tabla UI** | No estado | ✅ Estado column |
| **Acciones tabla** | No resend | ✅ Re-enviar btn |

---

## Qué NO hacer

- No cambiar SDD #492 (está archivada para verify)
- No cambiar expiry a más de 24h (seguridad)
- No permitir resend si status = ACTIVE (solo INVITED/EXPIRED)
- No guardar contraseña en logs

---

## Status

- [ ] Builder implementa A-F (2-3 horas)
- [ ] Tests all green (F.1–F.4 + M1–M3)
- [ ] Verify round 1 (mutaciones validadas)
- [ ] Archive to odd/archive/VERIFIED-2026-11-08-t-auth-inv-02-ui-adjustments.md
