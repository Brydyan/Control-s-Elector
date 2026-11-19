# Apply Progress — User Profile & Settings

```markdown
# Apply Progress — User Profile & Settings

## Estado: PENDIENTE

Esperando asignación a Builder (post-arquitecto review de D1-D5).

## Commits aplicados
(ninguno aún)

## Notas de Implementación
(a llenar durante build)

---

## Phase A: Database Migration

- [ ] Changeset liquibase creado
- [ ] Migración local ejecutada sin errores
- [ ] Columna email_personal visible en BD
- [ ] Índice UNIQUE creado

**Commits:** 
- TBD

---

## Phase B: Backend Password Change

- [ ] PasswordChangeService.java creado + unit tests ✓
- [ ] PasswordChangeResource.java creado con endpoint ✓
- [ ] Rate limit integrado (1 request/hora)
- [ ] Audit log implementado
- [ ] Integration tests: 3 scenarios (success, rate limit, invalid password)

**Commits:**
- TBD

---

## Phase C: Backend Email Update

- [ ] UserUpdateService.java creado ✓
- [ ] UserResource modificado: PUT + GET endpoints ✓
- [ ] Email validation + uniqueness check ✓
- [ ] Audit log integrado
- [ ] Integration tests: duplicate email, format validation

**Commits:**
- TBD

---

## Phase D: Frontend

- [ ] profile-settings.page.ts creado ✓
- [ ] profile-settings.page.html creado con 3 tabs ✓
- [ ] user.api.ts actualizado con endpoints ✓
- [ ] Form validation (5 password requirements, email format)
- [ ] Error/success messages implementadas

**Commits:**
- TBD

---

## Phase E: Testing

- [ ] Backend unit tests: PasswordChangeService, UserUpdateService
- [ ] Backend integration tests: endpoints + rate limit + audit
- [ ] Frontend tests: form validation, API calls, error states
- [ ] E2E (opcional): complete flow login → settings → change password → new login

**Test Coverage:** TBD%

**Commits:**
- TBD

---

## Phase F: Documentation

- [ ] AUTHENTICATION.md updated
- [ ] API_REFERENCE.md updated
- [ ] SETTINGS_UX.md created

**Commits:**
- TBD

---

## Regressions & Verification

- [ ] Admin user creation flow: no cambios
- [ ] Login flow: no cambios
- [ ] Existing password validation: same rules applied
- [ ] Rate limit: tested with multiple requests
- [ ] Audit log: visible + human-readable

**Result:** ✓ / ✗

---

## Risk Assessment Final

| Risk | Mitigated |
|------|-----------|
| Password change affects login | E2E test: new password login ✓ |
| Rate limit blocks user | Manual test: wait 1h, retry ✓ |
| Email duplicate detection | Unique constraint + test ✓ |
| Validation mismatch (FE/BE) | Same rules in both ✓ |
| Audit log retention | Configure TTL policy ✓ |

---

## Sign-Off

**Builder:** _______________  
**Date:** _______________  

**QA:** _______________  
**Date:** _______________  

**Ready for Merge:** [ ] Yes [ ] No

**Merge Commit:** TBD

```
