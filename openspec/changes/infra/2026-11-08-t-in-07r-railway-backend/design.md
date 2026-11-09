# Design: T-IN-07r

## D1 — Dependency removal: Testcontainers en local, Neon en Railway

**Decisión**: Mantener `spring-boot-testcontainers` para tests locales.
En Railway, `SPRING_DATASOURCE_URL` apunta a Neon.

**Alternativa descartada**: Testcontainers para todo.
- Railway no soporta Docker in Docker. Testcontainers fallaría en CI.
- Neon tiene tier free; reutilizarlo en CI/CD es más económico.

## D2 — Profile-based configuration

**Decisión**:
```
dev: compose.yaml (Postgres local + RustFS)
test: Testcontainers (Postgres ephemeral)
prod: Railway env vars (Neon + R2)
```

**Alternativa descartada**: Single config, overrides dinamicos.
- Profiles son más explícitos, menos sorpresas.

## D3 — Boot order y readiness

**Decisión**: Backend lanza, healthchecks POST-boot permiten a Railway saber
si está ready (no solo started).

**Regla**: Railway debe esperar a `/health/db` antes de rutear requests.

## D4 — Migración cero-downtime

**Decisión**: Liquibase ejecuta ON_BOOT si schema es viejo.
Neon no tiene DDL locks, pueden correr varias migraciones en paralelo
(si múltiples instancias boot simultáneamente).

**Safeguard**: Liquibase tiene `databaseChangeLogLockWaitTime = 60` segundos.
Si una migración toma >60s, la segunda espera. Aceptable para MVP.

## D5 — Error handling en startup

**Decisión**: Si Neon no responde en startup, backend falla con exit code 1.
Railway lo reintenta hasta 3 veces (configurable).

**Alternativa descartada**: Graceful degradation.
- MVP es misión crítica; sin DB, sin app.
