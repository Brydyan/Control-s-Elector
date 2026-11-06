# T-COMPLIANCE-05: Tasks — Monitoring & Crash Reporting

**Effort:** 3-4 puntos → 1 sprint

---

## Sprint 1: Sentry Integration

### Task 5.1 — Backend Sentry Integration

**Estimación:** 1.5 pts

- [ ] **C.5.1.1** — Add dependency
  - `build.gradle`: `implementation 'io.sentry:sentry-quarkus:latest'`
  - `application.properties`: `quarkus.sentry.dsn = <SENTRY_DSN>`

- [ ] **C.5.1.1 — Mutación**
  - Remove DSN
  - Errors not captured (test fails)
  - Note: [test name]
  - Restore

- [ ] **C.5.1.2** — PII scrubbing rules
  - File: `src/main/resources/sentry-scrubbing.json`
  - Redact: email, password, token, Authorization header
  - Keep: user_id, timestamp, endpoint

- [ ] **C.5.1.2 — Mutación**
  - Comment out email redaction
  - Email should leak (test checks, must fail)
  - Note: [test name]
  - Restore

---

### Task 5.2 — Frontend Sentry Integration

**Estimación:** 1 pt

- [ ] **C.5.2.1** — Angular Sentry SDK
  - `npm install @sentry/angular`
  - Initialize in `main.ts`
  - DSN from environment

- [ ] **C.5.2.1 — Mutación**
  - Remove init code
  - Errors not captured (test fails)
  - Note: [test name]
  - Restore

---

### Task 5.3 — Slack Integration

**Estimación:** 0.5 pts

- [ ] **C.5.3.1** — Sentry alert rule
  - Slack channel: #alerts
  - Trigger: errors with level >= error
  - Message: "[{environment}] {error.type}: {error.message}"

---

## Gates

```bash
# Check DSN configured
grep "quarkus.sentry.dsn" application.properties

# Frontend Sentry init
grep -r "Sentry.init" frontend/src/main.ts

# Tests pass
rtk cargo test
npm run test
```

**Last Updated:** 2026-11-05
