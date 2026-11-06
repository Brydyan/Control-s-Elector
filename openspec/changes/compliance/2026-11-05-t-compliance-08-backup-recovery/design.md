# T-COMPLIANCE-08: Design — Backup & Disaster Recovery

**Date:** 2026-11-05

---

## D1 — Frequency: Daily vs Hourly vs Continuous

**Decisión: Daily (11 PM UTC) + event-triggered (app startup)**

```
Schedule:
  - 23:00 UTC: full DB backup
  - Daily: incremental backup
  - App startup: quick health check of latest backup
  
Razón: Daily es estándar, costs controlados
```

---

## D2 — Storage: S3 local vs Cross-region vs OnPrem

**Decisión: S3 local + Cross-region copy (24h after)**

```
Primary backup: S3 [primary region]
Secondary backup: S3 [secondary region] (24h delay)

Razón: RPO 24h acceptable, protege regional disaster
```

---

## D3 — Retention: 7 días vs 30 días vs 1 año

**Decisión: 30 días (compliance window)**

```
Backups más viejo que 30 días → auto-delete
Razón: GDPR compliance, costs
```

---

## D4 — Recovery Plan: Documented vs Tested

**Decisión: Documentado + tested quarterly**

```
Recovery playbook: docs/DISASTER_RECOVERY.md
- RTO: 4 horas
- RPO: 24 horas
- Step-by-step: restore from S3, verify data, test login, run smoke tests
- Quarterly test run (simulated failure)
```

---

**Next:** specs, tasks  
**Last Updated:** 2026-11-05
