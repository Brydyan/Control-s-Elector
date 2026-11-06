# T-COMPLIANCE-07: Tasks — Data Transparency & Consent

**Effort:** 3-4 puntos → 1 sprint

---

## Sprint 1: Cookie Banner + Consent Service

### Task 7.1 — CookieConsentService (Frontend)

**Estimación:** 1 pt

- [ ] **C.7.1.1** — Service to manage consent
  - Methods: getConsent(), setConsent(prefs), hasAnalyticsConsent(), etc.
  - Storage: localStorage key = "cookie_consent"
  - Versioned: {version, timestamp, preferences}

- [ ] **C.7.1.1 — Mutación**
  - Remove localStorage logic
  - Consent not persisted (test fails)
  - Note: [test name]
  - Restore

### Task 7.2 — CookieBannerComponent

**Estimación:** 1 pt

- [ ] **C.7.2.1** — Banner component
  - Shows on first-visit (no localStorage)
  - Options: Accept All, Customize
  - Modal for preferences
  - Sticky positioning (bottom)

- [ ] **C.7.2.1 — Mutación**
  - Remove first-visit check
  - Banner always shows (test fails)
  - Note: [test name]
  - Restore

### Task 7.3 — Analytics Guards

**Estimación:** 1 pt

- [ ] **C.7.3.1** — Init analytics only if consented
  - Google Analytics: only if hasAnalyticsConsent()
  - Sentry: init always (errors), but custom events only if consented
  - Check: CookieConsentService.hasAnalyticsConsent()

- [ ] **C.7.3.1 — Mutación**
  - Remove consent check
  - Analytics loads without consent (test fails)
  - Note: [test name]
  - Restore

---

## Gates

```bash
npm run test -- consent.spec.ts
npm run test -- cookie-banner.spec.ts
grep -r "analytics.*consent" frontend/src/
```

**Last Updated:** 2026-11-05
