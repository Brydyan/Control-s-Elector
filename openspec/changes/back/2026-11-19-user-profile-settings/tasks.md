# Tasks — User Profile & Settings

---

## A. Database Migration

- [ ] Crear liquibase changeset: `xxx-usuario-email-personal.sql`
  - `ALTER TABLE usuario ADD COLUMN email_personal VARCHAR(255) UNIQUE NULL`
  - Add index: `CREATE UNIQUE INDEX idx_usuario_email_personal ON usuario(email_personal) WHERE email_personal IS NOT NULL`
- [ ] Validar migración en BD local: `select * from usuario` muestra nueva columna
- [ ] No hay rollback bloqueante (nullable, no hay cascadas)

---

## B. Backend: Password Change Endpoint

- [ ] Crear `PasswordChangeService.java`
  - Método: `changePassword(userId: UUID, currentPassword: String, newPassword: String): void`
  - Validaciones internas:
    - Fetch usuario por ID
    - `bcrypt.verify(currentPassword, usuario.passwordHash)` → error si no match
    - `validatePasswordComplexity(newPassword)` → 8+ chars, upper, lower, number, special
    - `!bcrypt.matches(newPassword, usuario.passwordHash)` → error si igual a anterior
  - Acción: `usuario.setPasswordHash(bcrypt.hash(newPassword, 12))`
  - Audit: agregar entrada en tabla `audit_log`

- [ ] Crear `PasswordChangeResource.java` con endpoint `POST /api/v1/auth/change-password`
  - Autenticación: `@Auth Usuario usuario` (JWT)
  - Request body: `ChangePasswordRequest { currentPassword, newPassword, passwordConfirm }`
  - Validaciones externas:
    - `newPassword == passwordConfirm` → 400 "Passwords don't match"
  - Rate limit: `RateLimitService.checkLimit("auth:change-password", userId, ipAddress)` → 429 si excede 1/hora
  - Response: `{ "message": "...", "lastPasswordChangeAt": "..." }`

---

## C. Backend: Email Update Endpoint

- [ ] Crear `UserUpdateService.java` con método `updatePersonalEmail(userId: UUID, emailPersonal: String, confirmPassword: String): void`
  - Validaciones:
    - Fetch usuario
    - `bcrypt.verify(confirmPassword, usuario.passwordHash)` → error si no match
    - `validateEmailFormat(emailPersonal)` → RFC 5322
    - `!emailPersonal.equals(usuario.emailControl)` → error si igual a email control
    - `!existsOtherUserWithEmail(emailPersonal, userId)` → error si email duplicado
  - Acción: `usuario.setEmailPersonal(emailPersonal)`
  - Audit: "Usuario cambió email personal de X a Y"

- [ ] Modificar `UserResource.java`
  - Agregar `PUT /api/v1/user/update-personal-email`
  - Agregar `GET /api/v1/user/profile`

- [ ] Endpoint `GET /api/v1/user/profile` retorna:
  ```json
  {
    "id", "nombre", "emailControl", "emailPersonal", 
    "rol", "estado", "lastPasswordChangeAt"
  }
  ```

---

## D. Frontend: Profile Settings Page

- [ ] Crear página: `profile-settings.page.ts` con componentes:
  - `ProfileHeaderComponent` — muestra nombre + email control (read-only)
  - `ChangePasswordFormComponent` — formulario con validación real-time
  - `UpdateEmailFormComponent` — formulario simple

- [ ] Crear `profile-settings.page.html` con tabs:
  - Tab 1: "Mi Perfil" → datos inmutables + último cambio de contraseña
  - Tab 2: "Cambiar Contraseña" → form con password requirements feedback
  - Tab 3: "Email Personal" → form para actualizar email_personal

- [ ] Validaciones frontend:
  - Confirm password match (live)
  - 5 checkboxes: 8+ chars, uppercase, lowercase, number, special char
  - Email format: simple regex
  - Disable submit si invalid

- [ ] API calls en `user.api.ts`:
  ```typescript
  getProfile(): Observable<ProfileDto>
  changePassword(request: ChangePasswordRequest): Observable<any>
  updatePersonalEmail(request: UpdateEmailRequest): Observable<any>
  ```

---

## E. Testing

### Backend Unit Tests
- [ ] `PasswordChangeServiceTest`
  - currentPassword incorrecto → error
  - newPassword == currentPassword → error
  - newPassword válida → success
  - audit log creado

- [ ] `UserUpdateServiceTest`
  - emailPersonal duplicado → error
  - emailPersonal == emailControl → error
  - emailPersonal válida → success
  - audit log creado

### Backend Integration Tests
- [ ] POST /auth/change-password → JWT + rate limit + success
- [ ] Rate limit: 2 requests en 1 hora → segundo falla 429
- [ ] PUT /user/update-personal-email → success + audit
- [ ] GET /user/profile → retorna datos correctos

### Frontend Tests
- [ ] `profile-settings.page.spec.ts`
  - Load profile → display immutable fields ✓
  - Form validation: empty → submit disabled
  - Submit change-password → API called ✓
  - API error → show error message
  - API success → show success + clear form
  - Submit email update → API called ✓

### E2E Tests (Optativo para MVP, Phase 2)
- [ ] Login → Settings → Change password → Logout → Login con nueva contraseña ✓

---

## F. Documentation

- [ ] Update `AUTHENTICATION.md`:
  - Sección: "Password Management" → User can change via /settings

- [ ] Update `API_REFERENCE.md`:
  - POST /api/v1/auth/change-password
  - PUT /api/v1/user/update-personal-email
  - GET /api/v1/user/profile

- [ ] Create `SETTINGS_UX.md`:
  - Screenshots de flow
  - Validación de requisitos
  - Comportamiento de errores

---

## Implementation Checklist

### Pre-Build
- [ ] Arquitecto aprueba D1-D5
- [ ] Product Manager confirma MVP scope (¿Phase 2 email confirmation?)

### Build Phase
- [ ] A: Migration + BD testing
- [ ] B: PasswordChangeService + endpoint + rate limit
- [ ] C: UserUpdateService + endpoints
- [ ] D: Frontend components + template
- [ ] E: Tests (Backend integration + Frontend)
- [ ] F: Documentación

### Post-Build
- [ ] No regresiones en login flow
- [ ] No regresiones en admin user creation
- [ ] Audit log visible en backend logs
- [ ] Rate limit funciona (test 429 response)

---

## Risk Assessment

| Risk | Mitigation |
|------|-----------|
| Cambio de contraseña afecta login | Tests E2E: login con nueva contraseña |
| Rate limit bloquea usuario legítimo | Verificar 1 hora es suficiente, considerar 10 min/5 req |
| Email duplicado no detectado | Índice UNIQUE con constraint check |
| Password validation diferentes (FE vs BE) | Tests: mismo set de reglas en ambos |
| Audit log crece infinito | Configurar retention policy (ej: 90 días) |

---

## Notas de Diseño

1. **¿Por qué pedir contraseña actual en email change?**
   - Prevenir CSRF. Si atacante tiene sesión abierta, debe confirmar identidad.

2. **¿Por qué rate limit 1/hora en password?**
   - Prevenir credential stuffing. Ajustable si UX lo requiere.

3. **¿Email personal puede quedar null?**
   - SÍ. Es contacto personal, usuario elige actualizar o no.

4. **¿Future: email change confirmation?**
   - Phase 2. Enviar link de confirmación a nuevo email (token TTL 24h).
   - MVP: cambio inmediato para acelerar.

