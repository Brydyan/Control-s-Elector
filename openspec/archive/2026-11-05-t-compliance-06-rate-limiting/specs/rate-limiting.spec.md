# Specs: T-COMPLIANCE-06 — Rate Limiting

**Last Updated:** 2026-11-05

---

## Feature: Rate Limiting

```gherkin
Feature: API endpoints están rate-limited

  Scenario: Global rate limit 100/min by IP
    GIVEN cliente en IP 192.168.1.1
    WHEN envía 100 requests en 60s
    THEN todos exitosos (200)
    AND request #101 rechazado: 429 Too Many Requests
    AND header: Retry-After: 60

  Scenario: Per-user limit 1000/min
    GIVEN usuario autenticado
    WHEN envía 1000 requests en 60s
    THEN todos exitosos
    AND request #1001 rechazado: 429

  Scenario: Export-data limit 1/24h
    GIVEN usuario
    WHEN descarga datos a las 10:00
    AND intenta descargar again a las 10:01
    THEN segundo rechazado: 429
    AND header: Retry-After: 86400

  Scenario: Login brute force prevention
    GIVEN atacante
    WHEN intenta login 6 veces en 5 minutos
    THEN primeros 5 exitosos (fail/success)
    AND request #6 rechazado: 429
```

---

**Total Scenarios:** 4  
**Last Updated:** 2026-11-05
