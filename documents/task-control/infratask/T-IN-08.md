# T-IN-08 — application-prod.properties

**Estado:** TODO
**Dificultad:** Principiante
**Tiempo estimado:** 30 min
**Depende de:** T-IN-07 (configuración Railway)

---

## ¿Qué vas a construir?

Archivo `application-prod.properties`: configuración de Spring Boot específica para producción (Railway). Desactiva logs verbose, activa validación de schema, y configura seguridad.

---

## Archivo a crear

```
backend/electoral-api/src/main/resources/
  application-prod.properties   ← Crear
```

---

## Contenido completo

```properties
# ============================================================================
# SPRING BOOT — PRODUCCIÓN
# ============================================================================

# Puerto asignado por Railway via $PORT
server.port=${PORT:8080}

# ============================================================================
# BASE DE DATOS — SUPABASE
# ============================================================================

# Conexión a Supabase (variable de Railway)
spring.datasource.url=${DATABASE_URL}
spring.datasource.driver-class-name=org.postgresql.Driver
spring.datasource.hikari.maximum-pool-size=10
spring.datasource.hikari.minimum-idle=2
spring.datasource.hikari.idle-timeout=600000
spring.datasource.hikari.connection-timeout=30000

# ============================================================================
# JPA / HIBERNATE
# ============================================================================

# validate: falla si schema BD no coincide con entidades
spring.jpa.hibernate.ddl-auto=validate

# No mostrar SQL en prod (seguridad, rendimiento)
spring.jpa.show-sql=false
spring.jpa.properties.hibernate.format_sql=false

# Dialect para PostgreSQL
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQL10Dialect

# Batch inserts para mejor rendimiento
spring.jpa.properties.hibernate.jdbc.batch_size=20
spring.jpa.properties.hibernate.order_inserts=true
spring.jpa.properties.hibernate.order_updates=true

# ============================================================================
# LOGGING
# ============================================================================

# Root logger: WARNING en prod (no debug)
logging.level.root=WARN

# Spring Boot: WARNING
logging.level.org.springframework=WARN

# Hibernate: WARNING (no "Dialect" info)
logging.level.org.hibernate=WARN

# Aplicación: INFO (ej: "Server started")
logging.level.ec.twintailcs=INFO

# ============================================================================
# JWT / SEGURIDAD
# ============================================================================

app.jwt.issuer=${JWT_ISSUER:control-elector}
app.jwt.secret=${JWT_SECRET:placeholder-change-in-prod}
app.jwt.access-ttl=PT15M
app.jwt.refresh-ttl=PT8H
app.jwt.refresh-pepper=${JWT_REFRESH_PEPPER:placeholder-change-in-prod}

# ============================================================================
# SUPABASE / STORAGE
# ============================================================================

app.supabase.url=${SUPABASE_URL}
app.supabase.service-key=${SUPABASE_SERVICE_KEY}
app.supabase.bucket=actas-images

# ============================================================================
# SERVER CONFIG
# ============================================================================

# Timeout para requests largos (ej: upload foto)
server.tomcat.connection-timeout=20000
server.tomcat.max-http-post-size=10485760

# Gzip compression (reduce ancho de banda)
server.compression.enabled=true
server.compression.min-response-size=1024
server.compression.mime-types=application/json,application/xml,text/html,text/xml,text/plain

# Charset UTF-8 para respuestas
server.servlet.encoding.charset=UTF-8
server.servlet.encoding.force=true
```

---

## Cómo crear el archivo

1. Abre editor
2. Copia el contenido anterior
3. Guarda en: `backend/electoral-api/src/main/resources/application-prod.properties`
4. Comitea: `git add application-prod.properties && git commit -m "Add prod config"`

---

## Verificación

Railway automáticamente activa este perfil porque la variable `SPRING_PROFILES_ACTIVE=prod` lo hace:
- Spring carga `application.properties` (base)
- Luego carga `application-prod.properties` (sobreescribe)

Logs en Railway Dashboard:
```
2026-01-15 10:00:00 INFO Started ElectoralApiApplication in 3.5 seconds
```

---

## Checklist

- [ ] Archivo creado en ubicación correcta
- [ ] DATABASE_URL, JWT_SECRET, SUPABASE_* variables existen en Railway
- [ ] Rails build log muestra `Starting ... with profile [prod]`
