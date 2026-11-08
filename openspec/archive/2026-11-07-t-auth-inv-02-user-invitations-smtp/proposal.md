# T-AUTH-INV-02: Propuesta — User Invitations + SMTP Email

**Date:** 2026-11-07  
**Author:** Control Electoral Team  
**Status:** Proposal  
**Priority:** ALTO  
**Estimación:** 3-4 puntos → 2-3 días

---

## Qué se construye

Sistema de **invitación de usuarios** por SMTP que permite:
- Formulario "Crear Usuario" con campo email personal
- Generación automática de username: `{3name}{3lastname}{3role}@control.ttcs`
- Envío de invitación SMTP con link de activación
- Confirmación de email antes de completar onboarding
- Auditoría de invitaciones (fecha, IP, estado)

---

## Por qué es necesario

**Hoy:**
```
❌ Formulario "Gestión de Usuarios" MUY simplificado
❌ NO hay campo email personal
❌ NO hay envío de invitación SMTP
❌ Username generación manual (error-prone)
❌ Usuario no sabe credenciales hasta irse del sistema
```

**Con esta SDD:**
```
✅ Email personal requerido (contacto)
✅ Username generado automático (seguro)
✅ Invitación SMTP con credenciales temporales
✅ Link activación en email
✅ Auditoría de invitaciones
✅ UX clara: "Invitación enviada a X"
```

---

## Problema específico

- **MASTER crea usuarios** pero flujo UI es minimal
- **Backend genera username** ({3name}{3lastname}{3role}) pero frontend desconoce
- **SMTP no enviado** (backend tiene config, frontend no integrado)
- **Auditoría falta** en tabla de invitaciones

---

## Solución de alto nivel

```
Flow Invitación Usuarios
├─ Frontend: Form "Crear Usuario"
│  ├─ Campos: Nombre, Apellido, Email Personal, Rol
│  ├─ Submit → POST /api/v1/users/invite
│  └─ Response: { username, temporary_password, status: "invited" }
│
├─ Backend: UserInvitationService
│  ├─ Generate username: {3name}{3lastname}{3role}@control.ttcs
│  ├─ Generate temporary_password (12 chars, secure)
│  ├─ Create record in usuario + user_invitations table
│  ├─ Send SMTP email (HTML template)
│  └─ Return username + password (never stored in plaintext)
│
└─ Email Template
   └─ "Bienvenido a Control Electoral"
      - Usuario: XX@control.ttcs
      - Contraseña temporal: ***
      - Link activación: https://control.ttcs/auth/activate?token=XXX
      - "Cambia tu contraseña en primer login"

BD Changes:
├─ usuario (ya existe)
└─ user_invitations (nueva tabla)
   (usuario_id, email_personal, invited_at, status, ip_address, activated_at)
```

---

## Alcance

✅ **DENTRO:**
- Formulario "Crear Usuario" con email personal
- Username automático ({3name}{3lastname}{3role}@control.ttcs)
- Temporary password generation (12 chars, upper+lower+digit+special)
- SMTP email send (HTML template, branded)
- Email validation (RFC 5322 basic)
- Auditoría: user_invitations table
- Response UI: "Invitación enviada a X"
- Multi-idioma: ES
- Rate limiting: 1 create/5s per IP

❌ **FUERA:**
- Email verification link (separado en T-AUTH-03)
- Password reset flow (separado)
- Bulk user import (futuro)
- 2FA setup en invitación
- Custom SMTP template per role

---

## Dependencias / Supuestos

- ✅ Backend: Quarkus 3.40, Hibernate ORM
- ✅ SMTP: Configurado en application.properties (ya existe)
- ✅ Frontend: Angular 21
- ✅ BD: PostgreSQL
- ✅ Permisos RBAC: Solo MASTER puede invitar usuarios

---

## Beneficios

| Beneficio | Antes | Después |
|-----------|-------|---------|
| Email contacto | ❌ No | ✅ Sí |
| Username generado | ❌ Manual | ✅ Automático |
| Invitación email | ❌ No | ✅ Automático SMTP |
| Auditoría | ❌ No | ✅ Completa |
| UX | 🔴 Confusa | 🟢 Clara |

---

## Criterios de aceptación

- [ ] Formulario "Crear Usuario" con campos: nombre, apellido, email, rol
- [ ] Username generado: {3name}{3lastname}{3role}@control.ttcs
- [ ] Temporary password: 12 chars (upper+lower+digit+special)
- [ ] Email SMTP enviado con template HTML
- [ ] Tabla user_invitations con auditoría (IP, timestamp, status)
- [ ] Response: { username, status: "invited" }
- [ ] UI mensaje: "Invitación enviada a usuario@example.com"
- [ ] Rate limiting: 1 invite/5s per IP
- [ ] Solo MASTER puede invitar

---

## Riesgos principales

| Risk | Prob | Impact | Mitigation |
|------|------|--------|-----------|
| SMTP config inválido | Low | High | Test antes (mailhog/mailtrap) |
| Username collision | Low | Med | Index único en DB |
| Email validation loose | Med | Low | RFC 5322 + regex check |
| Temp password leak | Low | Critical | Never log, use env variable |

---

## Próximo paso

→ design.md: decisiones sobre password generation, email template, activation flow

---

**Status:** Ready for design  
**Last Updated:** 2026-11-07
