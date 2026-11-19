# ODD — Invitación Inversible: Check determina estado cuenta

**Referencia:** T-AUTH-INV-02 (Invitación con email personal)  
**Epic:** Admin › Gestión de Usuarios  
**Componente:** `AdminUsuariosPage` — modal creación usuario  

---

## Propuesta

Refinar lógica de invitación: check "Enviar Invitación por Email" determina **estado activación cuenta**:

| Check | Acción | Estado Cuenta | Muestra |
|-------|--------|---------------|---------|
| ✅ Activado | Enviar invitación | **INACTIVA** (pending email) | Nada (usuario activa via link) |
| ❌ Desactivado | NO enviar email | **ACTIVA** | PasswordTemporal (accede directo) |

**Hoy:** Independientes. Check solo controla envío de email, pero cuenta siempre se crea con estado implícito.  
**Propuesta:** Check → determina ambos: envío + estado.

---

## Scope

### A. Ajustar endpoint backend
- [ ] `POST /admin/usuarios` recibe `sendInvitation: boolean`
- [ ] Si `true` → crea cuenta con `estado='PENDING_INVITATION'` (inactiva)
- [ ] Si `false` → crea cuenta con `estado='ACTIVE'` (activa directo)
- [ ] Validar: solo cuentas ACTIVE pueden login

### B. Refactorizar frontend logic
- [ ] Si check activado:
  - Llama `crearUsuario(sendInvitation=true)` → no muestra passwordTemporal
  - Muestra: "Invitación enviada a email"
- [ ] Si check desactivado:
  - Llama `crearUsuario(sendInvitation=false)` → muestra passwordTemporal
  - Mensaje: "Cuenta activa. Usar password temporal para acceder"

### C. Actualizar templates
- [ ] HTML: condicional en `guardar()` → mostrar passwordTemporal solo si `!enviarInvitacionPorEmail()`
- [ ] Mensaje contextual claro para ambos casos

### D. Validaciones
- [ ] Si `sendInvitation=false` → email REQUERIDO (para acceso posterior)
- [ ] Backend rechaza `sendInvitation=false` sin email válido
- [ ] No permitir cambiar estado después de creación (separar endpoint para eso)

### E. Tests
- [ ] Si check activado → cuenta NO activa, invitación enviada, NO muestra password
- [ ] Si check desactivado → cuenta ACTIVA, NO invitación, muestra password
- [ ] Si check desactivado + email inválido → error
- [ ] Usuario con cuenta ACTIVE puede login directo con passwordTemporal

### F. Documentación
- [ ] Update ODD padre (T-AUTH-INV-02) si existe
- [ ] Diagrama: flujo invitación vs acceso directo
- [ ] Notas: cambio de UX

---

## Decisiones de Diseño

**D1 — ¿Estado en BD o inferido?**
- Opción A: Guardar `invitationStatus` = PENDING_INVITATION | ACTIVE
- Opción B: Inferir de `invitationSentAt` (null = active, exists = pending)
- **Elegida:** A. Explícito = claridad. Estado es fact, no inferencia.

**D2 — ¿Bloquear login si PENDING_INVITATION?**
- Sí. AuthService chequea `estado != 'ACTIVE'` antes de permitir login.
- Error: "Cuenta pendiente de activación. Check email para link"

**D3 — ¿Cambiar estado post-creación?**
- No. Si usuario necesita cambiar: borrar + recrear (simple, raro caso).
- Mantiene audit clear.

---

## Cambios de Archivo

| Archivo | Acción | Notas |
|---------|--------|-------|
| Backend: `UsuarioAdminService.java` | Modify `crearUsuario()` | Acepta `sendInvitation` boolean |
| Backend: `UsuarioResponseDto` | Add | `invitationStatus: PENDING_INVITATION \| ACTIVE` |
| Frontend: `admin.api.ts` | Modify | `crearUsuario()` pasa `sendInvitation` |
| Frontend: `admin-usuarios.page.ts` | Modify | Lógica `guardar()` refactorizada |
| Frontend: `admin-usuarios.page.html` | Modify | Mostrar passwordTemporal condicional |
| Backend: `Usuario.java` | Add | Campo `invitationStatus` enum |
| Liquibase: `xxx-usuario-invitation-status.sql` | New | Migración: add column + default |

---

## Checklist de Implementación

- [ ] Backend: agregar enum `InvitationStatus`
- [ ] Backend: persistir `invitationStatus` en creación
- [ ] Backend: AuthService valida `estado == ACTIVE` en login
- [ ] Frontend: pasar `sendInvitation` a API
- [ ] Frontend: condicional passwordTemporal en template
- [ ] Frontend: mensajes contextuales
- [ ] Tests: 2 paths (con/sin invitación)
- [ ] Verificar no regresión: table, re-enviar, re-habilitar

---

## Apply Progress

```markdown
# Apply Progress — Invitación Inversible

## Estado: PENDIENTE

Esperando asignación a Builder.

## Commits aplicados
(ninguno aún)

## Notas
(a llenar durante implementación)
```

---

## Próximos Pasos

✅ QA aprobó: Cambio clasificado como ODD (ajuste a T-AUTH-INV-02)

→ **Listo para** `/02-build-odd` — Builder implementa scope A-F
