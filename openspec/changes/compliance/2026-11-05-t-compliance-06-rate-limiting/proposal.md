# T-COMPLIANCE-06: Propuesta — Rate Limiting Infrastructure

**Date:** 2026-11-05  
**Priority:** HIGH  
**Estimación:** 3 puntos → 1 sprint

---

## Qué se construye

Sistema de rate limiting que:
- Previene abuso de API (spam, DoS)
- Protege recursos (email, data export)
- Cumple Play Store requirements
- Configurable por endpoint + usuario

## Alcance

✅ DENTRO:
- Global rate limit (100 req/min per IP)
- Per-user limits (1000 req/min)
- Per-endpoint limits (export-data 1/24h, email 5/min)
- Redis-backed
- Exponential backoff retry advice

❌ FUERA:
- DDoS mitigation (WAF) → futuro
- Advanced ML-based abuse detection → futuro

---

**Status:** Ready for design  
**Last Updated:** 2026-11-05
