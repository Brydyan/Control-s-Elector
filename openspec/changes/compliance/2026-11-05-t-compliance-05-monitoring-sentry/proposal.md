# T-COMPLIANCE-05: Propuesta — Monitoring & Crash Reporting (Sentry)

**Date:** 2026-11-05  
**Priority:** CRITICAL  
**Estimación:** 3-4 puntos → 1 sprint

---

## Qué se construye

Sistema de error tracking con Sentry que:
- Captura crashes/errors en frontend + backend
- Audita eventos críticos (login failures, data access, etc)
- Alerts en Slack para errores críticos
- Privacy: no captura PII (emails, passwords, sensitive data)
- GDPR compliant

## Por qué es necesario

- Google Play: monitoring + crash reporting required
- Production ops: sabemos cuándo falla algo
- Debugging: stack trace auditables
- Legal: auditoría de eventos críticos

## Alcance

✅ DENTRO:
- Sentry backend integration (Java/Quarkus)
- Sentry frontend integration (Angular)
- Error sampling + rate limiting
- PII scrubbing (no emails, passwords)
- Custom events: login success/failure, data access
- Slack alerts
- Dashboard visuales

❌ FUERA:
- Custom monitoring platform (use Sentry)
- Analytics (Mixpanel/GA) → futuro
- APM (Application Performance Monitoring) → futuro

---

**Status:** Ready for design  
**Last Updated:** 2026-11-05
