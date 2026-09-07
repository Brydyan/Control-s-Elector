# Design — T-IN-08: application-prod.properties

## D1 — JPA ddl-auto

**Decisión:** validate (no update). Liquibase manage schema, JPA solo valida.

**Alternativa descartada — ddl-auto=update.** Peligroso en prod. JPA puede hacer cambios no deseados.

## D2 — CORS allowedOrigins

**Decisión:** `https://twintailcs.xyz,https://control.twintailcs.xyz,https://www.twintailcs.xyz` (sin http://).

**Alternativa descartada — wildcard.** Menos seguro. Explícito es mejor.

## D3 — Logging level

**Decisión:** root logger WARN, Spring Boot INFO (logs esenciales).

**Alternativa descartada — DEBUG.** Verbose, ralentiza prod.

## D4 — HTTPS redirect

**Decisión:** Spring Security `requiresSecure()` en profile prod.

**Alternativa descartada — nginx layer.** Spring lo puede hacer, más simple.
