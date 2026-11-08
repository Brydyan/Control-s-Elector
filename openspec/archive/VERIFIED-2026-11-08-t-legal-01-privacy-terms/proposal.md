# T-LEGAL-01: Propuesta — Privacy Policy + Terms of Use (PWA Privada)

**Date:** 2026-11-06  
**Author:** Control Electoral Team  
**Status:** Proposal  
**Priority:** 🟡 ALTO  
**Estimación:** 13 story points  
**Contexto:** PWA privada (control.twintailcs.xyz). Sin publicación en Play Store ni GitHub público.

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

**Hoy (riesgos legales reales):**
```
❌ GDPR/RGPD Art. 13/14: información obligatoria NO documentada
❌ GDPR/RGPD Art. 15: usuario NO puede acceder sus datos (violación)
❌ GDPR/RGPD Art. 17: delete account existe pero SIN términos claros
❌ Datos electorales SENSIBLES: sin política de retención/acceso
❌ Responsabilidad legal INDEFINIDA (sin términos: qué se guarda, por cuánto)
❌ Auditoría de consentimiento CERO (no hay rastro de aceptaciones)
❌ Sistema CON datos de veedores: nombres, emails, JRVs, observaciones
```

**Con esta SDD:**
```
✅ GDPR/RGPD Art. 13/14: info legal completa y transparente
✅ GDPR/RGPD Art. 15: endpoint export-data auditable
✅ GDPR/RGPD Art. 17: términos claros + procedimiento delete
✅ Retención definida: datos electorales, tiempo de conservación
✅ Responsabilidad clara: qué se guarda, quién accede, por cuánto tiempo
✅ Auditoría completa: rastro de quién aceptó qué, cuándo, desde dónde
✅ Cumplimiento GDPR/RGPD: si inspección, tienes documentación
```

---

## Problema específico

- **Auditoría encontró:** NO existe `/privacy`, NO existe `/terms`, NO existe `usuario.accepted_terms_version`
- **GDPR/RGPD requiere:** Info obligatoria (Art. 13/14), export data (Art. 15), auditoría de aceptación
- **Seguridad electoral:** Sistema maneja datos de veedores, JRVs, observaciones. **Debe haber política clara:**
  - Qué datos se guardan
  - Por cuánto tiempo se conservan  
  - Quién puede acceder
  - Cómo se protegen datos sensibles (género, zona, observaciones)
- **Auditoría:** Sin rastro de aceptaciones, no hay prueba de consentimiento informado

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
- ✅ ODD #478 (CORS) completado ← desbloquea este SDD
- ✅ Admin/Abogado revisa y acepta política antes de deploy (contenido estático en código)
- ✅ Email/soporte configurado para DPO
- **NO es requisito:** Publicación en Play Store (proyecto privado PWA)

---

## Beneficios

| Beneficio | Antes | Después |
|-----------|-------|---------|
| GDPR Art. 13/14 (Información obligatoria) | ❌ No | ✅ Sí (Privacy + Terms) |
| GDPR Art. 15 (Data Export) | ❌ No export | ✅ Endpoint JSON |
| GDPR Art. 17 (Delete Account) | ❌ Sin procedimiento | ✅ Documentado en términos |
| Rastro de aceptaciones | ❌ Cero | ✅ Auditoría completa (BD) |
| Riesgo legal (inspección GDPR) | 🔴 Crítico | 🟢 Bajo (documentado) |
| Responsabilidad de datos | ❌ Indefinida | ✅ Clara en términos |

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
