# T-COMPLIANCE-06: Design — Rate Limiting

**Date:** 2026-11-05

---

## D1 — Límites: Global vs Per-user vs Per-endpoint

**Decisión: Todos los tres**

```
Global (by IP):
  - 100 requests / 60 segundos
  
Per-user:
  - 1000 requests / 60 segundos (authenticated)
  
Per-endpoint:
  - GET /api/user/export-data: 1/24h
  - POST /api/legal/contact-dpo: 1/30s
  - POST /api/auth/login: 5/5m (prevent brute force)
```

---

## D2 — Storage: Redis vs In-Memory

**Decisión: Redis (distributed, survives server restart)**

```
Key pattern: "rate_limit:{type}:{identifier}:{endpoint}"
Value: count
TTL: window duration

Ejemplos:
  - rate_limit:global:192.168.1.1:/api/users
  - rate_limit:user:123:/api/export-data
```

---

**Next:** specs, tasks  
**Last Updated:** 2026-11-05
