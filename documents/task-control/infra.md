# Infra Tasks — Docker, Railway, Cloudflare Pages, Neon, Cloudflare R2

> Topología de producción vigente: backend en **Railway** (1 réplica), frontend en **Cloudflare Pages**, base de datos en **Neon** (PostgreSQL serverless) y evidencias en **Cloudflare R2**.

---

## Local / Staging

### T-IN-01 — Docker Compose staging completo

**Estado:** ✅ HECHO
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** —

PostgreSQL 16 corriendo en Docker local en `localhost:5432`.

- [x] `docker-compose.yml` con postgres:16
- [x] Volumen persistente `postgres_data`
- [x] Credenciales: `electoral_db / electoral_user / electoral_pass`
- [x] Backend Quarkus conectado y arrancando correctamente

---

### T-IN-02 — Schema SQL inicial

**Estado:** ✅ HECHO
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** T-BE-03

Crear script `infra/sql/01-schema.sql` con:

- [x] Tablas del dominio (Liquibase es el dueño del esquema; el script manual queda como referencia)
- [x] Índices: `acta(jrv_id, cargo_id)`, `observer_jrv_asignacion(observer_id)`, `refresh_token(token)`
- [x] Constraints: unicidad `acta(jrv_id, cargo_id)` donde estado != ANULADA
- [x] Extensiones: `uuid-ossp` si se usan UUIDs como PK

---

### T-IN-03 — RLS en PostgreSQL

**Estado:** HECHO ✅
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** T-IN-02

Script `infra/sql/02-rls.sql` + RlsInterceptor.java:

- [x] Habilitar RLS en tabla `acta`, `acta_detalle`, `evidencia`, `audit_log`, `notificacion`
- [x] Policy OBSERVER: `jrv_id IN (SELECT jrv_id FROM observer_jrv_asignacion WHERE observer_id = get_current_user_id())`
- [x] Policy COORDINATOR / ADMIN / MASTER: sin restricción de fila
- [x] Filtro de RLS en la capa Quarkus: convierte publicId (UUID) a internal id (Long) y ejecuta `SET LOCAL app.current_user_id`
- [x] SecurityConfig: wiring RlsInterceptor después de JwtAuthFilter
- [x] Función `get_current_user_id()` que lee session variable (vía `app.current_user_id` setting)
- [x] Verificar que API pasa `SET LOCAL app.current_user_id = ?` antes de queries

---

### T-IN-04 — Datos semilla (seed)

**Estado:** HECHO ✅
**Prioridad:** MEDIA
**Asignado:** Andy
**Depende de:** T-IN-02 ✅

Script `infra/sql/03-seed.sql`:

- [x] Provincia Santa Elena (1)
- [x] Cantones: Santa Elena (2401), La Libertad (2402), Salinas (2403)
- [x] Parroquias principales por cantón (6)
- [x] JRVs de ejemplo (15 total: 5 por cantón)
- [x] Usuario MASTER: creado vía DataSeeder.java en runtime
- [x] Usuario OBSERVER de ejemplo: juapeperobs@control.ttcs con 3 JRVs asignadas
- [x] Cargos: 6 electorales
- [x] Partidos: 5 ejemplos (UP, CREO, RC, ID, CS)
- [x] Candidatos: 3 ejemplos (Prefecto)
- [x] JRV_CARGO: todos los cargos en todas las JRVs (cross join)

---

## Base de datos gestionada (histórico: Supabase → Neon)

> Las tareas T-IN-05 y T-IN-06 describen el proveedor de base de datos y realtime usado en una etapa anterior del proyecto (Supabase). **Ya no describen producción**: la base de datos de producción es **Neon** (ver T-IN-13) y el realtime ya no depende de un proveedor administrado. Se conservan como registro histórico.

### T-IN-05 — Configurar proyecto Supabase

**Estado:** ⚠️ HISTÓRICO — sustituido por T-IN-13 (Neon). No describe producción.
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** —

- [x] Crear proyecto en Supabase (región más cercana — São Paulo o US East)
- [x] Obtener `DATABASE_URL` (prod), `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_KEY`
- [x] Ejecutar scripts RLS en Supabase SQL Editor
- [x] Crear bucket en Object Storage: `actas-images` (privado)
- [x] Configurar política de acceso al bucket: solo service key puede escribir

---

### T-IN-06 — Realtime gestionado por el proveedor

**Estado:** ⚠️ HISTÓRICO — sustituido por T-IN-13 (Neon). No describe producción.
**Prioridad:** MEDIA
**Asignado:** Andy
**Depende de:** T-IN-05

- [x] Habilitar Realtime en tabla `acta` (INSERT, UPDATE)
- [x] Habilitar Realtime en tabla `notificacion`
- [ ] Configurar canal en frontend Angular con `@supabase/supabase-js`
- [ ] Verificar que eventos llegan solo a usuarios con RLS válido

---

## Railway (Backend)

### T-IN-07 — Deploy del backend Quarkus en Railway

**Estado:** ⚠️ PARCIAL — el deploy histórico apuntaba al backend Spring Boot y al dominio `api.twintailcs.xyz`. La topología vigente es backend Quarkus (GraalVM native) en `api.brydyan.me` con Neon como base de datos.
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** T-IN-13

- [x] Conectar repositorio GitHub a Railway
- [x] `railway.json` con builder `dockerfile` → `backend/electoral-api-quarkus/Dockerfile.native`
- [x] Plan Pro, 1 réplica
- [ ] Configurar variables de entorno en Railway:
  - [ ] `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (Neon PostgreSQL) — bloqueado por T-IN-13
  - [ ] `JWT_SECRET`
  - [ ] `AWS_S3_ENDPOINT`, `AWS_S3_REGION`, `AWS_S3_BUCKET`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY` (Cloudflare R2, S3-compatible)
  - [ ] `AWS_S3_PATH_STYLE=true` para R2 (R2 usa endpoint con path-style)
- [ ] Verificar health endpoint
- [ ] Configurar dominio custom `api.brydyan.me`

---

### T-IN-08 — Configuración de producción del backend

**Estado:** ⚠️ HISTÓRICO — la configuración descrita abajo corresponde al backend Spring Boot (`application-prod.properties`). El backend vigente es Quarkus y usa `backend/electoral-api-quarkus/src/main/resources/application.properties` con variables de entorno (`DB_*`, `AWS_S3_*`, `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`).
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** T-IN-07

El backend **no tiene** un `application-prod.properties`: Quarkus resuelve la configuración por
precedencia y en producción eso son **variables de entorno en el dashboard de Railway**
(plantilla canónica: `.env.example`).

- [x] `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USER`, `DB_PASSWORD` (Neon) — el backend usa `DB_*`, no `DATABASE_URL`
- [x] `AWS_ACCESS_KEY_ID`, `AWS_SECRET_ACCESS_KEY`, `AWS_S3_ENDPOINT`, `AWS_S3_REGION`, `AWS_S3_BUCKET`, `AWS_S3_PATH_STYLE=true` (Cloudflare R2)
- [x] `APP_JWT_SECRET`, `APP_JWT_REFRESH_PEPPER` (HS256, 32+ chars, nunca hardcodeados)
- [x] `QUARKUS_LIQUIDASE_CONTEXTS=seed` (sin `bootstrap-admin` en producción)
- [x] `QUARKUS_HIBERNATE_GENERATION=validate` y `QUARKUS_LOG_LEVEL=INFO`
- [x] `CACHE_TTL=2m` (ver `documents/CACHE_SETUP_GUIDE.md`)

---

## Cloudflare Pages (Frontend)

### T-IN-09 — Deploy Angular en Cloudflare Pages

**Estado:** ⏳ EN CURSO — Cloudflare Pages es el destino de producción del frontend (sustituye al deploy en Vercel). Ver `CLOUDFLARE_PAGES_SETUP.md`.
**Prioridad:** MEDIA
**Asignado:** Andy
**Depende de:** T-IN-07

- [x] Conectar repo GitHub a Cloudflare Pages
- [x] Build command: `npm run build`
- [x] Output directory: `frontend/app/www` (`angular.json` → `outputPath.base`)
- [ ] Variables de entorno en Cloudflare Pages: `CF_API_URL=https://api.brydyan.me/api/v1`
- [x] Configurar dominio custom `control.brydyan.me`
- [x] Configuración en runtime vía `assets/config.json` (sin rebuild para cambiar la URL de la API)

---

### T-IN-10 — Redirect SPA en Cloudflare Pages

**Estado:** TODO
**Prioridad:** ALTA
**Asignado:** sin asignar
**Depende de:** T-IN-09

En `frontend/app/public/_redirects` (o en la configuración de Pages):

```
/* /index.html 200
```

Sin esto, refresh en rutas Angular devuelve 404.

---

## CI/CD

### T-IN-11 — GitHub Actions básico

**Estado:** ✅ HECHO
**Prioridad:** BAJA
**Asignado:** Andy
**Depende de:** T-IN-07, T-IN-09

- [x] Workflow `ci.yml`: checks requeridos (tests backend + cobertura, `spotless:check`, ESLint, format check, build de producción, tests unitarios frontend)
- [x] Workflow `ci-native.yml`: build de la imagen nativa del backend (solo cuando cambia el módulo Quarkus)
- [x] Workflow `load-test-baseline.yml`: corrida del load test de referencia
- [x] Workflow `deploy.yml`: build de imágenes + rollout a **staging k3s** (no es producción)
- [x] Producción: el deploy lo hacen Railway y Cloudflare Pages con su propia integración de GitHub (sin tokens de despliegue en GitHub)
- [ ] Job de Playwright para los E2E de caché (hoy solo corren en local)
- [ ] Secrets en GitHub: credenciales de Neon y de Cloudflare R2 (nunca en el repo)

---

## Neon (Base de datos de producción)

### T-IN-13 — Neon Database Setup

**Estado:** ⏳ PENDIENTE — la especificación vive en `openspec/changes/infra/2026-11-08-t-in-13-neon-database/` y **no se ha ejecutado**: creación del proyecto, migraciones Liquibase contra Neon, VPC endpoint / IP whitelist, connection pooling, backups y health check siguen pendientes. Ver `apply-progress.md` de ese change para el detalle del estado real.

**Prioridad:** CRÍTICA
**Asignado:** sin asignar
**Depende de:** —

- [ ] Crear el proyecto Neon (`control-electoral-mvp`) y anotar la connection string
- [ ] Ejecutar las migraciones Liquibase contra Neon
- [ ] Verificar el esquema (tablas y FKs)
- [ ] Configurar VPC endpoint o IP whitelist para Railway
- [ ] Verificar el connection pooling (el pool de Agroal de Quarkus)
- [ ] Verificar backups automáticos y PITR
- [ ] Health check de base de datos (`/api/v1/health/db`)

---

## Tailscale (Staging privado)

### T-IN-12 — Staging con Tailscale

**Estado:** TODO
**Prioridad:** BAJA
**Asignado:** sin asignar
**Depende de:** T-IN-01

- [x] Instalar Tailscale en máquina de staging
- [x] Exponer `localhost:8080` solo dentro de la red Tailscale
- [ ] Equipo puede probar staging sin exponer puertos públicos
- [ ] Documentar IP de staging en `diagram/02-deployment.md`
