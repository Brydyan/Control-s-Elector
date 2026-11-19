# Design — User Profile & Settings

---

## API Endpoints

### 1. GET /api/v1/user/profile
**Responsable:** Backend auth layer  
**Autenticación:** JWT token requerido

```json
Response (200):
{
  "id": "uuid",
  "nombre": "Juan Pérez",
  "emailControl": "j@control.ec",        // immutable
  "emailPersonal": "j.personal@gmail.com", // mutable, puede ser null
  "rol": "MASTER",
  "estado": "ACTIVO",
  "lastPasswordChangeAt": "2026-11-15T10:30:00Z"
}
```

---

### 2. POST /api/v1/auth/change-password
**Responsable:** PasswordChangeService  
**Autenticación:** JWT token requerido  
**Rate Limit:** 1 request/hora per IP+userID  

```json
Request:
{
  "currentPassword": "TempPassword123!",
  "newPassword": "NewPassword@2026",
  "passwordConfirm": "NewPassword@2026"
}

Response (200):
{
  "message": "Password updated successfully",
  "lastPasswordChangeAt": "2026-11-19T14:30:00Z"
}

Errors:
  400 Bad Request — currentPassword incorrect
  400 Bad Request — newPassword == currentPassword
  400 Bad Request — passwords don't match
  400 Bad Request — password doesn't meet requirements (8+ chars, upper, lower, number, special)
  429 Too Many Requests — rate limit exceeded (1/hour)
```

---

### 3. PUT /api/v1/user/update-personal-email
**Responsable:** UserService  
**Autenticación:** JWT token requerido  
**Validación:** Email debe ser único o null  

```json
Request:
{
  "emailPersonal": "newemail@gmail.com",
  "password": "CurrentPassword123!"  // confirm current identity
}

Response (200):
{
  "message": "Personal email updated successfully",
  "emailPersonal": "newemail@gmail.com"
}

Errors:
  400 Bad Request — email already in use by another user
  400 Bad Request — password incorrect
  400 Bad Request — invalid email format
  400 Bad Request — emailPersonal cannot be same as emailControl
```

---

## Database Changes

### Add Column: Usuario.email_personal

```sql
ALTER TABLE usuario ADD COLUMN email_personal VARCHAR(255) UNIQUE NULL;
```

**Notas:**
- Inmediatamente nullable para usuarios existentes
- Índice UNIQUE permite NULL (SQL standard)
- No afecta emailControl (ya existe, es NOT NULL)

---

## Validaciones Backend

### PasswordValidationService
```
✓ Mínimo 8 caracteres
✓ Al menos 1 mayúscula
✓ Al menos 1 minúscula
✓ Al menos 1 número
✓ Al menos 1 carácter especial (!@#$%^&*)
✓ No igual a contraseña anterior (compare hash con bcrypt)
```

### EmailValidationService
```
✓ Formato RFC 5322 válido
✓ No igual a emailControl
✓ Único en tabla usuario (si no es null)
✓ No puede ser dominio prohibido (ej: temporal@test.com)
```

---

## Security Considerations

### S1 — Password Hashing
- Algoritmo: bcrypt (cost factor 12)
- Nunca log plaintext passwords
- Validación: comparar hash con bcrypt.compare()

### S2 — Rate Limiting
- Change-password: 1 request/hora per IP + userID
- Backend: usar Redis con key `auth:change-password:{userId}:{ipAddress}`
- Response: 429 Too Many Requests si excede

### S3 — Email Change Confirmation (Opcional)
- Enviar email de confirmación a nueva dirección
- Link para confirmar (token TTL 24h)
- Implementar en fase 2 (MVP: cambio inmediato)

### S4 — Audit Trail
- Crear entrada en tabla `audit_log` para:
  - Change-password (usuario, timestamp, ip)
  - Email-change (usuario, old email, new email, timestamp, ip)

---

## Frontend Architecture

### Component: profile-settings.page.ts

Estados:
- `loadingProfile` — GET /user/profile en progreso
- `selectedTab` — 'profile' | 'password' | 'email'
- `changePasswordForm` — FormGroup con validation
- `emailForm` — FormGroup
- `successMessage` — feedback usuario
- `errorMessage` — feedback de errores

Métodos:
```typescript
loadProfile(): void
changePassword(): void  // POST /auth/change-password
updateEmail(): void     // PUT /user/update-personal-email
resetForms(): void
showNotification(message, type): void
```

### Validación Frontend
- Confirm password match (real-time)
- Password requirements feedback (5 checkboxes)
- Email format validation
- Disable submit si form inválido

---

## Testing Strategy

### Backend Tests
- **Unit:** PasswordChangeService.changePassword()
- **Integration:** POST /auth/change-password → JWT + rate limit
- **Integration:** PUT /user/update-personal-email → unique constraint
- **Security:** Rate limit resets after 1 hour
- **Edge cases:** 
  - currentPassword incorrect
  - newPassword == oldPassword
  - Invalid email format
  - Duplicate email_personal

### Frontend Tests
- **Unit:** Form validation rules
- **Integration:** Load profile → display immutable fields
- **Integration:** Submit change-password → success message
- **Integration:** Submit email change → success message
- **E2E:** Complete flow: login → settings → change password → logout → login with new password

---

## Rollout & Rollback

### Rollout (Progressive)
1. **Phase 0:** Merge database migration (nullable email_personal column)
2. **Phase 1:** Backend endpoints (change-password, update-email, get-profile)
3. **Phase 2:** Frontend UI + tests
4. **Phase 3:** Docs + rollout

### Rollback
- Revert migration: `ALTER TABLE usuario DROP COLUMN email_personal`
- Endpoints: 404 Not Found → frontend shows "Coming soon"

---

## Archivo Changes Summary

| Archivo | Acción | Notas |
|---------|--------|-------|
| Backend: `UsuarioEntity.java` | Add | Campo `emailPersonal: String` |
| Backend: `PasswordChangeService.java` | New | Validación + bcrypt compare |
| Backend: `PasswordChangeResource.java` | New | POST /auth/change-password |
| Backend: `UserResource.java` | Modify | Agregar PUT /user/update-personal-email + GET /user/profile |
| Backend: `UserService.java` | Modify | Lógica de update email |
| Backend: `RateLimitService.java` | Modify | Configurar rate limit para change-password |
| Liquibase: `xxx-usuario-email-personal.sql` | New | ALTER TABLE usuario ADD email_personal |
| Frontend: `profile-settings.page.ts` | New | Component principal |
| Frontend: `profile-settings.page.html` | New | Template con tabs |
| Frontend: `user.api.ts` | Modify | Agregar endpoints |
| Tests: `password-change.spec.ts` | New | Tests de cambio de contraseña |
| Tests: `profile-settings.page.spec.ts` | New | Tests de UI |

