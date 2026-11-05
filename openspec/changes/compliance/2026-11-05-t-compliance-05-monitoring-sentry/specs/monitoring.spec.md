# Specs: T-COMPLIANCE-05 — Monitoring & Crash Reporting

**Last Updated:** 2026-11-05

---

## Feature: Error Capturing

```gherkin
Feature: Errores son capturados en Sentry

  Scenario: 500 error captured
    GIVEN endpoint falla con 500
    WHEN error ocurre
    THEN Sentry captura:
      - error type, message, stack trace
      - timestamp, endpoint, HTTP method
      - user_id (no email, no PII)
      - environment (prod/staging)

  Scenario: Frontend error captured
    GIVEN Angular app crashes
    WHEN uncaught exception
    THEN Sentry captura: stack trace, browser, OS

  Scenario: PII no capturado
    GIVEN request contiene: email, password
    WHEN error ocurre
    THEN Sentry NO captura email/password
    AND request body redacted

  Scenario: Slack alert en error crítico (500)
    GIVEN error 500 ocurre
    WHEN Sentry procesa
    THEN Slack message a #alerts:
      - "[PROD] 500 error: NullPointerException en UserService.java:123"
      - "Affected users: 5"
      - Link a Sentry dashboard

  Scenario: Sampling en production (10%)
    GIVEN 1000 similar errors en producción
    WHEN todos ocurren
    THEN Sentry captura ~100 (10% sample)
    AND reporte: "1000 similar errors, showing 100 samples"
```

---

**Total Scenarios:** 5  
**Last Updated:** 2026-11-05
