# T-AUTH-INV-01: Propuesta — Sistema de Invitaciones de Usuario por Email

**Date:** 2026-11-05  
**Author:** Control Electoral Team  
**Status:** Proposal (antes de escribir design.md)  
**Priority:** Alta  
**Estimación:** 8-12 puntos → 2-3 sprints

---

## Qué se construye

Un flujo de **invitación por email con activación self-service** que permite al usuario MASTER:

1. **Invitar usuarios en bulk** (500 observadores en minutos, no 2-3 horas)
2. Cada usuario recibe **email con link de activación** (24h expiry)
3. Usuario **elige su propia contraseña** (no temporal copiable)
4. Usuario se activa y puede **loguear inmediatamente**
5. **Auditoría completa**: quién invitó, cuándo, cuándo activó

## Por qué es necesario

**Hoy:**
```
MASTER crea 500 usuarios manualmente:
  User 1: crear → copiar password temporal → enviar por WhatsApp
  User 2: crear → copiar password temporal → enviar por WhatsApp
  ...
  User 500: crear → copiar password temporal → enviar por WhatsApp
  Total: 2-3 horas de trabajo tedioso, sin auditoría, sin escalabilidad
```

**Con invitaciones:**
```
MASTER: selecciona 500 usuarios → clica "Enviar Invitación"
Backend: genera tokens, envía emails (async, rate-limited)
Usuarios: reciben email, eligen contraseña, están activos
Total: 5-10 minutos, auditable, escalable, seguro
```

## Problema específico

- MASTER no tiene forma de invitar usuarios en bulk
- No hay método de email para alertar al usuario de su new account
- Usuarios no pueden elegir su password (copian temporal del admin)
- No hay auditoría de quién activó y cuándo
- No escala para 500+ usuarios

## Solución de alto nivel

```
Flow 1: Invitación
MASTER en admin panel → selecciona usuarios (multi-select)
→ clica "Enviar Invitación"
→ Backend genera JWT token (24h expiry) por usuario
→ Envía emails async (rate-limited: 100/min, retry 3x)
→ Muestra progreso en UI

Flow 2: Activación
Usuario recibe email → clica link → valida token
→ Forma contraseña (8+ chars, mayúscula, número, especial)
→ Confirma contraseña
→ Backend: actualiza usuario a ACTIVO, consume token
→ Pantalla éxito: "Tu cuenta está lista, loguéate"
→ Redirect a login
```

## Alcance

✅ **DENTRO:**
- Email-based invitations con JWT tokens 24h
- Self-service password setup (usuario elige)
- Bulk invite desde admin panel
- Async email con retry (3x exponential backoff)
- Rate limiting en email send (100/min)
- Estado usuario: PENDIENTE_INVITACION → ACTIVO
- Auditoría: invitation_audit table
- Validación: token expiry, one-time use, password requirements
- Frontend: ActivateUserComponent con password checklist
- Admin UI: multi-select + bulk actions

❌ **FUERA DE ALCANCE:**
- SMS invitations (solo email)
- 2FA en activación (futuro)
- Password reset flow (separate feature)
- Email template editor (fixed templates)
- SendGrid integration (abstracted, puede venir después)
- Social login (separate)
- Resend automático si token expira (MASTER re-envía manualmente)
- Bulk import desde CSV (futuro)
- Invitation analytics (futuro)
- Calendar invite (futuro)

## Dependencias / Supuestos

- ✅ Backend: Quarkus + Hibernate/Panache
- ✅ BD: PostgreSQL con migraciones Liquibase
- ✅ Frontend: Angular 19 con reactive forms
- ✅ Email: SMTP disponible (o SendGrid, abstracted)
- ✅ JWT library: Quarkus JWT RBAC
- ✅ Admin panel existe (agregar UI para bulk invite)

## Beneficios

| Beneficio | Antes | Después |
|-----------|-------|---------|
| Tiempo crear 500 usuarios | 2-3 horas | 5-10 minutos |
| Auditoría | ❌ No | ✅ Sí (invitation_audit) |
| Seguridad password | Copia/pega visible | ✅ User chooses |
| Escalabilidad | No (manual) | ✅ Sí (async, batch) |
| Error handling | Frágil | ✅ Retry + clear feedback |

## Criterios de aceptación

- [ ] Usuarios pueden ser invitados en bulk (50, 100, 500)
- [ ] Cada invitación genera email con link único
- [ ] Link expira en 24h
- [ ] Usuario ve password requirements en tiempo real
- [ ] Usuario no puede loguear hasta activar
- [ ] Token no reutilizable (consumed)
- [ ] Auditoría: 100% de eventos logged
- [ ] No failures > 10% en k6 pico test (100 VUs simultáneos)
- [ ] p95 latency < 5s, p99 < 8s

## Riesgos principales

| Risk | Prob | Impact | Mitigation |
|------|------|--------|-----------|
| Email delivery fails | Medium | High | Retry logic, audit trail, admin retry UI |
| Token collision | Very Low | Critical | UUID v4, unique constraint in DB |
| Rate limiting too strict | Medium | Medium | Monitor, adjust based on load test |
| Password validation confusing | Medium | Low | Clear checklist UI, tooltips |
| Mass invite spam flagged | Low | High | Rate limiting, proper headers, DKIM |

## Secuencia recomendada

1. **Sprint 1:** DB schema + backend services (token, email, validation)
2. **Sprint 2:** Use cases + REST endpoints
3. **Sprint 3:** Frontend component + admin UI
4. **Sprint 4:** Testing + SMTP config + load testing

## Próximo paso

→ design.md: documentar decisiones (D1, D2, D3...) y alternativas descartadas

---

**Status:** Ready for design phase  
**Last Updated:** 2026-11-05
