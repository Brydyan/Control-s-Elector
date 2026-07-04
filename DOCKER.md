# Docker — Guía de Desarrollo Local

Sistema de Control Electoral — Docker en este proyecto se usa **solo para los servicios de infraestructura** (PostgreSQL y RustFS/S3). El backend y el frontend corren nativos en el host.

## Filosofía dev/prod

| Capa | Dev local | Producción (Railway) |
|------|-----------|----------------------|
| PostgreSQL | Docker (`compose.yaml`) | Supabase (managed) |
| RustFS / S3 | Docker (`compose.yaml`) | Supabase Object Storage |
| Backend Spring Boot | `mvn spring-boot:run` en el host | Docker (imagen JAR ejecutable, JVM 21) |
| Frontend Angular | `ng serve` en el host | Vercel (build de Node 20) |

**¿Por qué?** Buildar GraalVM Native Image consume ~3-4 GB heap durante AOT, lo cual satura Docker Desktop con el límite por defecto. Correr JVM y dev server en el host da hot-reload instantáneo y deja RAM del VM Docker libre para las DBs.

> **Para prod (Railway)**: existe `backend/electoral-api/Dockerfile` que arma una imagen con JRE Alpine + JAR ejecutable. Para GraalVM nativo en prod, hay un approach documentado pero no aplicado por ahora (su RAM de build lo permite).

---

## 1. Prerrequisitos

### Software requerido

| Software | Versión mínima | Notas |
|----------|----------------|-------|
| Java JDK | 21 | Temurin (host) |
| Maven | vía `mvnw` | Wrapper incluido en el repo |
| Docker | 24.0+ | [Install Docker](https://docs.docker.com/get-docker/) |
| Docker Compose | v2.20+ | Incluido en Docker Desktop |
| Node | 20.x | Solo si vas a tocar el frontend |
| Angular CLI | 17.x | `npm install -g @angular/cli` |

### Recursos de hardware

| Recurso | Mínimo | Recomendado |
|---------|--------|-------------|
| RAM | 8 GB | 16 GB |
| Disco | 5 GB libres | 10 GB libres |

### Opcional pero recomendado

| Software | Uso |
|----------|-----|
| [DBeaver](https://dbeaver.io/) | Visualizar PostgreSQL |
| [MinIO Client (mc)](https://min.io/download) | Admin de storage S3 — compatible con RustFS |
| [Tailscale](https://tailscale.com/) | Acceso remoto sin puertos públicos |

---

## 2. Inicialización

### 2.1 Levantar la infraestructura

```bash
# Clonar el repositorio
git clone https://github.com/twintailcs/Control-s-Elector.git
cd Control-s-Elector

# Levantar Postgres + RustFS (servicios de dev)
docker compose --profile dev up -d

# Verificar que ambos están healthy
docker compose --profile dev ps
```

### 2.2 Levantar el backend (en una terminal aparte)

```bash
cd backend/electoral-api

# Dev con hot-reload via spring-boot:run
mvn spring-boot:run

# Alternativa: package + ejecutar el JAR
mvn clean package -DskipTests
java -jar target/electoral-api-0.0.1-SNAPSHOT.jar
```

El backend escucha en **http://localhost:8080**.

### 2.3 Levantar el frontend (en otra terminal)

```bash
cd frontend/app
npm install   # primera vez
ng serve --host 0.0.0.0 --port 4200 --proxy-config proxy.conf.json
```

El frontend queda disponible en **http://localhost:4200**.

### 2.4 Servicios disponibles

| Servicio | URL | Credenciales |
|----------|-----|--------------|
| Frontend (Angular) | http://localhost:4200 | — |
| API REST Spring Boot | http://localhost:8080 | — |
| Health Check | http://localhost:8080/actuator/health | — |
| RustFS Console | http://localhost:9001 | rustfsadmin / rustfsadmin123 |
| RustFS API | http://localhost:9000 | rustfsadmin / rustfsadmin123 |
| PostgreSQL | localhost:5432 | electoral_user / electoral_pass |

### 2.5 Detener todo

```bash
# Detener contenedores Docker
docker compose --profile dev down

# Borrar también volúmenes (datos persistentes) — ¡cuidado!
docker compose --profile dev down -v

# Backend y frontend: Ctrl-C en la terminal donde corren
```

---

## 3. Referencia de Variables de Entorno

Las variables se leen desde el archivo `.env` en la raíz del proyecto.
Ver `.env.example` para la lista completa. Defaults seguros para dev.

### Para los contenedores Docker

| Variable | Default | Descripción |
|----------|---------|-------------|
| `DB_NAME` | electoral_db | Nombre de la base |
| `DB_USER` | electoral_user | Usuario PostgreSQL |
| `DB_PASSWORD` | electoral_pass | Password (solo dev) |
| `DB_PORT` | 5432 | Puerto PostgreSQL externo |
| `APP_ENV` | dev | Sufijo de contenedores/volúmenes |
| `POSTGRES_MEM_LIMIT` | 512M | Mem limit del container Postgres |

### Para el backend Spring Boot (cuando corre con `mvn spring-boot:run`)

Las variables se toman de `backend/electoral-api/src/main/resources/application.properties` o se sobreescriben con env vars:

| Variable | Default | Descripción |
|----------|---------|-------------|
| `SPRING_PROFILES_ACTIVE` | default | Activar `dev,docker` si conectás al Postgres del container |
| `DB_HOST` | localhost | `postgres` si querés conectarte al de Docker |
| `SPRING_JPA_HIBERNATE_DDL_AUTO` | update | `validate` en prod |
| `SPRING_JPA_SHOW_SQL` | true | Mostrar SQL en logs |

### Para RustFS

Configurado internamente en `compose.yaml`. Credenciales dev: `rustfsadmin / rustfsadmin123`.

---

## 4. Troubleshooting

### Backend no arranca: "Connection refused" a PostgreSQL

```bash
# Verificar que el container está corriendo
docker compose --profile dev ps

# Esperar a que el healthcheck pase
docker compose --profile dev ps  # debería mostrar "healthy"

# Conectar desde el host usando 'localhost'
# O usar 'postgres' como DB_HOST si necesitás acceso dentro del network Docker
```

### Backend no arranca: "OutOfMemoryError"

No confundir con el OOM de GraalVM Native Image (ya resuelto corriendo JVM directo). Si pasa con `mvn spring-boot:run`:

```bash
# Reducir heap de la JVM
export MAVEN_OPTS="-Xmx1g"
mvn spring-boot:run
```

### PostgreSQL no levanta

```bash
docker compose --profile dev logs postgres
docker compose --profile dev restart postgres
```

### RustFS no responde

```bash
docker compose --profile dev logs rustfs

# Bucket bootstrap (S3 necesita firma SigV4)
docker run --rm --network host minio/mc \
  alias set local http://localhost:9000 rustfsadmin rustfsadmin123
docker run --rm --network host minio/mc mb local/electoral-evidences
```

### Frontend no se conecta al backend

Verificar `frontend/app/proxy.conf.json` apunta a `http://localhost:8080` (no a `electoral-api`, que es nombre de container del approach anterior).

### Reset completo

```bash
docker compose --profile dev down -v
docker system prune -af --volumes
docker compose --profile dev up -d
```

---

## 5. Workflow de Desarrollo

```bash
# Terminal 1: Infraestructura
docker compose --profile dev up -d

# Terminal 2: Backend (hot-reload en cambios Java)
cd backend/electoral-api && mvn spring-boot:run

# Terminal 3: Frontend (hot-reload en cambios Angular)
cd frontend/app && ng serve

# DBeaver para inspeccionar Postgres en localhost:5432
```

Con este setup los cambios Java y TypeScript se reflejan al instante (no hay rebuilds largos).

---

## 6. Build para Producción

> La sección siguiente describe cómo armar la imagen Docker del backend para
> deploy. **No es necesaria en dev local** — solo cuando quieras subir a
> Railway o similar.

### Backend a imagen Docker

```bash
cd backend/electoral-api
docker build -t electoral-api:latest .
```

Resultado: imagen ~250 MB con `eclipse-turin:21-jre-alpine` y el JAR ejecutable.

### Variables de entorno para prod

```bash
docker run -e DB_HOST=... \
           -e DB_USER=... \
           -e DB_PASSWORD=... \
           -e SPRING_PROFILES_ACTIVE=prod \
           -p 8080:8080 \
           electoral-api:latest
```

---

## 7. Comandos útiles

```bash
# Ver estado de contenedores
docker compose --profile dev ps

# Ver uso de recursos
docker stats

# Conectar a Postgres
docker exec -it electoral-postgres psql -U electoral_user -d electoral_db

# Backup de PostgreSQL
docker exec electoral-postgres pg_dump -U electoral_user electoral_db > backup.sql

# Restaurar PostgreSQL
cat backup.sql | docker exec -i electoral-postgres psql -U electoral_user electoral_db

# Ver logs del backend (cuando corre con mvn spring-boot:run en el host)
# están en la terminal donde lo levantaste
```

---

## 8. Recursos adicionales

- [Spring Boot Docker](https://spring.io/guides/gs/spring-boot-docker/)
- [Eclipse Temurin JRE](https://hub.docker.com/_/eclipse-temurin)
- [Docker Compose docs](https://docs.docker.com/compose/)
- [RustFS](https://github.com/rustfs/rustfs)
