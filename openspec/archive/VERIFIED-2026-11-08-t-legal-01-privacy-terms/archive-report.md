# Archive Report — SDD T-LEGAL-01: Privacy Policy + Terms of Use

**Date Closed:** 2026-11-08  
**Original Path:** `openspec/changes/compliance/2026-11-06-t-legal-01-privacy-terms`  
**Archived Path:** `openspec/changes/archive/VERIFIED-2026-11-08-t-legal-01-privacy-terms`  
**Status:** ✅ **VERIFIED & COMPLETE**

---

## Summary

### What Was Built

GDPR/RGPD compliance infrastructure for private PWA (control.twintailcs.xyz):

- **Legal pages:** `/privacy` + `/terms` (public, static, versioned)
- **Endpoints:** 
  - POST `/api/v1/user/accept-terms` (acceptance + audit)
  - GET `/api/v1/user/export-data` (GDPR Art. 15)
  - POST `/api/v1/legal/contact-dpo` (rate limited)
  - GET `/api/v1/legal/current-version` (version query)
- **Database:** 3 tables (terms_acceptance_audit, dpo_contact_requests, user_export_log) with audit trails
- **Frontend:** Modal trigger on login (acceptance mandatory), legal pages + contact form
- **Auth:** @PreAuthorize guards on protected endpoints
- **Rate limiting:** DPO contact 1/30s enforced

### Acceptance Criteria Met

- [x] Página `/privacy` accesible públicamente, responsive
- [x] Página `/terms` con versionado (v1.0 visible)
- [x] Endpoint `/api/user/export-data` retorna JSON completo
- [x] Endpoint `POST /api/user/accept-terms` audita en BD
- [x] Modal Términos aparece en login si versión cambió
- [x] Usuario NO puede continuar sin aceptar (mandatorio)
- [x] Formulario DPO funciona + audita
- [x] Rate limiting: 1 export/24h, DPO 1/30s
- [x] Multi-idioma ES + EN
- [x] Auditoría: terms_acceptance_audit log completo

---

## Verification Results

### Backend (Verify Round 3)

```
82 tests / 0 failures / 0 errors
JaCoCo gate: PASS (> 0.08)
Spotless format: 0 violations
BUILD SUCCESS
```

### Frontend (Verify Round 3)

```
686 tests / 0 failures (73 test files)
Lint: All files pass
Format: Prettier compliant
```

### Mutations

- M1 (rate limiter removal): Test falls ✅
- M2 (endpoint rename): Test falls ✅
- M3 (@PreAuthorize removal): Test falls ✅

### Compuertas

| Gate | Status |
|------|--------|
| Backend compile | ✅ |
| Backend tests | ✅ |
| Frontend tests | ✅ |
| Lint | ✅ |
| Format | ✅ |
| Coverage | ✅ |
| Auth guards | ✅ |
| Rate limiting | ✅ |
| DB migration | ✅ |
| Endpoints respond | ✅ |

**Final Status:** ✅ **PASS — No blockers**

---

## Technical Details

### Files Created

**Backend (22 files):**
- Domain: TermsAcceptanceAudit, DpoContactRequest, UserExportLog entities
- Web: LegalResource, UserLegalResource, DpoResource controllers
- Service: LegalService, DpoRateLimiter
- Tests: Legal ITCs, unit tests, rate limiter tests
- DB: Liquibase migration (3 tables)
- Content: privacy-{es,en}.html, terms-{es,en}.html

**Frontend (13 files):**
- Services: LegalApiService, LegalService, TermsAcceptanceService
- Components: TermsAcceptanceModalComponent
- Pages: TermsAcceptancePage, ContactDpoPage
- Tests: 8 test suites (service + component tests)

### Database Schema

```
usuario (4 new columns):
  - accepted_terms_version VARCHAR(10)
  - accepted_terms_at TIMESTAMP
  - accepted_privacy_version VARCHAR(10)
  - accepted_privacy_at TIMESTAMP

terms_acceptance_audit:
  - id BIGINT PK
  - usuario_id BIGINT FK
  - terms_version VARCHAR(10)
  - accepted_at TIMESTAMP
  - ip_address VARCHAR(45)
  - user_agent VARCHAR(500)
  - locale VARCHAR(5)
  - consent_type VARCHAR(50)

dpo_contact_requests:
  - id BIGINT PK
  - name, email, subject, message
  - submitted_at TIMESTAMP
  - ip_address VARCHAR(45)
  - status VARCHAR(50)
  - dpo_response TEXT
  - responded_at TIMESTAMP

user_export_log:
  - id BIGINT PK
  - usuario_id BIGINT FK
  - export_id VARCHAR(36)
  - exported_at TIMESTAMP
  - ip_address VARCHAR(45)
```

### Endpoints

```
GET  /privacy                       → HTML estático (pública)
GET  /terms                         → HTML estático (pública)
GET  /api/v1/legal/current-version  → { "version": "1.0" }
POST /api/v1/user/accept-terms      → Accept + audit (auth required)
GET  /api/v1/user/export-data       → GDPR export (auth required)
POST /api/v1/legal/contact-dpo      → DPO contact (rate limited)
```

### Architecture Decisions

**D1 — Versioned static content:** Terms/Privacy versioned in code (not admin UI). Git-managed, lawyer sign-off in PR.

**D2 — Static HTML pages:** Not database-driven. Faster serving, version control, compliant.

**D3 — Separation of concerns:** Public legal endpoints (no auth), API endpoints versioned (`/api/v1/`), acceptance audited in DB.

---

## Outstanding Items

### E.1 — Legal content review

HTML templates are stubs. **Awaiting legal team review + sign-off before production deployment.**

This is out of SDD scope — content review is a product decision, not an engineering task.

### E.2 — E2E flow testing

Playwright E2E test created (j6-legal-terms.spec.ts) but out of unit SDD scope. Can be run separately in E2E suite.

---

## Lessons Learned

**Three rounds of verification caught integration issues:**

1. **Round 1:** Entity property mapping incomplete (Liquibase migration vs JPA entity mismatch)
2. **Round 2:** Frontend service/component incomplete (AuthStore methods missing, modal button not rendering)
3. **Round 3:** All fixed. Full gates green.

**Key insight:** Database + service layer must be coordinated. Liquibase creates schema, JPA entity must match. Service calls must have store support. Component templates must render.

---

## Next Steps for Product

1. **Legal review:** Have lawyer review privacy-{es,en}.html and terms-{es,en}.html stubs
2. **Content finalization:** Update stubs with actual legal text + dates
3. **Deployment:** Push to production after content approval
4. **Monitoring:** Watch terms_acceptance_audit table for acceptance patterns
5. **Versioning:** When terms change, increment version in config (v1.1, v2.0, etc.) and re-collect acceptances

---

## Decision: Archive Complete

- [x] All compuertas pass
- [x] No blockers, no fixes-required.md
- [x] Mutations verify guards work
- [x] Coverage gates OK
- [x] Ready for next SDD

**Status:** Moved to `openspec/changes/archive/` with full history preserved.

---

**Archived:** 2026-11-08 by Verify agent  
**Next:** SDD #492 (User Invitations) ready to start (independent, parallelizable)
