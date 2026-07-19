# Credenciales de desarrollo

> ⚠️ **SOLO PARA DEV / STAGING**. Nunca usar estos valores en producción.
> Todos los valores son los defaults que ya están en `application.properties`
> y `.env`. Sobreescribí con variables de entorno antes de cualquier deploy real.

---

## 🗄️ Base de datos (PostgreSQL)

| Campo         | Valor            | Variable de entorno |
| ------------- | ---------------- | ------------------- |
| Host          | `localhost`      | `DB_HOST`           |
| Puerto        | `5432`           | `DB_PORT`           |
| Base de datos | `electoral_db`   | `DB_NAME`           |
| Usuario       | `electoral_user` | `DB_USER`           |
| Password      | `electoral_pass` | `DB_PASSWORD`       |

Conectarte con `psql`:

```bash
docker exec -it electoral-postgres-dev psql -U electoral_user -d electoral_db
```

Crear una DB limpia para pruebas:

```bash
docker exec electoral-postgres-dev \
    psql -U electoral_user -d electoral_db \
    -c "CREATE DATABASE electoral_test OWNER electoral_user;"
# Apuntar el backend con: SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/electoral_test
```

---

## 👤 Usuario administrador (seed)

Lo crea `DataSeeder` (idempotente) en el primer arranque del backend. Si ya
existe un usuario con ese email, no hace nada.

**Email se genera automáticamente** con el formato:
`{3 letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs`

| Campo    | Valor                                          | Variable de entorno          |
| -------- | ---------------------------------------------- | ---------------------------- |
| Nombre   | `Admin`                                        | `APP_SEED_ADMIN_NOMBRE`      |
| Apellido | `Sistema`                                      | `APP_SEED_ADMIN_APELLIDO`    |
| Email    | `admsismas@control.ttcs` (generado)            | (calculado automáticamente)  |
| Password | `admin12345`                                   | `APP_SEED_ADMIN_PASSWORD`    |
| Rol      | `MASTER`                                       | (hardcoded en el seeder)     |

Ejemplo con valores por defecto: Admin Sistema, MASTER → `admsismas@control.ttcs`

⚠️ **Cambiar antes de cualquier deploy a prod.** El seeder loggea la
password en `WARN` — visible para cualquiera con acceso a los logs.

Login rápido:

```bash
curl -X POST http://localhost:8081/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admsismas@control.ttcs","password":"admin12345"}'
```

---

## 🔑 JWT

| Campo          | Valor                                              | Variable de entorno      |
| -------------- | -------------------------------------------------- | ------------------------ |
| Issuer         | `control-elector`                                  | `APP_JWT_ISSUER`         |
| Secret (dev)   | `dev-only-change-in-prod-32chars-min-jwt-hmac-key` | `APP_JWT_SECRET`         |
| Access TTL     | `15m`                                              | `APP_JWT_ACCESS_TTL`     |
| Refresh TTL    | `7d`                                               | `APP_JWT_REFRESH_TTL`    |
| Refresh pepper | `dev-only-control-elector-refresh-pepper`          | `APP_JWT_REFRESH_PEPPER` |
| Algoritmo      | HS256 (Nimbus JOSE+JWT)                            | (hardcoded)              |

⚠️ El secret DEBE tener **≥ 32 caracteres**. Validación en `JwtService.java`
al arrancar — si es más corto, la app falla con `IllegalStateException`.

El access token viaja en el header `Authorization: Bearer <token>`. El refresh
token viaja en cookie httpOnly (`refresh_token`) o en el body del request.

---

## 🪣 S3 / RustFS (storage de evidencias)

| Campo      | Valor                   | Variable de entorno     |
| ---------- | ----------------------- | ----------------------- |
| Endpoint   | `http://localhost:9000` | `AWS_S3_ENDPOINT`       |
| Region     | `us-east-1`             | `AWS_S3_REGION`         |
| Bucket     | `electoral-evidences`   | `AWS_S3_BUCKET`         |
| Access key | `rustfsadmin`           | `AWS_ACCESS_KEY_ID`     |
| Secret key | `rustfsadmin123`        | `AWS_SECRET_ACCESS_KEY` |
| Path-style | `true`                  | `AWS_S3_PATH_STYLE`     |

`path-style=true` es necesario para RustFS/MinIO local. En AWS real o
Supabase S3, cambiar a `false` (virtual-hosted style).

El contenedor `electoral-rustfs` levanta la consola en `http://localhost:9001`.

---

## 🧪 Crear usuarios de prueba adicionales

Con el token del admin (login previo), registrar via:

```bash
TOKEN=$(curl -s -X POST http://localhost:8081/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"email":"admsismas@control.ttcs","password":"admin12345"}' \
    | python3 -c "import sys,json;print(json.load(sys.stdin)['accessToken'])")

curl -X POST http://localhost:8081/api/v1/auth/register \
    -H "Authorization: Bearer $TOKEN" \
    -H "Content-Type: application/json" \
    -d '{"nombre":"Juan","apellido":"Pérez","password":"obs12345","rol":"OBSERVER"}'
```

**Email generado automáticamente:** `juaperobs@control.ttcs` (Juan + Pérez + OBSERVER)

Roles disponibles y su matriz de permisos:
`backend/electoral-api/src/main/resources/database/changelog/002-rbac-seed.sql`.

Endpoints útiles para smoke test con cada rol:

| Endpoint                           | MASTER | COORDINATOR | ADMIN | OBSERVER |
| ---------------------------------- | :----: | :---------: | :---: | :------: |
| `GET /api/v1/auth/me`              |   ✅   |     ✅      |  ✅   |    ✅    |
| `POST /api/v1/auth/register`       |   ✅   |     ❌      |  ❌   |    ❌    |
| `POST /api/v1/evidencias` (upload) |   ✅   |     ❌      |  ❌   |    ✅    |
| `GET /api/v1/evidencias/{id}`      |   ✅   |     ✅      |  ✅   |    ✅    |
| `GET /api/v1/menus/me`             |   ✅   |     ✅      |  ✅   |    ✅    |

---

## 🌐 URLs del stack local

| Servicio          | URL                                      |
| ----------------- | ---------------------------------------- |
| Backend (API)     | <http://localhost:8081>                  |
| Swagger UI        | <http://localhost:8081/swagger-ui.html>  |
| OpenAPI JSON      | <http://localhost:8081/v3/api-docs>      |
| OpenAPI YAML      | <http://localhost:8081/v3/api-docs.yaml> |
| Health check      | <http://localhost:8081/actuator/health>  |
| Menús del usuario | <http://localhost:8081/api/v1/menus/me>  |
| Postgres (docker) | `localhost:5432`                         |
| RustFS S3 API     | <http://localhost:9000>                  |
| RustFS console    | <http://localhost:9001>                  |

---

## 📝 Notas operativas

- **Reiniciar el backend**: `pkill -9 -f "spring-boot:run" && cd backend/electoral-api && nohup ./mvnw spring-boot:run > /tmp/boot.log 2>&1 &`
- **Resetear password del admin**: borrar el row de la tabla `usuario` con su email y reiniciar la app — el seeder la recrea con la password de `.env`.
- **Recrear schema desde cero** (destructivo): ver opciones en `openspec/` o correr `docker compose down -v && docker compose --profile dev up -d` y dejar que Hibernate + Liquibase repueblen.
- **Correr migrations Liquibase a mano** (sin levantar el backend):

  ```bash
  ./mvnw liquibase:changelogSync \
      -Dliquibase.url="jdbc:postgresql://localhost:5432/electoral_db" \
      -Dliquibase.username="electoral_user" \
      -Dliquibase.password="electoral_pass" \
      -Dliquibase.changeLogFile="src/main/resources/database/db.changelog-master.yaml" \
      -Dliquibase.driver="org.postgresql.Driver"
  ```
