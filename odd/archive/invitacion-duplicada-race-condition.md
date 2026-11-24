# ODD — Fix: Invitación Duplicada (Race Condition)

**Status:** ✅ Verificado y archivado (2026-11-23)  
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

- [x] **A1:** Frontend `puedeGuardar()` verifica `submitting` — `admin-usuarios.page.ts:374-375` (`!this.submitting() && !this.invitaando()`); template `[disabled]="!puedeGuardar()"`
- [x] **A2:** Test: botón desactiva al presionar dos veces — `admin-usuarios.page.spec.ts`: `guardar_noDisparaSegundoRequestCuandoSubmitting`, `puedeGuardar_falseCuandoSubmitting`, `puedeGuardar_falseCuandoInvitaando`, `puedeGuardar_trueCuandoNoHayOperacionEnVuelo`
- [x] **B1:** Backend valida emailPersonal no duplicado — `UserInvitationService.invitar()`: `Usuario.count("emailPersonal", …) > 0` → 409; además la violación de unicidad de BD (SQLState 23505) se traduce al mismo 409 en `persistirUsuario()`
- [x] **B2:** Test: segunda invitación recibe 409 Conflict — `UserInvitationIT.unEmailPersonalYaUsadoResponde409` (secuencial) y `dosInvitacionesParalelasMismoEmailPersonal` (paralelo: nunca 500, nunca más de una fila)
- [x] **B3:** Test: mensaje de error es claro para usuario — el mensaje "Ya existe usuario con email personal: `<email>`" viaja en el body (`message`) y el frontend lo muestra tal cual (`readBackendMessage` en `shared/errors/error-handler.ts`)
- [x] **C1:** (Opcional) DB migration para eliminar DEFERRABLE — no aplicada: el índice único parcial `idx_usuario_email_personal` ya impide el duplicado en BD y la capa de aplicación traduce esa violación a 409; el schema no se toca
- [x] **C2:** Spotless check pasa — `spotless:check` BUILD SUCCESS · `prettier --check` OK · `pnpm lint` OK
- [x] **C3:** 224 backend tests pasan — la suite de hoy: **310 backend** (0 fallos, 0 errores) + **1158 frontend** (111 archivos) en verde

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

**→ Cerrado. ODD verificado y archivado en `odd/archive/`.**

Resultado de la verificación (2026-11-23):
- Tareas A y B implementadas y verificadas contra el spec.
- Backend: `UserInvitationIT` 16/16 verde; suite completa 310/310; `spotless:check` OK.
- Frontend: spec 16/16 verde; suite completa 1158 tests; `prettier --check` y `pnpm lint` OK.
- Mutaciones M-A1, M-A2, M-B1 y M-C1 cubiertas.
- Tarea C (eliminar el constraint `DEFERRABLE`) no se ejecuta: el índice único parcial
  `idx_usuario_email_personal` ya garantiza la integridad y el mapeo 23505 → 409 vive en
  `UserInvitationService.persistirUsuario()`. Sin cambios de schema (Liquibase es el único dueño).
- Commits: `73ff08b`, `d00c0fe`, `b1460f8`.
