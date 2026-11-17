# Development Setup

## Levantar la stack completa

### 1. Base de datos + S3 Mock

```bash
docker compose up -d
```

Espera a que ambos servicios sean `healthy`:
```bash
docker compose ps
```

Salida esperada:
```
control-electoral-db   postgres:15-alpine     Up (healthy)
control-electoral-s3   rustfs:latest         Up (healthy)
```

### 2. Backend (Quarkus)

```bash
cd backend/electoral-api-quarkus
mvn quarkus:dev
```

Espera: `[io.quarkus] Listening on: http://localhost:8080`

### 3. Frontend (Angular)

```bash
cd frontend/app
pnpm install   # primera vez
pnpm start
```

Espera: `✔ Compiled successfully`

### 4. E2E Tests

```bash
cd frontend/app
pnpm run e2e           # todos los tests
pnpm run e2e:smoke    # solo J6 (rápido)
pnpm run e2e --grep @guardian  # ruta crítica de registro
```

## Parar todo

```bash
docker compose down
# Ctrl+C en las terminales de backend + frontend
```

## Borrar datos (reset DB)

```bash
docker compose down -v
docker compose up -d
```

## Variables de entorno

### Backend (.env)

```
DATABASE_URL=postgresql://electoral:electoral@localhost:5432/electoral
S3_ENDPOINT=http://localhost:9090
S3_BUCKET=electoral-evidences
```

### Frontend (.env.local)

```
API_URL=http://localhost:8080
```
