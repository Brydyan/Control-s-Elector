# T-IN-01 — Docker Compose staging completo

**Estado:** ✅ HECHO
**Dificultad:** Principiante
**Tiempo estimado:** 1 hora
**Depende de:** ninguna

---

## ¿Qué vas a construir?

Vas a configurar un stack Docker completo para desarrollo/staging local. Esto incluye una base de datos PostgreSQL 16 corriendo en un contenedor, con volumen persistente, healthcheck automático, locale español (es-EC para Ecuador) y acceso desde el host a través del puerto estándar `5432`.

**Estado actual:** Ya existe `compose.yaml` en la raíz del proyecto. Este archivo documenta cómo se configuró y cómo reutilizarlo.

---

## Conceptos clave

**¿Qué es Docker Compose?**
Es una herramienta que define y ejecuta aplicaciones multi-contenedor. En lugar de crear y conectar contenedores manualmente con comandos largos, escribes un archivo `compose.yaml` que describe toda la stack.

**¿Qué es un "profile" en Docker Compose?**
Un perfil permite activar o desactivar servicios condicionales. Por ejemplo, `--profile dev` levanta solo PostgreSQL (ligero), mientras que `--profile s3` agrega RustFS (más pesado, opcional).

**¿Qué es un "volumen" persistente?**
Sin él, cuando el contenedor se detiene, los datos se pierden. Un volumen es almacenamiento que persiste en el disco del host, aunque el contenedor muera.

**¿Qué es un "healthcheck"?**
Un test automático que el contenedor ejecuta periódicamente (ej: cada 10s) para verificar que el servicio está sano. Si falla, Docker puede reiniciarlo automáticamente o alertar.

**¿Qué es "locale" en PostgreSQL?**
Afecta cómo ordena textos, formatea fechas/números, etc. `es-EC` es español para Ecuador. Sin especificarlo, PostgreSQL usa UTF-8 genérico.

---

## Archivos / Estado actual

```
/home/andy/Escritorio/PROYECTOS/CONTROL's/Control-s-Elector/
  compose.yaml           ← Existe, ya configurado ✅
  .env                   ← Existe, credenciales dev ✅
  .env.example           ← Plantilla (si existe)
  docker-compose.yml     ← Alternativa antigua (NO usar, obsoleta)
```

---

## ¿Qué se hizo? (retrospectiva)

### Paso 1 — Crear `compose.yaml`

El archivo actual contiene **dos servicios**:

1. **`postgres`** — perfil `dev` (levantado por default)
   - Imagen: `postgres:16` (última versión estable)
   - Variables env: `POSTGRES_DB`, `POSTGRES_USER`, `POSTGRES_PASSWORD` (leídas de `.env`)
   - Puerto: `${DB_PORT:-5432}` (default 5432, override posible)
   - Volumen: `postgres_data:/var/lib/postgresql/data` (persistencia)
   - Healthcheck: `pg_isready` cada 10s (timeout 5s, máx 5 reintentos, espera 30s antes de empezar)
   - Locale: ICU con `es-EC` (formatos de fecha/número ecuatorianos)
   - Logging: JSON, máx 10MB por archivo, 3 archivos max

2. **`rustfs`** — perfil `s3` (opcional, no se levanta por default)
   - Imagen: emulación S3
   - Puertos: 9000 (API), 9001 (consola web)
   - Volumen: `rustfs_data` (datos persistentes)

### Paso 2 — Crear `.env`

```bash
# .env (raíz del proyecto)
SERVER_PORT=8081           # Backend (Spring Boot) en el host, no Docker
DB_HOST=localhost
DB_PORT=5432               # PostgreSQL dentro de Docker
DB_NAME=electoral_db
DB_USER=electoral_user
DB_PASSWORD=electoral_pass # ⚠️ SOLO para dev, cambiar en prod
APP_ENV=dev                # Sufijo contenedor: electoral-postgres-dev
POSTGRES_MEM_LIMIT=512M    # Límite de RAM para Postgres
RESTART_POLICY=no          # No reiniciar si se cae en dev (facilita debugging)
```

### Paso 3 — Verificar que levanta

```bash
cd /path/to/Control-s-Elector

# Opción 1: Docker Compose moderno (v2.0+)
docker compose --profile dev up -d

# Opción 2: Docker Compose legacy (v1.x)
docker-compose -f compose.yaml up -d

# Ver estado
docker compose ps

# Ver logs de Postgres
docker compose logs postgres

# Conectar a BD desde host
psql -h localhost -U electoral_user -d electoral_db
# Te pide password: electoral_pass
# Si entra, funciona ✅
```

### Paso 4 — Configurar Spring Boot para conectar

En `backend/electoral-api/src/main/resources/application.properties`:

```properties
# Postgres en localhost:5432 (vía Docker)
spring.datasource.url=jdbc:postgresql://${DB_HOST:localhost}:${DB_PORT:5432}/${DB_NAME:electoral_db}
spring.datasource.username=${DB_USER:electoral_user}
spring.datasource.password=${DB_PASSWORD:electoral_pass}
spring.datasource.driver-class-name=org.postgresql.Driver

# JPA / Hibernate
spring.jpa.database-platform=org.hibernate.dialect.PostgreSQL10Dialect
spring.jpa.hibernate.ddl-auto=update
spring.jpa.show-sql=true
```

Luego arranca el backend:
```bash
cd backend/electoral-api
./mvnw spring-boot:run
```

Spring Boot se conectará al Postgres en Docker automáticamente.

---

## Cómo verificar que funciona

1. **Docker containers levantados:**
   ```bash
   docker compose ps
   # Debería mostrar:
   # NAME                    STATUS
   # electoral-postgres-dev  Up (healthy)
   ```

2. **PostgreSQL responde:**
   ```bash
   docker exec -it electoral-postgres-dev psql -U electoral_user -d electoral_db -c "SELECT version();"
   ```

3. **Volumen persiste:**
   ```bash
   # Ver volúmenes
   docker volume ls | grep electoral
   
   # Detener contenedor (datos siguen ahí)
   docker compose down
   
   # Reiniciar (datos recuperados)
   docker compose up -d
   ```

4. **Spring Boot conecta:**
   ```bash
   cd backend/electoral-api && ./mvnw spring-boot:run
   # En los logs buscar: "HikariPool-1 - Added connection org.postgresql.jdbc.PgConnection"
   ```

---

## Errores comunes

**Error: `Address already in use: bind`**
Razón: puerto 5432 o 9000 ya está ocupado (por otro Postgres o RustFS nativo).
Solución: `lsof -i :5432` para ver quién lo usa. Mata ese proceso o cambia el puerto en `.env`: `DB_PORT=5433`.

**Error: `docker: command not found`**
Razón: Docker no está instalado o no está en PATH.
Solución: Instala Docker Desktop (macOS/Windows) o `sudo apt install docker.io` (Linux). Reinicia la terminal después.

**Error: `permission denied while trying to connect to Docker daemon`**
Razón: Usuario no tiene permisos para acceder a Docker.
Solución: `sudo usermod -aG docker $USER` y reinicia sesión.

**PostgreSQL levanta pero no responde a healthcheck**
Razón: timeout muy corto o credenciales inválidas en healthcheck.
Solución: Revisa `.env` y verifica que `POSTGRES_USER`, `POSTGRES_PASSWORD`, `DB_NAME` coinciden. Aumenta `timeout` en compose.yaml si es lento.

**Datos desaparecen al reiniciar Docker**
Razón: No estás usando volumen o el volumen se eliminó.
Solución: Confirma `volumes:` en compose.yaml. Usa `docker volume ls` para ver si `electoral-postgres-dev-data` existe.

**Locale no es es-EC (fechas/números en formato inglés)**
Razón: Postgres se creó sin locale `es-EC` (sucede si subiste datos antes de esta tarea).
Solución: Elimina volumen y recrea:
```bash
docker volume rm electoral-postgres-dev-data
docker compose down -v && docker compose up -d
```

---

## Checklist original (de infra.md)

- [x] `docker-compose.yml` con postgres:16
- [x] Volumen persistente `postgres_data`
- [x] Credenciales: `electoral_db / electoral_user / electoral_pass`
- [x] Spring Boot conectado y arrancando correctamente
