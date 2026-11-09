# T-IN-13: Neon Database Setup para MVP

## El problema

MVP necesita PostgreSQL en cloud (production-ready).
Hoy: compose.yaml levanta Postgres en local.
Producción: Neon (serverless postgres con autoscaling).

## Alcance

✅ Neon project creation + VPC endpoint (secure)
✅ Database schema: Liquibase migrations ejecutadas contra Neon
✅ Backup strategy: automated daily snapshots (Neon built-in)
✅ Health check endpoint: `/api/v1/health/db` valida conexión
✅ Connection pooling: verificado contra producción

❌ Replicación multi-región (post-MVP)
❌ Read replicas (post-MVP, decide usuario)
❌ Row-Level Security (ya en code, solo verificar)

## Razón de criticidad

**CRÍTICO**: Sin Neon, las migraciones Liquibase no se pueden ejecutar.
Sin migrados, no hay schema. Sin schema, T-IN-07r no levanta.
