# ODD — Fix: Invitación Duplicada (Race Condition)

**Status:** 📋 Ready for Builder  
**Ticket:** SC-522 (admin/usuarios)  
**Bug:** Se crean 2 invitaciones cuando usuario presiona "Guardar" dos veces  
**Root Cause:** Race condition + falta validación preventiva + estado de UI inconsistente

---

## 📌 Scope

### A. Frontend — Botón "Guardar" desactiva correctamente

**Archivo:** `src/app/features/admin/pages/usuarios/admin-usuarios.page.ts`

**Fix:**
- Línea 373, método `puedeGuardar()`
- Actual: `return !this.invitaando();`
- Cambio: `return !this.submitting() && !this.invitaando();`
- Razón: Verifica AMBAS señales (submitting = POST en vuelo, invitaando = otra operación)

**Test:** Presionar "Guardar" dos veces rápido → Botón desactiva en segundo intento

---

### B. Backend — Validar emailPersonal NO duplicado

**Archivo:** `src/main/java/ec/twintailcs/electoral/auth/application/UserInvitationService.java`

**Fix:**
- Línea 107, método `invitar()`
- Agregar validación ANTES de `usuario.persist()`:
  ```java
  if (usuarioRepository.existsByEmailPersonal(emailPersonal)) {
    throw new ConflictException(
      "Ya existe usuario con email personal: " + emailPersonal);
  }
  ```
- Razón: Rechaza con 409 ANTES de que dos transacciones paralelas creen duplicado

**Test:** 
- Invitar usuario1 con email X
- Invitar usuario2 con email X simultáneamente → Segunda solicitud recibe 409

---

### C. DB — Eliminar DEFERRABLE (Opcional, pero recomendado)

**Archivo:** `src/main/resources/database/changelog/046-usuario-email-personal-unique.sql`

**Fix (alternativa):**
Cambiar constraint:
```sql
-- Actual:
ALTER TABLE usuario ADD CONSTRAINT unique_email_personal 
  UNIQUE (email_personal) DEFERRABLE INITIALLY DEFERRED;

-- Cambiar a:
ALTER TABLE usuario ADD CONSTRAINT unique_email_personal_not_null 
  UNIQUE (email_personal) WHERE email_personal IS NOT NULL;
```
- Razón: Elimina ventana de race condition, además permite múltiples NULLs

**Nota:** Este cambio requiere migración Liquibase. Si no se hace, la validación de negocio (B) lo cubre.

---

## 🎯 Checklist

- [ ] **A1:** Frontend `puedeGuardar()` verifica `submitting`
- [ ] **A2:** Test: botón desactiva al presionar dos veces
- [ ] **B1:** Backend agrega `existsByEmailPersonal()` check
- [ ] **B2:** Test: segunda invitación recibe 409 Conflict
- [ ] **B3:** Test: mensaje de error es claro para usuario
- [ ] **C1:** (Opcional) DB migration para eliminar DEFERRABLE
- [ ] **C2:** Spotless check pasa
- [ ] **C3:** 224 backend tests pasan

---

## 🧪 Test Cases

### Caso 1: Doble click rápido (Frontend)
1. Abrir admin/usuarios
2. Crear usuario nuevo
3. Presionar "Guardar" dos veces rápido
4. ✅ Expected: Solo 1 invitación creada, botón desactiva

### Caso 2: Invitación duplicada (Backend)
1. Invitar usuario1 con email: `ana@control.ttcs`
2. Intentar invitar usuario2 con mismo email
3. ✅ Expected: HTTP 409, mensaje "Ya existe usuario con email personal: ana@control.ttcs"

### Caso 3: Paralelo (Race Condition)
1. Enviar dos POST `/auth/invitaciones` con mismo `emailPersonal` simultáneamente
2. ✅ Expected: Una tiene éxito (201), la otra falla (409)

---

## 📊 Impacto

| Aspecto | Antes | Después |
|--------|-------|---------|
| Duplicados por doble click | Sí ❌ | No ✅ |
| Validación de negocio | Ausente | 409 Conflict |
| DB race condition | Posible | Eliminada |
| UX feedback | Confuso | Claro |

---

## 🔗 Referencias

- **SDD padre:** T-AUTH-INV-01 (Bulk Invite Feature)
- **Issue:** SC-522
- **Commits relacionados:**
  - c14cea3: CDI @Qualifier pattern
  - 7149f73: FQ annotations
  - 7eb304a: Lazy RESEND_API_KEY

---

## 📝 Apply-Progress Template

```markdown
# Apply-Progress — Invitación Duplicada

## Status: [TODO → IN_PROGRESS → DONE]

### A. Frontend
- [ ] `puedeGuardar()` actualizado
- [ ] Tests: botón desactiva
- [ ] Spotless check

### B. Backend
- [ ] `existsByEmailPersonal()` agregado
- [ ] Validación lanzar 409
- [ ] Tests: duplicado rechazado
- [ ] Spotless check

### C. DB (Opcional)
- [ ] Migration: DEFERRABLE eliminado
- [ ] Tests: constraint valida

### Results
- [ ] 224 backend tests ✅
- [ ] 1105 frontend tests ✅
- [ ] Manual test: doble click ✅
- [ ] Manual test: paralelo ✅

### Commits
- [ ] fix(admin): desactiva guardar durante invitación
- [ ] fix(auth): valida emailPersonal no duplicado
- [ ] (opt) database: elimina DEFERRABLE en constraint
```

---

## ✅ Próximos Pasos

**→ Listo para `/02-build-odd`**

Builder:
1. Implementar fixes A, B (C es opcional)
2. Pasar checklist
3. Crear PR con tests verdes
4. Merge cuando AC's pasen
