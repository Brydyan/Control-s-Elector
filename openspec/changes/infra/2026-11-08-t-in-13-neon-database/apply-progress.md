# Apply Progress: T-IN-13

> Estado real de la ejecución. Este change **no se ha ejecutado**: nada de lo
> listado abajo se completó. Ver `tasks.md` para el detalle de cada bloque.

## Estado actual

- Neon project creation: [PENDIENTE]
- Liquibase migrations: [PENDIENTE]
- Schema verification: [PENDIENTE]
- VPC endpoint / IP whitelist: [PENDIENTE]
- Connection pooling: [PENDIENTE]
- Backups: [PENDIENTE]
- Health check: [PENDIENTE]
- Mutaciones: [PENDIENTE]

## Ya existe en el repo (no requiere ejecución de este change)

- `railway.json`: builder `dockerfile` → `backend/electoral-api-quarkus/Dockerfile.native`.
  Es el camino de despliegue del backend en Railway (plan Pro, 1 réplica).
- `backend/electoral-api-quarkus/Dockerfile.native`: imagen de producción en GraalVM native.
- `backend/electoral-api-quarkus/src/main/resources/application.properties`: variables de
  conexión (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD`) y de object storage
  S3-compatible (`AWS_S3_ENDPOINT`, `AWS_S3_REGION`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`,
  `AWS_SECRET_ACCESS_KEY`), que son las que se configurarán contra Neon y Cloudflare R2.
- `compose.yaml` + `railway.json` no dependen de este change para su configuración.

## Pendiente para que Neon sea la base de datos de producción

1. Crear el proyecto Neon (`control-electoral-mvp`) y registrar la connection string
   (host, usuario, password, base, región) — la región debe alinearse con la de Railway.
2. Ejecutar las migraciones Liquibase contra Neon y verificar el esquema (tablas y FKs).
3. Definir el acceso de red desde Railway (VPC endpoint o IP whitelist) y anotar método + fecha
   de revisión.
4. Verificar el connection pooling (HikariCP) contra Neon.
5. Verificar backups automáticos y PITR en el plan de Neon activo.
6. Implementar/verificar el health check de base de datos (`/api/v1/health/db`).
7. Ejecutar las mutaciones de `tasks.md` (bloque H).

## Notas

- Topología de producción vigente y confirmada: backend en Railway (1 réplica, `api.brydyan.me`),
  frontend en Cloudflare Pages (`control.brydyan.me`), base de datos en Neon y evidencias .webp
  en Cloudflare R2.
- Los manifiestos de `k8s/` son de referencia/legado: no son el destino de producción ni una
  vía de rollout.
- La región de Neon, el tier concreto y el costo del plan de Railway **no están confirmados**
  en este change: quedan por verificar al ejecutar el bloque A de `tasks.md`.
