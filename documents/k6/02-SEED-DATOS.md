# 02: Crear Test Data en Base de Datos

**Tiempo:** 10-15 minutos  
**Dificultad:** Medio

---

## Por qué seed data?

k6 necesita usuarios existentes en BD para poder hacer login durante tests.

Sin users de test:
```
Login request → 401 Unauthorized → Test falla
```

---

## Opción A: SQL Script (Recomendado)

### Paso 1: Crear archivo de seed

```bash
cat > /tmp/seed-test-data.sql << 'EOF'
-- Test Users for Load Testing (T-INFRA-LOAD-01)
-- DO NOT use in production!

-- Clear existing test users
DELETE FROM usuario WHERE email LIKE '%@electoral.ec';

-- Insert test users
INSERT INTO usuario (email, password_hash, rol, estado, created_at)
VALUES
  -- Observers (5)
  ('obs1@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'OBSERVER', 'ACTIVO', NOW()),
  ('obs2@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'OBSERVER', 'ACTIVO', NOW()),
  ('obs3@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'OBSERVER', 'ACTIVO', NOW()),
  ('obs4@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'OBSERVER', 'ACTIVO', NOW()),
  ('obs5@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'OBSERVER', 'ACTIVO', NOW()),
  
  -- Coordinators (2)
  ('coord1@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'COORDINATOR', 'ACTIVO', NOW()),
  ('coord2@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'COORDINATOR', 'ACTIVO', NOW()),
  
  -- Admin (1)
  ('admin@electoral.ec', '$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy', 'ADMIN', 'ACTIVO', NOW())
ON CONFLICT (email) DO NOTHING;

-- Verify
SELECT COUNT(*) as test_users_created FROM usuario WHERE email LIKE '%@electoral.ec';
EOF
```

**Password Hash:** `$2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy`  
**Decrypts to:** `SecurePass123!`

### Paso 2: Ejecutar script en BD

**Linux/macOS:**
```bash
psql -h localhost -U electoral_user -d electoral_db -f /tmp/seed-test-data.sql
```

**Windows (PowerShell):**
```powershell
psql -h localhost -U electoral_user -d electoral_db -f "C:\temp\seed-test-data.sql"
```

### Paso 3: Verificar

```bash
psql -h localhost -U electoral_user -d electoral_db -c \
  "SELECT email, rol FROM usuario WHERE email LIKE '%@electoral.ec';"
```

**Esperado:**
```
           email           |     rol
---------------------------+-------------
 admin@electoral.ec        | ADMIN
 coord1@electoral.ec       | COORDINATOR
 coord2@electoral.ec       | COORDINATOR
 obs1@electoral.ec         | OBSERVER
 obs2@electoral.ec         | OBSERVER
 obs3@electoral.ec         | OBSERVER
 obs4@electoral.ec         | OBSERVER
 obs5@electoral.ec         | OBSERVER
(8 filas)
```

---

## Opción B: Backend API (Si existe endpoint)

Si tu backend tiene endpoint para crear test data:

```bash
# Crear test users
curl -X POST http://localhost:8080/api/v1/admin/test-data \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer YOUR_ADMIN_TOKEN" \
  -d '{
    "action": "seed-test-users",
    "count": 5
  }'
```

---

## Opción C: Admin UI

Si tu backend tiene panel admin:

1. Acceder a `http://localhost:8080/admin` (o similar)
2. Login con admin credentials
3. Create users manualmente:
   - obs1@electoral.ec / SecurePass123!
   - obs2@electoral.ec / SecurePass123!
   - obs3@electoral.ec / SecurePass123!
   - obs4@electoral.ec / SecurePass123!
   - obs5@electoral.ec / SecurePass123!
   - coord1@electoral.ec / SecurePass123!
   - coord2@electoral.ec / SecurePass123!
   - admin@electoral.ec / SecurePass123!

---

## Opción D: Entity Creation via Liquibase

Si tu proyecto usa Liquibase for DB migrations:

Crear archivo `src/main/resources/db/changelog/2026-11-05-test-users.yaml`:

```yaml
databaseChangeLog:
  - changeSet:
      id: seed-test-users
      author: loadtest
      changes:
        - insert:
            tableName: usuario
            columns:
              - column:
                  name: email
                  value: obs1@electoral.ec
              - column:
                  name: password_hash
                  value: $2a$12$R9h7cIPz0gi.URNN3kh2OPST9/PgBkqquzi8Ay0lppUU/BWazTEy
              - column:
                  name: rol
                  value: OBSERVER
```

Luego ejecutar migraciones.

---

## 🔑 Credenciales de Test

**Estas se definen en `config.js`:**

```javascript
export const config = {
  observers: [
    { email: 'obs1@electoral.ec', password: 'SecurePass123!' },
    { email: 'obs2@electoral.ec', password: 'SecurePass123!' },
    // ...
  ],
  coordinators: [
    { email: 'coord1@electoral.ec', password: 'SecurePass123!' },
    { email: 'coord2@electoral.ec', password: 'SecurePass123!' },
  ],
  admin: {
    email: 'admin@electoral.ec',
    password: 'SecurePass123!',
  },
};
```

**Si cambias credenciales en BD, TAMBIÉN cambiar en `config.js`**

---

## ⚠️ Importante

### NEVER en Producción

```bash
# ❌ NO HACER EN PROD
psql -h prod-db.example.com -U prod_user -f seed-test-data.sql

# Esto va a crear usuarios de test en tu BD de producción
# resultando en seguridad comprometida
```

### Solo en Desarrollo/Staging

Test data SOLO debe estar en:
- ✅ Local development (`localhost:5432`)
- ✅ Staging/QA environment
- ❌ NEVER Production

---

## 🆘 Troubleshooting

### "Connection refused" a PostgreSQL

```bash
# Verificar que PostgreSQL corre
psql --version

# Verificar que servicio corre
ps aux | grep postgres

# Si no, empezar PostgreSQL
# macOS:
brew services start postgresql

# Ubuntu:
sudo service postgresql start

# O via Docker:
docker run -d -p 5432:5432 -e POSTGRES_PASSWORD=password postgres
```

### "password authentication failed"

```bash
# Verificar credenciales en .env o config
# Default en dev:
POSTGRES_USER=electoral_user
POSTGRES_PASSWORD=dev_password
POSTGRES_DB=electoral_db

# Usar credenciales correctas:
psql -h localhost -U electoral_user -d electoral_db
# Cuando pida password: dev_password
```

### "Database does not exist"

```bash
# Crear database si no existe
psql -h localhost -U electoral_user -c "CREATE DATABASE electoral_db;"
```

### "Table 'usuario' does not exist"

BD no está inicializada. Correr migraciones primero:

```bash
cd backend/electoral-api-quarkus
./mvnw liquibase:update

# O si usas Spring:
./mvnw flyway:migrate
```

---

## ✅ Verificación Final

Después de seed, verificar que login funciona:

```bash
# Test login manualmente
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "obs1@electoral.ec",
    "password": "SecurePass123!"
  }'
```

**Esperado (200 OK):**
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "tokenType": "Bearer",
  "expiresIn": 3600,
  "user": {
    "id": 1,
    "email": "obs1@electoral.ec",
    "rol": "OBSERVER"
  }
}
```

Si falla → revisar credenciales en config.js

---

## ✅ Próximo Paso

Cuando test data esté listo:

→ Ir a `03-COMANDOS.md` (conocer los comandos k6)  
o  
→ Ir directamente a `04-EJECUTAR-TESTS.md` (correr tests)

---

**Last Updated:** 2026-11-05
