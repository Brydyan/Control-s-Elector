# T-COMPLIANCE-01: Propuesta — Privacy Policy Page (Política de Privacidad)

**Date:** 2026-11-05  
**Author:** Control Electoral Team  
**Status:** Proposal  
**Priority:** CRITICAL  
**Estimación:** 3-4 puntos → 1 sprint

---

## Qué se construye

Una **página estática de Política de Privacidad (Privacy Policy)** que cumple con:
- **GDPR** (Regulación General de Protección de Datos - UE)
- **Google Play Store requirements** (Play Console compliance)
- **CCPA/LGPD** (California Consumer Privacy Act / Lei Geral de Proteção de Dados - Brasil)
- Legislación electoral local

## Por qué es necesario

**Hoy:**
```
❌ Sin política de privacidad documentada
❌ Google Play rechaza apps sin Privacy Policy clara
❌ Usuario no sabe qué datos se recopilan
❌ Exposición legal: GDPR fines hasta 4% de ingresos globales
❌ No hay transparencia sobre retención de datos
```

**Con Privacy Policy:**
```
✅ Página /privacy documentada en frontend
✅ Google Play compliant
✅ GDPR checklist: consentimiento, derechos, contacto de DPO
✅ Usuario puede descargar copia de sus datos
✅ Claridad en recopilación y retención
```

## Problema específico

- No existe página `/privacy` que explique qué datos se recopilan
- Usuario no tiene forma de acceder a su política de privacidad
- Google Play Store rechaza versiones sin Privacy Policy clara
- No hay transparencia sobre cookies, tracking, o compartición de datos
- Falta información de contacto para Data Protection Officer (DPO)

## Solución de alto nivel

```
Flow 1: Frontend - Privacy Page
/privacy → Página estática con:
  - Qué datos se recopilan (email, nombre, rol, IP, timestamps)
  - Por qué se recopilan (autenticación, auditoría, mejora de servicio)
  - Cuánto tiempo se retienen (180 días inactivos, 7 años actas electorales)
  - Quién tiene acceso (MASTER, sistema, auditoría)
  - Derechos de usuario (acceso, portabilidad, olvido, rectificación)
  - Cómo contactar DPO (email dedicado)
  - Link a Terms of Use
  - Footer con "Última actualización: [fecha]"

Flow 2: Backend - /api/user/export-data
  - Endpoint que permite usuario descargar su data en JSON
  - Incluye: perfil, login history, auditoría de acciones
  - Formato: data.json, descargable vía browser

Flow 3: Auditoría
  - Cada descarga de data logged en audit_trail
  - MASTER ve quién descargó y cuándo
```

## Alcance

✅ **DENTRO:**
- Página `/privacy` con contenido completo en HTML/Markdown
- Secciones: recopilación, retención, derechos, contacto
- Responsive design (mobile, tablet, desktop)
- Multiidioma: español (principal) + inglés
- Backend endpoint `/api/user/export-data` (JSON download)
- Auditoría de descargas en audit_trail
- Link a Privacy Policy desde footer global
- GDPR, CCPA, LGPD references
- Google Play compliance checklist

❌ **FUERA DE ALCANCE:**
- Cookie banner (consentimiento inicial) → T-COMPLIANCE-07
- 2FA en descarga de datos (futuro, MVP sin 2FA)
- Anonimización de datos (futuro)
- Integración con DPO externo (manual process)
- Traducción a idiomas adicionales (futuro)
- Chatbot de soporte para preguntas de privacidad (futuro)

## Dependencias / Supuestos

- ✅ Frontend: Angular 19
- ✅ Backend: Quarkus + Hibernate/Panache
- ✅ BD: PostgreSQL con auditoría existente
- ✅ Admin puede gestionar contenido (o es estático para MVP)
- ✅ Usuario autenticado puede descargar su data

## Beneficios

| Beneficio | Antes | Después |
|-----------|-------|---------|
| Google Play compliance | ❌ No | ✅ Sí |
| GDPR compliance | ❌ No | ✅ Sí (Level 3 de 4) |
| User transparency | ❌ Cero | ✅ Completa |
| Data export capability | ❌ No | ✅ Sí (JSON download) |
| Legal risk | 🔴 Alto | 🟢 Bajo |

## Criterios de aceptación

- [ ] Página `/privacy` accesible y públicamente visible
- [ ] Contiene: qué datos, por qué, cuánto tiempo, quién accede, derechos
- [ ] Endpoint `/api/user/export-data` retorna JSON con todos los datos del usuario
- [ ] Descarga auditada en audit_trail (usuario_id, fecha, acción: "DATA_EXPORT")
- [ ] Mobile responsive (< 768px = single column)
- [ ] Link a privacy desde footer en todas las páginas
- [ ] Última actualización timestamp visible
- [ ] Google Play checklist: ✅ Privacy Policy clear and complete
- [ ] GDPR rights listed: acceso, portabilidad, olvido, rectificación

## Riesgos principales

| Risk | Prob | Impact | Mitigation |
|------|------|--------|-----------|
| Privacy Policy demasiado técnico | High | Medium | Plain language, ejemplos, diagrama |
| Usuario no encuentra página | Medium | High | Link prominente en footer + menu principal |
| Export data exposición seguridad | Low | Critical | HTTPS only, rate limiting, audit |
| Incumplimiento GDPR detectado post-launch | Medium | Critical | Pre-launch audit con abogado |
| Traducción incompleta o incorrecta | Medium | Medium | QA review de traducción |

## Secuencia recomendada

1. **Sprint 1:** Frontend Privacy page (HTML/Markdown) + responsive design
2. **Sprint 1:** Backend export-data endpoint + audit logging
3. **Sprint 1:** Integration, testing, pre-launch audit

## Próximo paso

→ design.md: documentar decisiones sobre contenido, recopilación de datos, y retención

---

**Status:** Ready for design phase  
**Last Updated:** 2026-11-05
