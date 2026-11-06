# T-LEGAL-01: Propuesta — Privacy Policy + Terms of Use

**Date:** 2026-11-06  
**Author:** Control Electoral Team  
**Status:** Proposal  
**Priority:** CRÍTICO  
**Estimación:** 4-5 puntos → 1 sprint

---

## Qué se construye

Sistema de **Legal Compliance** (Privacidad + Términos) que permite:
- Página `/privacy` con política de privacidad (GDPR Art. 13/14)
- Página `/terms` con términos de uso (marco legal)
- Endpoint `/api/user/export-data` (GDPR Art. 15 — derecho de acceso)
- Formulario de contacto DPO (Data Protection Officer)
- Modal de aceptación de Términos en login
- Auditoría de aceptaciones

---

## Por qué es necesario

**Hoy (CRÍTICO):**
```
❌ Google Play rechaza apps sin Privacy Policy + Terms
❌ GDPR Art. 13/14: información obligatoria no documentada
❌ GDPR Art. 15: usuario NO puede acceder sus datos (violación)
❌ GDPR Art. 17: delete account existe pero SIN privacidad documentada
❌ Responsabilidad legal indefinida (sin términos)
❌ Sistema electoral CON datos sensibles = riesgo legal grave
```

**Con esta SDD:**
```
✅ Google Play: legal docs cumplimiento
✅ GDPR Art. 13/14: info completa transparente
✅ GDPR Art. 15: endpoint export-data auditable
✅ GDPR Art. 17: delete documentado en términos
✅ Responsabilidad clara en términos
✅ Auditoría de aceptaciones (prueba de consentimiento)
```

---

## Problema específico

- **Auditoría encontró:** NO existe `/privacy`, NO existe `/terms`, NO existe `usuario.accepted_terms_version`
- **Google Play requiere:** Legal docs claros y accesibles
- **GDPR requiere:** Info obligatoria, export data, auditoría de aceptación
- **Seguridad electoral:** Dónde guardar datos, cuánto tiempo, quién accede

---

## Solución de alto nivel

```
Flow Legal Compliance
├─ GET /privacy                      → HTML estático en versión control (git)
├─ GET /terms                        → HTML estático con versionado
├─ POST /api/user/accept-terms       → Audita aceptación en BD
├─ GET /api/user/export-data         → Retorna JSON completo (GDPR)
├─ POST /api/legal/contact-dpo       → Formulario contacto DPO
└─ Frontend modals
   ├─ PrivacyPage (/privacy)
   ├─ TermsPage (/terms)
   ├─ TermsAcceptanceModal (login)
   └─ ContactDpoForm

BD Changes:
├─ usuario.accepted_terms_version (String, null = nunca aceptó)
├─ usuario.accepted_terms_at (Instant)
└─ terms_acceptance_audit table
   (usuario_id, terms_version, accepted_at, ip, user_agent)
```

---

## Alcance

✅ **DENTRO:**
- Páginas `/privacy` + `/terms` públicas, responsivas
- Endpoint `/api/user/export-data` (JSON completo de user)
- Endpoint `POST /api/user/accept-terms` + auditoría
- Formulario contacto DPO
- Modal Términos en login (no permite continuar sin aceptar)
- Multi-idioma: ES + EN
- Rate limiting: 1 export/24h, contacto DPO 1/30s
- Versionado de términos (v1.0, v1.1, etc)
- GDPR audit trail completa

❌ **FUERA:**
- Cambios dinámicos de Terms via admin (estático en código = seguro)
- Anonimización de datos (futuro)
- Dinámica de "acepto o rechazo" (acepto = mandatorio para usar app)
- Cookies banner (separado T-COMPLIANCE-07)

---

## Dependencias / Supuestos

- ✅ Frontend: Angular 21, TypeScript 5
- ✅ Backend: Quarkus 3.40, Hibernate ORM
- ✅ BD: PostgreSQL con Liquibase
- ✅ Admin acepta política redactada (no genera contenido)
- ✅ Email/soporte configurado para DPO

---

## Beneficios

| Beneficio | Antes | Después |
|-----------|-------|---------|
| Google Play | ❌ Rechazo | ✅ Compliance |
| GDPR Art 13/14 | ❌ No | ✅ Sí |
| GDPR Art 15 | ❌ No export | ✅ Endpoint |
| Legal Risk | 🔴 Crítico | 🟢 Bajo |
| Auditoría | ❌ Cero | ✅ Completa |

---

## Criterios de aceptación

- [ ] Página `/privacy` accesible públicamente, responsive
- [ ] Página `/terms` con versionado (v1.0 visible)
- [ ] Endpoint `/api/user/export-data` retorna JSON completo
- [ ] Endpoint `POST /api/user/accept-terms` audita en BD
- [ ] Modal Términos aparece en login si versión cambió
- [ ] Usuario NO puede continuar sin aceptar (mandatorio)
- [ ] Formulario DPO funciona + audita
- [ ] Rate limiting: 1 export/24h, DPO 1/30s
- [ ] Multi-idioma ES + EN
- [ ] Auditoría: terms_acceptance_audit log completo

---

## Riesgos principales

| Risk | Prob | Impact | Mitigation |
|------|------|--------|-----------|
| Contenido incompleto | Medium | High | Abogado revisa antes de deploy |
| Usuario rechaza Terms | Low | High | Diseño modal: "Aceptar o Salir" |
| Export data slow | Low | Medium | Caché, índices DB |
| No audita aceptación | Low | Critical | Tests verifican registro en BD |

---

## Próximo paso

→ design.md: decisiones sobre versionado, auditoría, export format

---

**Status:** Ready for design  
**Last Updated:** 2026-11-06
