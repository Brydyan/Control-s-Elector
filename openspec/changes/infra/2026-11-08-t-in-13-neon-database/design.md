# Design: T-IN-13

## D1 — Serverless vs Dedicated Postgres

**Decisión**: Neon serverless con autoscaling.

**Alternativa descartada**: Dedicated PostgreSQL en AWS RDS.
- RDS: costo fijo ~$15–50/mes. MVP no lo justifica.
- Neon: $0 tier gratis hasta 3 GB almacenamiento. Escalable.
- Neon soporta todas las extensiones que Control Electoral usa (uuid-ossp, ltree).

## D2 — VPC Endpoint (seguridad)

**Decisión**: Neon VPC endpoint + IP whitelist Railway (una vez que IP sea fija).

**Alternativa descartada**: IP whitelist abierta (0.0.0.0/0).
- Neon soporta VPC endpoints pero Railway IP es dinámica en el plan free.
- Solución intermedia: IP whitelist temporal Railway hasta que migren a plan paid.

## D3 — Liquibase idempotencia

**Decisión**: Verificar que TODAS las migraciones en `changelog-master.yaml`
son idempotentes (describen qué DEBE existir, no qué CAMBIAR).

**Regla**: cada migration (`NNN-MM-*.sql`) lleva `-- rollback` comentado.

**Alternativa descartada**: reescribir migraciones. Ya están bien, solo validar.

## D4 — Backups

**Decisión**: Neon automated daily snapshots (built-in, gratis en tier gratuito).
User puede hacer PITR (point-in-time recovery) desde Neon console.

**Alternativa descartada**: pg_dump + S3.
- Neon backups son suficientes para MVP.
- Automatización cron se añade post-MVP con T-OBS-02 (backups).

## D5 — Connection pooling

**Decisión**: Quarkus DataSource pooling (HikariCP).
Verificar que MaxPoolSize ≤ Neon connection limit (100 conns en tier free).

**Alternativa descartada**: PgBouncer (add complexity).
