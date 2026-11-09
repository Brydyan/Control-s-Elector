# T-COMPLIANCE-04: Tasks — Delete Account

**Effort:** 4-5 puntos → 1-2 sprints

---

## Sprint 1: Backend Infrastructure

### Task 4.1 — DB Migrations

**Estimación:** 1 pt

- [ ] **C.4.1.1** — Migration: usuario.deleted_at, deletion_status
  - Add columns: usuario.deleted_at (nullable), usuario.deletion_status (ACTIVE/DELETED_PENDING/DELETED_PERMANENT)
  - Migration: `030-usuario-deletion-fields.sql`

- [ ] **C.4.1.1 — Mutación**
  - DROP COLUMN deletion_status
  - Queries filtering by status should fail
  - Note: [test name]
  - Restore

- [ ] **C.4.1.2** — deletion_audit table
  - Migration: `031-deletion-audit.sql`
  - Columns: id, usuario_id, email_at_deletion, deleted_at, recovery_deadline, was_recovered, hard_deleted_at

- [ ] **C.4.1.2 — Mutación**
  - DROP TABLE deletion_audit
  - Audit queries fail
  - Note: [test name]
  - Restore

---

### Task 4.2 — Backend: Request Deletion Endpoint

**Estimación:** 1.5 pts

- [ ] **C.4.2.1** — POST /api/user/request-deletion
  - Input: {password: "xxx"} (confirm password)
  - Validates: password correct
  - Updates: usuario.deletion_status = "DELETED_PENDING", usuario.deleted_at = NOW()
  - Records: deletion_audit row
  - Sends: email de confirmación con recovery link

- [ ] **C.4.2.1 — Mutación**
  - Remove password validation
  - DELETE should work without password (should fail)
  - Note: [test name]
  - Restore

- [ ] **C.4.2.2** — POST /api/user/cancel-deletion/{token}
  - Input: token from email
  - Validates: token not expired (< 7 días)
  - Updates: usuario.deletion_status = "ACTIVO"
  - Records: deletion_audit.was_recovered = true

- [ ] **C.4.2.2 — Mutación**
  - Remove token expiry check
  - Token valid forever (should fail time-based test)
  - Note: [test name]
  - Restore

---

### Task 4.3 — Cron Job: Hard-Delete

**Estimación:** 1.5 pts

- [ ] **C.4.3.1** — Job: delete-account-job (runs daily)
  - Query: SELECT * FROM usuario WHERE deletion_status = "DELETED_PENDING" AND deleted_at < NOW() - 30 days
  - For each: DELETE FROM usuario WHERE id = X
  - Update: deletion_audit.hard_deleted_at = NOW()

- [ ] **C.4.3.1 — Mutación**
  - Comment out DELETE statement
  - Test: usuario should still exist (must fail)
  - Note: [test name]
  - Restore

---

## Sprint 2: Frontend UI

### Task 4.4 — Delete Account UI Component

**Estimación:** 1.5 pts

- [ ] **C.4.4.1** — DeleteAccountComponent
  - Path: `frontend/src/app/settings/delete-account/`
  - Button: "Eliminar Cuenta" (red, danger)
  - Modal: confirmación con password entry
  - Shows: "Se eliminará en 30 días"

- [ ] **C.4.4.1 — Mutación**
  - Remove password field
  - Modal should allow deletion without password (test fails)
  - Note: [test name]
  - Restore

---

### Task 4.5 — Recovery Link Handler

**Estimación:** 0.5 pts

- [ ] **C.4.5.1** — ActivateCancellationComponent
  - Route: `/auth/cancel-deletion?token=XXX`
  - Validates token
  - Shows: "Eliminación cancelada exitosamente"
  - Redirect a login

- [ ] **C.4.5.1 — Mutación**
  - Remove token validation
  - Any token should work (test fails)
  - Note: [test name]
  - Restore

---

## Gates

```bash
# 1. Migrations run
liquibase --url=jdbc:postgresql://localhost:5432/control_electoral update

# 2. Columns exist
psql -c "SELECT deletion_status FROM usuario LIMIT 1"

# 3. deletion_audit table exists
psql -c "SELECT * FROM deletion_audit LIMIT 1"

# 4. Endpoint responds
curl -H "Authorization: Bearer <JWT>" -X POST http://localhost:8080/api/user/request-deletion \
  -H "Content-Type: application/json" -d '{"password": "xxx"}'

# 5. Tests pass
rtk cargo test
npm run test
```

**Last Updated:** 2026-11-05
