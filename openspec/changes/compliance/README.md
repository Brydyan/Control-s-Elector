# Compliance & Security Changes

SDDs para cumplimiento legal, protección de datos, y requisitos de Google Play Store.

---

## Changes Completadas

### 🔴 CRITICAL (4 items)

1. **T-COMPLIANCE-01: Privacy Policy**
   - Página `/privacy` con política de privacidad completa
   - Endpoint `/api/user/export-data` para descargas GDPR
   - Auditoría de descargas
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

2. **T-COMPLIANCE-02: Terms of Use**
   - Página `/terms` con términos de servicio
   - Modal de aceptación en login (cambios significativos)
   - Auditoría de aceptaciones
   - Versionado: v1.0, v1.1, etc.
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

3. **T-COMPLIANCE-03: Licenses & IP Attribution**
   - Página `/licenses` con all open source libraries
   - Auto-generated desde package.json
   - Assets attribution
   - Google Play compliance
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

4. **T-COMPLIANCE-04: Delete Account (GDPR Right to Forget)**
   - Soft-delete (30 días grace period) + hard-delete
   - Email de confirmación + 7 días para cancelar
   - Exceptions: actas electorales (7 años retención legal)
   - Auditoría completa
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

### 🔴 CRITICAL (1 item)

5. **T-COMPLIANCE-05: Monitoring & Crash Reporting (Sentry)**
   - Sentry backend + frontend integration
   - Error sampling: production 10%, staging 100%
   - PII scrubbing automático
   - Slack alerts para 500 errors
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

### 🟠 HIGH (3 items)

6. **T-COMPLIANCE-06: Rate Limiting**
   - Global: 100 req/min by IP
   - Per-user: 1000 req/min
   - Per-endpoint: export-data 1/24h, email 5/min, login 5/5m
   - Redis-backed
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

7. **T-COMPLIANCE-07: Data Transparency & Consent Management**
   - Cookie banner (first-visit)
   - Granular preferences: Essential, Analytics, Marketing, Preferences
   - Analytics guard: no Google Analytics sin consent
   - Settings panel para cambiar preferences
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

8. **T-COMPLIANCE-08: Backup & Disaster Recovery**
   - Daily backups (23:00 UTC)
   - Cross-region S3 replication (24h after)
   - Retention: 30 días
   - Recovery playbook: RTO < 4h, RPO < 24h
   - Quarterly tested
   - Status: Proposal ✅ → Design ✅ → Specs ✅ → Tasks ✅

---

## Estructura de Cada SDD

```
openspec/changes/compliance/2026-11-05-t-compliance-NN-[slug]/
├── proposal.md              # Qué, por qué, alcance
├── design.md                # Decisiones D1-DN con alternativas descartadas
├── specs/
│   └── [dominio].spec.md   # Escenarios Gherkin (GIVEN/WHEN/THEN)
└── tasks.md                 # Sprint breakdown con mutations y gates
```

### Convenciones

- **Design Decisions:** D1, D2, D3... (nunca sin "Alternativa descartada")
- **Mutations:** C.N.1, C.N.2 (verificación ejecutable de cada garantía)
- **Gates:** Comandos exactos a ejecutar antes de cierre de fase
- **Gherkin:** GIVEN/WHEN/THEN + escenarios reales

---

## Próximos Pasos

1. **Sprint Planning:**
   - Priorizar cuál SDD comenzar primero
   - Estimar: T-COMPLIANCE-01 = 3-4 pts (1 sprint)
   - Total portfolio: ~25 puntos (3 sprints approx)

2. **Builder Assignment:**
   - Cada SDD requiere Builder + Verifier
   - Verifier audita mutations

3. **Integration:**
   - Fusionar specs en `openspec/specs/compliance/spec.md` cuando cierren
   - Mover SDDs a `openspec/changes/archive/` cuando COMPLETADO

---

## Prioridad Recomendada

1. **T-COMPLIANCE-01** (Privacy Policy) - Google Play requirement
2. **T-COMPLIANCE-02** (Terms of Use) - Google Play requirement
3. **T-COMPLIANCE-04** (Delete Account) - GDPR artículo 17 requirement
4. **T-COMPLIANCE-05** (Monitoring/Sentry) - Production observability
5. **T-COMPLIANCE-03** (Licenses) - IP compliance
6. **T-COMPLIANCE-06** (Rate Limiting) - Security/DDoS protection
7. **T-COMPLIANCE-07** (Cookie Consent) - GDPR compliance
8. **T-COMPLIANCE-08** (Backups) - Disaster recovery (already partially done?)

---

**Status:** All 8 SDDs drafted and ready for builder assignment  
**Last Updated:** 2026-11-05
