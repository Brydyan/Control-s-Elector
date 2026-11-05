# T-COMPLIANCE-02: Tasks — Terms of Use

**Effort:** 3-4 puntos → 1 sprint  
**Depends on:** design.md, specs/terms.spec.md

---

## Sprint 1: Terms Page + Acceptance Tracking

### Task 2.1 — Terms of Use Page (Frontend)

**Estimación:** 1.5 pts

- [ ] **C.2.1.1** — TermsOfUseComponent
  - Path: `frontend/src/app/legal/terms-of-use/`
  - Route: `/terms` (public)
  - Multiidioma: Accept-Language detection

- [ ] **C.2.1.1 — Mutación**
  - Quitá route
  - Test debe fallar
  - Anotá: [test name]
  - Restaurá

- [ ] **C.2.1.2** — Responsive + Table of Contents
  - TOC con jump links
  - Mobile: collapsible sections
  - Desktop: side navigation

- [ ] **C.2.1.2 — Mutación**
  - Quitá media query mobile
  - Layout breaks
  - Anotá: [test name]
  - Restaurá

- [ ] **C.2.1.3** — Version & Effective Date display
  - Source: environment.ts (`TERMS_VERSION = "1.0"`)
  - Format: "v1.0 - Effective: 5 de noviembre de 2026"
  - Localized date (español/inglés)

- [ ] **C.2.1.3 — Mutación**
  - Cambió version a "0.0"
  - Snapshot test debe fallar
  - Anotá: [test name]
  - Restaurá

---

### Task 2.2 — Terms Acceptance Modal (Frontend)

**Estimación:** 1 pt

- [ ] **C.2.2.1** — TermsAcceptanceModalComponent
  - Trigger: after login, si user.terms_version < TERMS_VERSION
  - Modal con:
    - Resumen de cambios
    - Botón "Leer Términos Completos" → /terms
    - Botón "Aceptar y Continuar"
    - Scroll progress bar (botón disable hasta scroll to bottom)

- [ ] **C.2.2.1 — Mutación**
  - Quitá scroll-to-bottom validation
  - Test: botón habilitado immediato (debe fallar)
  - Anotá: [test name]
  - Restaurá

- [ ] **C.2.2.2** — Storage of acceptance
  - LocalStorage: `terms_acceptance = {version: "1.1", timestamp: "..."}`
  - Backend sync: POST /api/legal/accept-terms

- [ ] **C.2.2.2 — Mutación**
  - Quitá localStorage.setItem
  - LocalStorage vacío después de acceptance
  - Test debe fallar
  - Anotá: [test name]
  - Restaurá

---

### Task 2.3 — Backend: Terms Acceptance Tracking

**Estimación:** 1 pt

- [ ] **C.2.3.1** — POST /api/legal/accept-terms
  - Input: { version: "1.1" }
  - Update: usuario.terms_version = "1.1"
  - Audit: terms_acceptance_audit row

- [ ] **C.2.3.1 — Mutación**
  - Quitá UPDATE usuario.terms_version
  - Query: SELECT terms_version FROM usuario WHERE id = X
    debe ser "1.0" (no "1.1")
  - Test debe fallar
  - Anotá: [test name]
  - Restaurá

- [ ] **C.2.3.2** — terms_acceptance_audit table (DB migration)
  - Migration: `029-terms-acceptance-audit.sql`
  - Columns: id, usuario_id, terms_version, accepted_at, ip, user_agent
  - Indexes: usuario_id, terms_version

- [ ] **C.2.3.2 — Mutación**
  - Run migration
  - DELETE FROM terms_acceptance_audit WHERE usuario_id = X
  - Audit should be empty
  - Test debe fallar
  - Anotá: [test name]
  - Restaurá

---

### Task 2.4 — Terms Assets (Markdown files)

**Estimación:** 0.5 pts

- [ ] **C.2.4.1** — terms-of-use-es.md
  - Path: `frontend/src/assets/legal/terms-of-use-es.md`
  - Sections (required):
    - Uso Aceptable
    - Prohibiciones (con ejemplos)
    - Responsabilidad Usuario
    - Limitación Responsabilidad (cap = pagado)
    - IP Rights
    - Warranty Disclaimer (AS-IS + backups + uptime SLA)
    - Cambios en Términos (30 days notice)
    - Governing Law + Disputes (local + EU exception)
    - Contacto

- [ ] **C.2.4.1 — Mutación**
  - Quitá "Limitación Responsabilidad" section
  - Markdown parser test debe fallar
  - Anotá: [test name]
  - Restaurá

- [ ] **C.2.4.2** — terms-of-use-en.md
  - Traducción de es.md
  - Same structure

- [ ] **C.2.4.2 — Mutación**
  - Quitá "Governing Law"
  - Parser test debe fallar
  - Anotá: [test name]
  - Restaurá

---

## Qué NO hacer

- ❌ Arbitration-only disputes (GDPR requires court option)
- ❌ Terms acceptance token (MVP = simple flag)
- ❌ Automatic tracking acceptance via UI interaction (only explicit button)
- ❌ Signature required (digital signature → futuro)

## Compuertas

```bash
# 1. Frontend builds
npm run build --prod

# 2. Tests pass
npm run test -- --code-coverage

# 3. Backend builds
rtk cargo build

# 4. Endpoint works
curl -H "Authorization: Bearer <JWT>" -X POST http://localhost:8080/api/legal/accept-terms \
  -H "Content-Type: application/json" -d '{"version": "1.0"}'

# 5. DB migration runs
liquibase --url=jdbc:postgresql://localhost:5432/control_electoral update

# 6. Terms version in environment.ts
grep "TERMS_VERSION" frontend/src/environments/

# 7. No version mismatch
# Dev: 1.0, Prod: 1.0 (same version deployed)
```

---

**Last Updated:** 2026-11-05
