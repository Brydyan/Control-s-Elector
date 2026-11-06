# T-COMPLIANCE-05: Design — Monitoring & Crash Reporting

**Date:** 2026-11-05

---

## D1 — Error sampling: 100% vs 10% vs Dynamic

**Decisión: Dynamic (production 10%, staging 100%)**

```
if (environment === "production"):
  sample_rate = 0.10  # 10% of errors captured
else:
  sample_rate = 1.0   # 100% in dev/staging

Razón: Production volume alto, costs, but keep 10% statistically significant
```

---

## D2 — PII scrubbing: Automatic vs Whitelist

**Decisión: Automatic scrubbing + custom rules**

```
Sentry redacts automatically:
  - email, password, token, authorization headers
  - credit card numbers (if any)
  
Custom rules:
  - usuario_id: KEEP (not PII for us)
  - rol: KEEP (not sensitive)
  - login IP: scrub last octet
```

---

## D3 — Alerts: Email vs Slack vs PagerDuty

**Decisión: Slack (real-time) + Email digest (daily)**

```
Critical errors (500) → Slack immediately
Other errors → daily digest email
Rate limiting: max 50 alerts/hour
```

---

**Next:** specs, tasks  
**Last Updated:** 2026-11-05
