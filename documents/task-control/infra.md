# Infra Tasks — Docker, Railway, Vercel, Supabase

---

## Local / Staging

### T-IN-01 — Docker Compose staging completo

**Estado:** HECHO
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** —

PostgreSQL 16 corriendo en Docker local en `localhost:5432`.

- [x] `docker-compose.yml` con postgres:16
- [x] Volumen persistente `postgres_data`
- [x] Credenciales: `electoral_db / electoral_user / electoral_pass`
- [x] Spring Boot conectado y arrancando correctamente

---

### T-IN-02 — Schema SQL inicial

**Estado:** TODO
**Prioridad:** ALTA
**Asignado:** Andy
**Depende de:** T-BE-03

Crear script `infra/sql/01-schema.sql` con:

- [x] Tablas del dominio (Hibernate ddl-auto=update lo genera, pero tener script manual)
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
- [x] RlsInterceptor Spring filter: convierte publicId (UUID) a internal id (Long) y ejecuta `SET LOCAL app.current_user_id`
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

## Supabase

### T-IN-05 — Configurar Supabase proyecto

**Estado:** TODO
**Prioridad:** ALTA
**Asignado:** sin asignar
**Depende de:** —

- [x] Crear proyecto en Supabase (región más cercana — São Paulo o US East)
- [x] Obtener `DATABASE_URL` (prod), `SUPABASE_URL`, `SUPABASE_ANON_KEY`, `SUPABASE_SERVICE_KEY`
- [x] Ejecutar scripts RLS en Supabase SQL Editor
- [x] Crear bucket en Object Storage: `actas-images` (privado)
- [x] Configurar política de acceso al bucket: solo service key puede escribir

---

### T-IN-06 — Supabase Realtime

**Estado:** TODO
**Prioridad:** MEDIA
**Asignado:** sin asignar
**Depende de:** T-IN-05

- [x] Habilitar Realtime en tabla `acta` (INSERT, UPDATE)
- [x] Habilitar Realtime en tabla `notificacion`
- [ ] Configurar canal en frontend Angular con `@supabase/supabase-js`
- [ ] Verificar que eventos llegan solo a usuarios con RLS válido

---

## Railway (Backend)

### T-IN-07 — Deploy Spring Boot en Railway

**Estado:** TODO
**Prioridad:** MEDIA
**Asignado:** sin asignar
**Depende de:** T-IN-05

- [ ] Conectar repositorio GitHub a Railway
- [ ] Configurar variables de entorno en Railway:
  - `DATABASE_URL` (Supabase PostgreSQL)
  - `JWT_SECRET`
  - `SUPABASE_URL`, `SUPABASE_SERVICE_KEY`
  - `SPRING_PROFILES_ACTIVE=prod`
- [ ] Crear `application-prod.properties` con config de producción
  - `ddl-auto=validate` (no update en prod)
  - `show-sql=false`
- [ ] Verificar health endpoint: `GET https://api.twintailcs.xyz/actuator/health`
- [ ] Configurar dominio custom `api.twintailcs.xyz`

---

### T-IN-08 — application-prod.properties

**Estado:** TODO
**Prioridad:** ALTA
**Asignado:** sin asignar
**Depende de:** T-IN-07

Crear `backend/electoral-api/src/main/resources/application-prod.properties`:

- [ ] `spring.datasource.url=${DATABASE_URL}`
- [ ] `spring.jpa.hibernate.ddl-auto=validate`
- [ ] `spring.jpa.show-sql=false`
- [ ] `spring.jpa.open-in-view=false`
- [ ] `server.port=${PORT:8080}`
- [ ] JWT secret desde variable de entorno (nunca hardcodeado)

---

## Vercel (Frontend)

### T-IN-09 — Deploy Angular en Vercel

**Estado:** TODO
**Prioridad:** MEDIA
**Asignado:** sin asignar
**Depende de:** T-IN-07

- [ ] Conectar repo GitHub a Vercel
- [ ] Build command: `npm run build -- --configuration=production`
- [ ] Output directory: `dist/app/browser`
- [ ] Variable de entorno: `VITE_API_URL=https://api.twintailcs.xyz`
- [ ] Configurar dominio custom `control.twintailcs.xyz`
- [ ] Verificar SPA redirect: todas las rutas → `index.html` (vercel.json)

---

### T-IN-10 — vercel.json para SPA

**Estado:** TODO
**Prioridad:** ALTA
**Asignado:** sin asignar
**Depende de:** T-IN-09

Crear `frontend/app/vercel.json`:

```json
{
  "rewrites": [
    { "source": "/(.*)", "destination": "/index.html" }
  ]
}
```

Sin esto, refresh en rutas Angular devuelve 404.

---

## CI/CD

### T-IN-11 — GitHub Actions básico

**Estado:** TODO
**Prioridad:** BAJA
**Asignado:** sin asignar
**Depende de:** T-IN-07, T-IN-09

- [ ] Workflow `ci.yml`: en PR → build backend + frontend, correr tests
- [ ] Workflow `deploy.yml`: en push a `main` → Railway y Vercel despliegan automáticamente
- [ ] Secrets en GitHub: `DATABASE_URL`, `JWT_SECRET`, `SUPABASE_SERVICE_KEY`

---

## Tailscale (Staging privado)

### T-IN-12 — Staging con Tailscale

**Estado:** TODO
**Prioridad:** BAJA
**Asignado:** sin asignar
**Depende de:** T-IN-01

- [ ] Instalar Tailscale en máquina de staging
- [ ] Exponer `localhost:8080` solo dentro de la red Tailscale
- [ ] Equipo puede probar staging sin exponer puertos públicos
- [ ] Documentar IP de staging en `diagram/02-deployment.md`
