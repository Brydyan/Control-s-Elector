# Contexto del proyecto — pandorx

Este archivo es una **vista materializada compilada** a partir de las
decisiones de `pandorx init`. No se edita a mano: se regenera cada vez
que el init o el flujo de configuración se ejecuta. Cada decisión tiene
su ADR individual en `pdd/context/architecture/decisions/`.

## Decisiones por etapa

### Full-stack application (B2B SaaS or B2C user-facing product). (system_nature)

- **Procedencia:** user
- **Decisión:** C
- **Justificación:** Es un producto propio, no una librería ni una API headless: la persona usuaria (veedor, coordinador, administrador electoral) entra a una aplicación con su sesión y su rol. La mitad backend existe para sostener esa experiencia de campo, no para integrarse con terceros.
- **Guardrails:** El frontend Angular y el backend Quarkus se despliegan y versionan juntos: no hay contrato público reutilizable fuera del producto.; Capas del backend: resource (REST) -> application (casos de uso) -> domain (reglas) -> persistence (Panache).
- **Contenido:** ## Nature and domain of the system

- Question: What is the domain, business model and type of system you are building?
- Answer: C — Full-stack application (B2B SaaS or B2C user-facing product).
- Detail: Aplicación full-stack de misión crítica para el control electoral de la Provincia de Santa Elena: frontend Angular 21 + PrimeNG 21 + Tailwind CSS 4 (PWA offline-first) y backend Quarkus 3.40 / Java 25 (Panache + Hibernate ORM), con PostgreSQL y Supabase Object Storage para las evidencias fotográficas.
- Source: user

### Greenfield / new product: initial functional release with defined boundaries. (scope_mvp)

- **Procedencia:** user
- **Decisión:** A
- **Justificación:** El ciclo electoral 2027-2031 es el primer período de operación real del producto: el alcance de esta intervención es el release funcional inicial con sus límites definidos. La migración Spring -> Quarkus en curso es un trabajo de runtime y no cambia el contrato de producto, así que no define el horizonte de esta intervención.
- **Guardrails:** No ampliar el alcance del release inicial sin plantearlo antes como cambio de producto explícito.; Todo lo declarado invariante en un ADR requiere una decisión humana explícita para cambiarse.
- **Contenido:** ## System maturity and intervention horizon

- Question: What is the lifecycle maturity of this system and the scope of this intervention?
- Answer: A — Greenfield / new product: initial functional release with defined boundaries.
- Detail: Release funcional inicial del producto para el ciclo electoral 2027-2031, con límites explícitos de alcance. La migración interna Spring -> Quarkus es trabajo de runtime, no un cambio del contrato de producto.
- Source: user

### Other (specify). (concurrency_load)

- **Procedencia:** user
- **Decisión:** E
- **Justificación:** El perfil real es bajo en régimen normal y con ráfagas concentradas en la ventana de escrutinio, cuando todos los observadores registran y suben actas a la vez. Eso obliga a que el backend degrade con elegancia en lugar de caerse y a que el frontend tenga reintentos con backoff y cola local, pero hoy el sistema no tiene workers ni colas: esa es deuda conocida, no un supuesto de diseño.
- **Guardrails:** Los picos del escrutinio no habilitan por sí solos la introducción de colas o workers: primero se mide con métricas de latencia y tasa de error y después se decide.; El cliente móvil debe tolerar reintento y reconexión sin duplicar el envío de un acta: idempotencia por clave de negocio.
- **Contenido:** ## Concurrency, load and performance

- Question: What is the expected usage volume and performance profile?
- Answer: E — Other (specify).
- Detail: No habrá concurrencia como constante: es una app de control electoral con tráfico bajo y estable la mayor parte del tiempo, pero a la hora del escrutinio, cuando van a comenzar a subir resultados, puede ser bastante alta la concurrencia.
- Source: user

### Relational database (PostgreSQL, MySQL, SQLite). (data_architecture)

- **Procedencia:** user
- **Decisión:** A
- **Justificación:** El valor del producto es la integridad del acta: la coherencia entre padrón, votos válidos, blancos y nulos exige transacciones ACID y constraints en el esquema, que un almacén de documentos no da. Las fotos son el único payload grande y no se consultan relacionalmente, así que van a object storage.
- **Guardrails:** Liquibase es el único dueño del esquema: el mapeo ORM es explícito en cada entidad y no depende de conversiones implícitas.; Las evidencias se comprimen a .webp en el cliente y se almacenan como objetos en object storage; nunca como BYTEA en PostgreSQL.
- **Contenido:** ## Data architecture and persistence

- Question: How will the system store and manage data?
- Answer: A — Relational database (PostgreSQL, MySQL, SQLite).
- Detail: PostgreSQL como única fuente de verdad, con el esquema gestionado por Liquibase y el acceso por Hibernate ORM con Panache. Las evidencias fotográficas van a Supabase Object Storage en formato .webp; el binario no se guarda en la base de datos. RLS de PostgreSQL actúa como segunda línea de autorización.
- Source: user

### Multi-tenant with role-based access control (RBAC/ABAC). (auth_security)

- **Procedencia:** user
- **Decisión:** E
- **Justificación:** La sensibilidad del sistema no está en identificar a la persona sino en decidir qué puede hacer: un OBSERVER que alcanza la ruta de anulación de actas compromete la jornada entera. El control efectivo está en el RBAC de cuatro roles y en RLS, que por eso es la decisión que el proyecto fija. Se elige esta opción del catálogo porque es la única que nombra RBAC; en la práctica es single-tenant.
- **Guardrails:** Toda ruta y todo endpoint nuevo declara su matriz de roles antes de escribirse.; RLS de PostgreSQL es capa obligatoria: un endpoint que la salta se considera defecto y no excepción.
- **Contenido:** ## Authentication and security

- Question: Does the system require user authentication?
- Answer: E — Multi-tenant with role-based access control (RBAC/ABAC).
- Detail: JWT propio con access de 15 min y refresh de 7 días con pepper, firmado con SmallRye/JOSE; sin OAuth2 ni SSO externo. Sobre el login hay RBAC de cuatro roles (OBSERVER, COORDINATOR, MASTER, ADMIN) aplicado en tres capas: guards de ruta en Angular, verificación de rol en el backend y RLS en PostgreSQL. El sistema es single-tenant: una sola provincia y una sola administración electoral, sin multi-tenancy real.
- Source: user

### Full test pyramid (unit + integration + E2E). (testing_strategy)

- **Procedencia:** user
- **Decisión:** C
- **Justificación:** El producto es de misión crítica y el fallo de un acta se descubre tarde, en el escrutinio: la red de seguridad no puede depender de QA manual en jornada. La capa de integración con Testcontainers protege el contrato de las transiciones del acta (BORRADOR -> ENVIADA -> EN_DISPUTA -> CORREGIDA -> APROBADA) y la de E2E cubre el camino de campo con conectividad intermitente.
- **Guardrails:** Cada transición del ciclo de vida del acta necesita cobertura de integración antes de llegar a develop.; Los E2E de flujo crítico corren contra la misma API que producción: nada de mocks en la ruta de registro de acta.
- **Contenido:** ## Testing and quality strategy

- Question: What testing approach do you want from the start?
- Answer: C — Full test pyramid (unit + integration + E2E).
- Detail: Pirámide completa: unitarios en backend (JUnit 5 + AssertJ) y frontend (Vitest), contract tests de integración contra PostgreSQL real con Testcontainers + RestAssured, y E2E de navegador cubriendo el flujo completo de registro de acta y sus transiciones de estado. Todo orquestado por los workflows de CI existentes (ci.yml, ci-native.yml).
- Source: user

### Kubernetes / cloud-native (AWS ECS, GKE, etc.). (infrastructure_deployment)

- **Procedencia:** confirmed
- **Decisión:** D
- **Justificación:** El sistema tiene que estar disponible y auditable durante la jornada, con despliegue repetible y reversión inmediata si una versión sale mal: ArgoCD sobre Kubernetes da esa trazabilidad, y separar secretos de configmap permite rotar el JWT secret sin tocar la imagen. Compose se conserva para desarrollo.
- **Guardrails:** Ningún secreto se versiona: todo va por Secret de Kubernetes y .env.example es la única plantilla compartida.; El despliegue de producción es ArgoCD sobre k8s/: un kubectl apply manual rompe la trazabilidad del rollout.
- **Contenido:** ## Infrastructure and deployment

- Question: How will this system be deployed and run?
- Answer: D — Kubernetes / cloud-native (AWS ECS, GKE, etc.).
- Detail: Kubernetes como destino de producción, con ArgoCD para la sincronización (k8s/ con kustomization, ingress y ArgoCD Application), secretos y configmap separados, PostgreSQL en StatefulSet, frontend servido por nginx y backend Quarkus. Docker Compose (compose.yaml) queda como entorno local reproducible, no como destino de producción.
- Source: confirmed
- Evidence:
  - k8s/: kubernetes manifest directory

### Full observability: logs + metrics + traces (OpenTelemetry). (observability)

- **Procedencia:** user
- **Decisión:** D
- **Justificación:** En un sistema electoral un fallo silencioso es un fallo electoral: si un acta se queda sin consolidar, el resultado consolidado miente y nadie se entera hasta que ya no se puede corregir. Por eso se decide el nivel completo y no solo logging, y por eso las métricas cubren el pipeline de actas y no solo el Health de Quarkus.
- **Guardrails:** Ningún log lleva token ni refresh token ni credenciales de la persona observada.; Health de proceso no es salud del producto: las métricas cubren el pipeline de actas (enviadas / en disputa / aprobadas) y no solo el liveness del contenedor.
- **Contenido:** ## Observability and monitoring

- Question: How will you monitor the system in production?
- Answer: D — Full observability: logs + metrics + traces (OpenTelemetry).
- Detail: Observabilidad completa: logs estructurados en JSON a stdout con request id, métricas Prometheus vía SmallRye Metrics (latencia, tasa de error, saturación del pool de conexiones, actas por estado) y trazas OpenTelemetry que cruzan frontend, API, PostgreSQL y S3. Alertas sobre salud del servicio y sobre el retraso de la consolidación de actas.
- Source: user

## Guardrails activos

- El frontend Angular y el backend Quarkus se despliegan y versionan juntos: no hay contrato público reutilizable fuera del producto.  (decisión: Full-stack application (B2B SaaS or B2C user-facing product).)
- Capas del backend: resource (REST) -> application (casos de uso) -> domain (reglas) -> persistence (Panache).  (decisión: Full-stack application (B2B SaaS or B2C user-facing product).)
- No ampliar el alcance del release inicial sin plantearlo antes como cambio de producto explícito.  (decisión: Greenfield / new product: initial functional release with defined boundaries.)
- Todo lo declarado invariante en un ADR requiere una decisión humana explícita para cambiarse.  (decisión: Greenfield / new product: initial functional release with defined boundaries.)
- Los picos del escrutinio no habilitan por sí solos la introducción de colas o workers: primero se mide con métricas de latencia y tasa de error y después se decide.  (decisión: Other (specify).)
- El cliente móvil debe tolerar reintento y reconexión sin duplicar el envío de un acta: idempotencia por clave de negocio.  (decisión: Other (specify).)
- Liquibase es el único dueño del esquema: el mapeo ORM es explícito en cada entidad y no depende de conversiones implícitas.  (decisión: Relational database (PostgreSQL, MySQL, SQLite).)
- Las evidencias se comprimen a .webp en el cliente y se almacenan como objetos en object storage; nunca como BYTEA en PostgreSQL.  (decisión: Relational database (PostgreSQL, MySQL, SQLite).)
- Toda ruta y todo endpoint nuevo declara su matriz de roles antes de escribirse.  (decisión: Multi-tenant with role-based access control (RBAC/ABAC).)
- RLS de PostgreSQL es capa obligatoria: un endpoint que la salta se considera defecto y no excepción.  (decisión: Multi-tenant with role-based access control (RBAC/ABAC).)
- Cada transición del ciclo de vida del acta necesita cobertura de integración antes de llegar a develop.  (decisión: Full test pyramid (unit + integration + E2E).)
- Los E2E de flujo crítico corren contra la misma API que producción: nada de mocks en la ruta de registro de acta.  (decisión: Full test pyramid (unit + integration + E2E).)
- Ningún secreto se versiona: todo va por Secret de Kubernetes y .env.example es la única plantilla compartida.  (decisión: Kubernetes / cloud-native (AWS ECS, GKE, etc.).)
- El despliegue de producción es ArgoCD sobre k8s/: un kubectl apply manual rompe la trazabilidad del rollout.  (decisión: Kubernetes / cloud-native (AWS ECS, GKE, etc.).)
- Ningún log lleva token ni refresh token ni credenciales de la persona observada.  (decisión: Full observability: logs + metrics + traces (OpenTelemetry).)
- Health de proceso no es salud del producto: las métricas cubren el pipeline de actas (enviadas / en disputa / aprobadas) y no solo el liveness del contenedor.  (decisión: Full observability: logs + metrics + traces (OpenTelemetry).)

---
Generado automáticamente por `pandorx init` el 6 de enero de 2026.
