<!-- pandorx:profile:start -->
# Project profile

These are the architectural decisions of this project. Treat them as
invariants: a plan that contradicts one of them is a change to the
project, not an implementation of it, and it needs to be raised.

## Nature and domain of the system
- Answer: Full-stack application (B2B SaaS or B2C user-facing product).
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- El frontend Angular y el backend Quarkus se despliegan y versionan juntos: no hay contrato público reutilizable fuera del producto.
- Capas del backend: resource (REST) -> application (casos de uso) -> domain (reglas) -> persistence (Panache).

## System maturity and intervention horizon
- Answer: Greenfield / new product: initial functional release with defined boundaries.
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- No ampliar el alcance del release inicial sin plantearlo antes como cambio de producto explícito.
- Todo lo declarado invariante en un ADR requiere una decisión humana explícita para cambiarse.

## Concurrency, load and performance
- Answer: Other (specify).
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- Los picos del escrutinio no habilitan por sí solos la introducción de colas o workers: primero se mide con métricas de latencia y tasa de error y después se decide.
- El cliente móvil debe tolerar reintento y reconexión sin duplicar el envío de un acta: idempotencia por clave de negocio.

## Data architecture and persistence
- Answer: Relational database (PostgreSQL, MySQL, SQLite).
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- Liquibase es el único dueño del esquema: el mapeo ORM es explícito en cada entidad y no depende de conversiones implícitas.
- Las evidencias se comprimen a .webp en el cliente y se almacenan como objetos en object storage; nunca como BYTEA en PostgreSQL.

## Authentication and security
- Answer: Multi-tenant with role-based access control (RBAC/ABAC).
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- Toda ruta y todo endpoint nuevo declara su matriz de roles antes de escribirse.
- RLS de PostgreSQL es capa obligatoria: un endpoint que la salta se considera defecto y no excepción.

## Testing and quality strategy
- Answer: Full test pyramid (unit + integration + E2E).
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- Cada transición del ciclo de vida del acta necesita cobertura de integración antes de llegar a develop.
- Los E2E de flujo crítico corren contra la misma API que producción: nada de mocks en la ruta de registro de acta.

## Infrastructure and deployment
- Answer: Railway (backend) + Cloudflare Pages (frontend), con Neon (PostgreSQL serverless) y Cloudflare R2 (object storage).
- Confirmed by the human against the repository.
- Evidence: railway.json, backend/electoral-api-quarkus/Dockerfile.native, k8s/ (manifiestos de referencia/legado, no es destino de producción)

Guardrails para la IA (restricciones vinculantes):
- Ningún secreto se versiona: las variables de entorno de producción se configuran en Railway y en Cloudflare Pages, y .env.example es la única plantilla compartida.
- El despliegue de producción es Railway (backend, 1 réplica) + Cloudflare Pages (frontend); los manifiestos de k8s/ son de referencia y no son una vía de rollout.

## Observability and monitoring
- Answer: Full observability: logs + metrics + traces (OpenTelemetry).
- Stated by the human. Do not change it without saying so.

Guardrails para la IA (restricciones vinculantes):
- Ningún log lleva token ni refresh token ni credenciales de la persona observada.
- Health de proceso no es salud del producto: las métricas cubren el pipeline de actas (enviadas / en disputa / aprobadas) y no solo el liveness del contenedor.
<!-- pandorx:profile:end -->
