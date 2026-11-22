# T-IN-08 — Variables de producción en Railway

**Estado:** TODO
**Dificultad:** Principiante
**Tiempo estimado:** 30 min
**Depende de:** T-IN-07 (configuración Railway)

---

## ¿Qué vas a construir?

La configuración específica de producción, pero **no como un segundo archivo de properties**.

> La versión anterior de esta tarea pedía crear `application-prod.properties` con sintaxis de
> Spring Boot. **El backend de este proyecto es Quarkus y tiene un solo
> `application.properties`**: la configuración de producción son **variables de entorno en el
> dashboard de Railway**, no un archivo. Este archivo se conserva como registro del procedimiento.

---

## Por qué no un `application-prod.properties`

Quarkus resuelve la configuración por orden de precedencia:

1. `application.properties` (en el repo): valores por defecto con `${VAR:default}`
2. Variables de entorno: **pisan** los defaults
3. Archivos de properties fuera del repo: no se usan

Consecuencia práctica: si `DB_PASSWORD`, `APP_JWT_SECRET` o `APP_JWT_REFRESH_PEPPER` no están
setadas en Railway, **el backend arranca igual con los valores de dev** en vez de fallar. Por eso
el checklist de esta tarea es explícito sobre cuáles no pueden faltar.

---

## Variables obligatorias en Railway

```
# Base de datos (Neon)
DB_HOST=ep-xxxx-pooler.us-east-1.aws.neon.tech
DB_PORT=5432
DB_NAME=postgres
DB_USER=
DB_PASSWORD=

# Storage de evidencias (Cloudflare R2)
AWS_ACCESS_KEY_ID=
AWS_SECRET_ACCESS_KEY=
AWS_S3_ENDPOINT=https://<account>.r2.cloudflarestorage.com
AWS_S3_REGION=us-east-1
AWS_S3_BUCKET=electoral-evidences
AWS_S3_PATH_STYLE=true

# JWT (HS256)
APP_JWT_SECRET=            # openssl rand -base64 48
APP_JWT_REFRESH_PEPPER=    # openssl rand -base64 48
APP_JWT_ISSUER=control-elector

# Comportamiento de la app en producción
APP_ENV=prod
QUARKUS_LIQUIDASE_CONTEXTS=seed
QUARKUS_LIQUIDASE_MIGRATE_AT_START=true
QUARKUS_HIBERNATE_GENERATION=validate
QUARKUS_LOG_LEVEL=INFO
APP_LOG_LEVEL=INFO

# Caché
CACHE_TTL=2m
```

Los defaults que ya viven en `application.properties` (pool de conexiones, TTL de los tokens,
CORS, límites de upload) se pueden dejar como están salvo que production requiera cambiarlos.

---

## Verificación

1. En Railway Dashboard → **Variables** debe aparecer cada variable de la lista.
2. Tras el deploy, en los logs:
   ```
   INFO  [org.acme] ... Installed features: [cdi, resteasy-reactive, hibernate-orm, ...]
   INFO  [io.quarkus] ... Profile prod activated  (si se define quarkus.profile)
   ```
3. Comprobación funcional:
   ```bash
   curl https://api.brydyan.me/q/health
   curl -X POST https://api.brydyan.me/api/v1/auth/login \
     -H 'Content-Type: application/json' \
     -d '{"email":"<cuenta real>","password":"<contrasena real>"}'
   ```

---

## Checklist

- [ ] `DB_*` configuradas en Railway (Neon)
- [ ] `AWS_*` configuradas en Railway (Cloudflare R2)
- [ ] `APP_JWT_SECRET` y `APP_JWT_REFRESH_PEPPER` configuradas (32+ chars)
- [ ] `QUARKUS_LIQUIDASE_CONTEXTS=seed` (sin `bootstrap-admin` en producción)
- [ ] `CACHE_TTL` definida
- [ ] Login real funciona contra la base de producción
- [ ] Ningún secreto quedó en el repo
