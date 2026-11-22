# T-IN-07 — Deploy del backend Quarkus en Railway

**Estado:** TODO
**Dificultad:** Intermedio
**Tiempo estimado:** 1 hora
**Depende de:** T-IN-13 (Neon, base de datos de producción)

---

## ¿Qué vas a construir?

Vas a desplegar el backend **Quarkus** en **Railway**: plataforma que ejecuta tu app desde GitHub
automáticamente, con CI/CD incluido.

> La versión anterior de esta tarea describía un deploy de Spring Boot con `DATABASE_URL` de
> Supabase. **Ya no describe producción**: el backend es Quarkus y la base es Neon. Se conserva el
> procedimiento (crear proyecto, variables, dominio, health check) con los valores reales.

---

## Conceptos clave

**¿Qué es Railway?**
Plataforma PaaS ("Platform as a Service") que despliega apps desde Git. Conectas tu repo de GitHub y Railway automáticamente:
1. Detecta cambios en `main`
2. Construye la app
3. Ejecuta el binario
4. Maneja logs, monitoreo, escalado

**¿Cómo se construye?**
Por `railway.json`, que apunta al Dockerfile nativo de Quarkus:

```json
{
  "$schema": "https://railway.app/railway.schema.json",
  "build": {
    "builder": "dockerfile",
    "dockerfile": "backend/electoral-api-quarkus/Dockerfile.native"
  }
}
```

**¿Cómo asigna el puerto?**
Railway asigna un puerto via variable `$PORT` (ej: 8080). El backend lo toma de
`quarkus.http.port=${PORT:8080}`.

**¿Cuántas réplicas?**
**1 réplica** (plan Pro). Es la condición que sostiene la decisión de caché en memoria: con una
sola réplica no hay copias distintas entre réplicas. Si algún día se escala, la caché pasa a TTL
corto con etiqueta `replica` en las métricas (ver `documents/CACHE_SETUP_GUIDE.md`).

---

## Pasos

### Paso 1 — Crear cuenta Railway

1. Ve a https://railway.app
2. Regístrate con GitHub
3. Autoriza acceso a tus repos

### Paso 2 — Crear nuevo proyecto

1. Dashboard → **New Project**
2. Selecciona tu repo `Control-s-Elector`
3. Railway toma el `railway.json` del repo: builder `dockerfile` con
   `backend/electoral-api-quarkus/Dockerfile.native`
4. Configura variables de entorno (Paso 3)
5. Deploy

### Paso 3 — Configurar variables de entorno

En Railway Dashboard → **Variables** (la plantilla canónica es `.env.example`):

```
# Base de datos (Neon) — el backend usa DB_*, no DATABASE_URL:
# el JDBC url se arma en application.properties
DB_HOST=ep-xxxx-pooler.us-east-1.aws.neon.tech
DB_PORT=5432
DB_NAME=postgres
DB_USER=            # rol de la aplicación: SIN BYPASSRLS
DB_PASSWORD=

# Storage de evidencias (Cloudflare R2, S3-compatible)
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_S3_ENDPOINT=https://<account>.r2.cloudflarestorage.com
AWS_S3_REGION=us-east-1
AWS_S3_BUCKET=electoral-evidences
AWS_S3_PATH_STYLE=true

# JWT (HS256, mínimo 32 chars)
APP_JWT_SECRET=
APP_JWT_REFRESH_PEPPER=
APP_JWT_ISSUER=control-elector

# Liquibase: solo 'seed' en producción (la cuenta de admin se provisiona a mano)
QUARKUS_LIQUIDASE_CONTEXTS=seed

# Caché (ver CACHE_SETUP_GUIDE.md)
CACHE_TTL=2m
# RAILWAY_REPLICA_ID lo inyecta la plataforma, una por réplica
```

> Ningún secreto se versiona: estas variables se configuran en el dashboard de Railway.
> `.env.example` es la única plantilla compartida.

### Paso 4 — Conectar dominio custom

1. Railway → **Settings** → **Domains**
2. Agrega dominio: `api.brydyan.me`
3. Railway te da un CNAME; actualiza tu DNS

### Paso 5 — Verificar deploy

```bash
curl https://api.brydyan.me/q/health
# Esperado: { "status": "UP", ... }

curl https://api.brydyan.me/q/metrics | grep cache_size
# Las 4 cachés (cargos, partidos, candidatos, geografia) publicadas con cache_size
```

> Ojo: `/q/health` incluye el readiness del storage (R2). Si el bucket o las credenciales están
> mal, el health queda en 503 aunque la API responda.

---

## Checklist

- [ ] Cuenta Railway creada
- [ ] Repo conectado a Railway (build vía `railway.json` + `Dockerfile.native`)
- [ ] Variables de entorno configuradas (`DB_*`, `AWS_*`, `APP_JWT_*`, `CACHE_TTL`)
- [ ] Dominio custom apuntando
- [ ] Health check respondiendo en `/q/health`
- [ ] `/q/metrics` publica las métricas de caché
