# T-COMPLIANCE-06: Tasks — Rate Limiting

**Effort:** 3 puntos → 1 sprint

---

## Sprint 1: Rate Limiting Service

### Task 6.1 — RateLimitingService

**Estimación:** 1.5 pts

- [ ] **C.6.1.1** — Create RateLimitingService
  - Redis client injection
  - Methods: checkGlobalLimit(ip), checkUserLimit(userId), checkEndpointLimit(userId, endpoint)
  - Return: {allowed: true/false, remaining: N, retryAfter: T}

- [ ] **C.6.1.1 — Mutación**
  - Remove Redis client
  - Limits always pass (test fails)
  - Note: [test name]
  - Restore

### Task 6.2 — AOP Interceptor

**Estimación:** 1 pt

- [ ] **C.6.2.1** — @RateLimited annotation
  - Apply to endpoints: export-data, contact-dpo, login
  - Automatically check limits before execution
  - Return 429 if exceeded

- [ ] **C.6.2.1 — Mutación**
  - Remove @RateLimited
  - Limits not enforced (test fails)
  - Note: [test name]
  - Restore

### Task 6.3 — Configuration

**Estimación:** 0.5 pts

- [ ] **C.6.3.1** — application.properties
  - Global: 100/60s
  - Per-user: 1000/60s
  - Per-endpoint: configurable

---

## Gates

```bash
curl -H "Authorization: Bearer <JWT>" http://localhost:8080/api/user/export-data
# Repeat 2x → second should be 429

rtk cargo test
```

**Last Updated:** 2026-11-05
