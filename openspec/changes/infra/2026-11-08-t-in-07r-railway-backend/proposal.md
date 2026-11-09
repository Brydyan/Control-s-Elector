# T-IN-07r: Railway Backend Refactor (Neon + R2)

## El problema

Backend corre localmente con:
- Postgres en compose.yaml
- RustFS/MinIO en compose.yaml

Railway MVP necesita:
- Backend → Neon database (desacoplado, managed)
- Backend → R2 storage (desacoplado, managed)

## Alcance

✅ Remove local DB dependency (spring-boot-testcontainers)
✅ Add Neon connection string (SPRING_DATASOURCE_URL)
✅ Add R2 credentials (AWS_S3_*)
✅ Update application.properties profiles (dev/prod)
✅ Verify backend boots and serves health checks
✅ Railway deployment w/ env vars injected

❌ Kubernetes migration (post-MVP, decide usuario)
❌ Circuit breaker for Neon (post-MVP, Resilience4j)
❌ Failover logic (post-MVP, RTO/RPO SLA)

## Razón de criticidad

**CRÍTICO**: Sin este refactor, backend no arranca en Railway.
Este es el paso final que conecta todo (ODD #503 + T-IN-13 + T-IN-14).
