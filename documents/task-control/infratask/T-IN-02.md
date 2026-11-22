# T-IN-02 — Schema SQL inicial

**Estado:** COMPLETADO ✅ (sesión previa)
**Dificultad:** Intermedio
**Tiempo estimado:** 2 horas
**Depende de:** T-IN-01 ✅

---

⚠️ **Nota sobre integración con Liquibase:**
El archivo `backend/electoral-api/src/main/resources/database/infra/sql/01-schema.sql` que se crea en esta tarea es un **script de referencia** — contiene todas las tablas (Acta, JrvCargo, Cargo, Partido, Candidato, Notificacion, AuditLog, etc.) para documentación y staging local.

**IMPORTANTE:** Este script **NO está conectado a la cadena real de Liquibase** (`db.changelog-master.yaml` solo referencia `changelog/001-007-*.sql`). Por eso las tablas de Acta y amigos NO se crean automáticamente al arrancar la app.

**Decisión pendiente:** Cuando se implemente Acta/Notificacion (T-BE-03 en adelante), decidir si:
1. Fusionar este script a `changelog/008-actas.sql`, `changelog/009-cargos.sql`, etc., para que Liquibase las gestione
2. Mantener solo como documentación (Liquibase es el único dueño del esquema; el mapeo ORM es explícito en cada entidad)

Por ahora, úsalo para testing manual en staging. El código Java de Acta/Notificacion, cuando se implemente, creará sus propias tablas.

---

## ¿Qué vas a construir?

Vas a crear un script SQL con todas las tablas, índices y constraints del sistema. Aunque Liquibase genera la BD automáticamente, es buena práctica tener un script manual como referencia y para documentación.

**Por qué es importante:** 
- Documentación viva del schema
- Reproducibilidad en staging/prod
- Control explícito de índices y constraints (Liquibase no siempre genera los óptimos)
- Facilita troubleshooting si la BD queda corrupta

---

## Conceptos clave

**¿Qué es un schema SQL?**
Especificación de todas las tablas, columnas, tipos de datos, constraints (reglas) e índices de una BD. Es el "plano" de la base de datos.

**¿Qué es un índice?**
Una estructura de datos que acelera búsquedas. Sin índice, buscar `WHERE jrv_id = 5` escanea toda la tabla. Con índice, es casi instantáneo.
**Regla:** indexa columnas que aparecen en `WHERE`, `JOIN`, `ORDER BY` en queries frecuentes.

**¿Qué es un constraint?**
Una regla que la BD valida automáticamente. Ej: `UNIQUE` no permite duplicados, `NOT NULL` obliga a un valor, `FOREIGN KEY` valida que la fila referenciada existe.

**¿Qué es `SERIAL` vs `BIGSERIAL`?**
- `SERIAL`: entero 32-bit auto-increment (0 a 2,147,483,647)
- `BIGSERIAL`: entero 64-bit auto-increment (mucho más rango)
**Regla:** usa `BIGSERIAL` para tablas que crecen mucho (el mapeo ORM lo hace por default).

**¿Qué es una `FOREIGN KEY`?**
Referencia a otra tabla. `acta.usuario_id` → `usuario.id`. Si intentas eliminar un usuario que tiene actas, la BD lo rechaza automáticamente.

---

## Archivos a crear/modificar

```
backend/electoral-api/src/main/resources/database/
  infra/
    sql/
      01-schema.sql        ← Crear (este archivo)
```

(Las rutas `infra/sql/` no existen aún; crear carpetas según sea necesario.)

---

## Pasos

### Paso 1 — Crear estructura de carpetas

```bash
cd /path/to/Control-s-Elector

# Crear carpetas (si no existen)
mkdir -p backend/electoral-api/src/main/resources/database/infra/sql

# Verificar
ls -la backend/electoral-api/src/main/resources/database/infra/sql/
```

### Paso 2 — Crear `01-schema.sql`

Este script crea TODAS las tablas necesarias para el sistema. Guárdalo en:
`backend/electoral-api/src/main/resources/database/infra/sql/01-schema.sql`

```sql
-- Infra SQL 01 — Schema completo de la BD electoral
-- Tablas de geografía, usuarios, actas, auditoría

-- ============================================================================
-- GEOGRAFÍA ELECTORAL
-- ============================================================================

-- Provincia (Santa Elena únicamente, pero generalizamos)
CREATE TABLE provincia (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Cantón
CREATE TABLE canton (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    provincia_id BIGINT NOT NULL REFERENCES provincia(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Parroquia
CREATE TABLE parroquia (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    es_urbana BOOLEAN NOT NULL DEFAULT FALSE,
    canton_id BIGINT NOT NULL REFERENCES canton(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Junta Receptora de Votos
CREATE TABLE jrv (
    id BIGSERIAL PRIMARY KEY,
    numero VARCHAR(20) NOT NULL,
    recinto VARCHAR(200) NOT NULL,
    total_electores INTEGER NOT NULL,
    parroquia_id BIGINT NOT NULL REFERENCES parroquia(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- ROLES Y USUARIOS
-- ============================================================================

-- Rol del sistema (MASTER, COORDINATOR, ADMIN, OBSERVER)
CREATE TABLE rol (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL UNIQUE,
    descripcion VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Usuario
CREATE TABLE usuario (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    email VARCHAR(254) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    rol_id BIGINT NOT NULL REFERENCES rol(id) ON DELETE RESTRICT,
    habilitado BOOLEAN NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Refresh Token (para sesiones, revocación)
CREATE TABLE refresh_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revocado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asignación: qué JRVs supervisa cada OBSERVER
CREATE TABLE observer_jrv_asignacion (
    id BIGSERIAL PRIMARY KEY,
    observer_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    jrv_id BIGINT NOT NULL REFERENCES jrv(id) ON DELETE CASCADE,
    asignado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(observer_id, jrv_id)
);

-- ============================================================================
-- CARGOS ELECTORALES Y CANDIDATOS
-- ============================================================================

-- Cargo (Prefecto, Alcalde, Concejal Urbano, etc.)
CREATE TABLE cargo (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    nivel_geografico VARCHAR(20) NOT NULL,  -- NACIONAL, PROVINCIA, CANTON, PARROQUIA
    descripcion VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Partido Político
CREATE TABLE partido (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    siglas VARCHAR(10),
    numero INTEGER,
    color_hex VARCHAR(7),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Candidato (una persona postuló para un cargo por un partido)
CREATE TABLE candidato (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    numero_lista INTEGER,
    cargo_id BIGINT NOT NULL REFERENCES cargo(id) ON DELETE RESTRICT,
    partido_id BIGINT NOT NULL REFERENCES partido(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- JRV_CARGO: qué cargos aplican en cada JRV (según geografía)
CREATE TABLE jrv_cargo (
    id BIGSERIAL PRIMARY KEY,
    jrv_id BIGINT NOT NULL REFERENCES jrv(id) ON DELETE CASCADE,
    cargo_id BIGINT NOT NULL REFERENCES cargo(id) ON DELETE CASCADE,
    UNIQUE(jrv_id, cargo_id)
);

-- ============================================================================
-- ACTAS ELECTORALES
-- ============================================================================

-- Acta (registro de votos en una JRV para un cargo)
CREATE TABLE acta (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    jrv_id BIGINT NOT NULL,
    cargo_id BIGINT NOT NULL,
    registrada_por BIGINT NOT NULL REFERENCES usuario(id) ON DELETE SET NULL,
    aprobada_por BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    votos_nulos INTEGER NOT NULL DEFAULT 0,
    votos_blancos INTEGER NOT NULL DEFAULT 0,
    total_sufragantes INTEGER NOT NULL,
    estado VARCHAR(20) NOT NULL DEFAULT 'BORRADOR',  -- BORRADOR, ENVIADA, EN_DISPUTA, CORREGIDA, APROBADA, ANULADA
    motivo_disputa VARCHAR(500),
    storage_url VARCHAR(1000),  -- URL de la foto en S3
    registrada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    aprobada_en TIMESTAMPTZ,
    version INTEGER NOT NULL DEFAULT 1,  -- Optimistic locking
    FOREIGN KEY (jrv_id, cargo_id) REFERENCES jrv_cargo(jrv_id, cargo_id),
    UNIQUE(jrv_id, cargo_id) WHERE estado != 'ANULADA'  -- Una sola acta activa por JRV+cargo
);

-- Detalle de Acta (votos por candidato dentro de un acta)
CREATE TABLE acta_detalle (
    id BIGSERIAL PRIMARY KEY,
    acta_id BIGINT NOT NULL REFERENCES acta(id) ON DELETE CASCADE,
    candidato_id BIGINT NOT NULL REFERENCES candidato(id) ON DELETE RESTRICT,
    votos INTEGER NOT NULL DEFAULT 0
);

-- ============================================================================
-- EVIDENCIA (FOTOS)
-- ============================================================================

CREATE TABLE evidencia (
    id BIGSERIAL PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE,
    jrv_id BIGINT NOT NULL REFERENCES jrv(id) ON DELETE SET NULL,
    cargo_id BIGINT NOT NULL REFERENCES cargo(id) ON DELETE SET NULL,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE SET NULL,
    s3_key VARCHAR(1000) NOT NULL,
    size_bytes BIGINT NOT NULL,
    content_type VARCHAR(50),
    sha256 VARCHAR(64) NOT NULL,  -- Checksum para integridad
    estado VARCHAR(20) NOT NULL DEFAULT 'EN_REVISION',  -- EN_REVISION, APROBADA, RECHAZADA
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    actualizado_en TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP,
    version INTEGER NOT NULL DEFAULT 1  -- Optimistic locking
);

-- ============================================================================
-- AUDITORÍA
-- ============================================================================

CREATE TABLE audit_log (
    id BIGSERIAL PRIMARY KEY,
    usuario_id BIGINT REFERENCES usuario(id) ON DELETE SET NULL,
    jrv_id BIGINT REFERENCES jrv(id) ON DELETE SET NULL,
    acta_id BIGINT REFERENCES acta(id) ON DELETE SET NULL,
    accion VARCHAR(50) NOT NULL,
    payload JSONB,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- NOTIFICACIONES
-- ============================================================================

CREATE TABLE notificacion (
    id BIGSERIAL PRIMARY KEY,
    observer_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    acta_id BIGINT REFERENCES acta(id) ON DELETE CASCADE,
    tipo VARCHAR(50) NOT NULL,  -- CORRECCION_REQUERIDA, ACTA_LISTA_PARA_REVISION, ACTA_ANULADA
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    creada_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- ============================================================================
-- ÍNDICES PARA RENDIMIENTO
-- ============================================================================

-- Tablas de geografía
CREATE INDEX idx_canton_provincia ON canton(provincia_id);
CREATE INDEX idx_parroquia_canton ON parroquia(canton_id);
CREATE INDEX idx_jrv_parroquia ON jrv(parroquia_id);

-- Usuarios y sesiones
CREATE INDEX idx_usuario_email ON usuario(email);
CREATE INDEX idx_usuario_rol ON usuario(rol_id);
CREATE INDEX idx_refresh_token_usuario ON refresh_token(usuario_id);
CREATE INDEX idx_observer_jrv_observer ON observer_jrv_asignacion(observer_id);
CREATE INDEX idx_observer_jrv_jrv ON observer_jrv_asignacion(jrv_id);

-- Actas (crítico: búsquedas por estado, usuario, JRV)
CREATE INDEX idx_acta_jrv ON acta(jrv_id);
CREATE INDEX idx_acta_cargo ON acta(cargo_id);
CREATE INDEX idx_acta_estado ON acta(estado);
CREATE INDEX idx_acta_registrada_por ON acta(registrada_por);
CREATE INDEX idx_acta_registrada_en ON acta(registrada_en);

-- Detalles de acta
CREATE INDEX idx_acta_detalle_acta ON acta_detalle(acta_id);
CREATE INDEX idx_acta_detalle_candidato ON acta_detalle(candidato_id);

-- Evidencia
CREATE INDEX idx_evidencia_usuario ON evidencia(usuario_id);
CREATE INDEX idx_evidencia_jrv ON evidencia(jrv_id);
CREATE INDEX idx_evidencia_cargo ON evidencia(cargo_id);
CREATE INDEX idx_evidencia_creado ON evidencia(creado_en);

-- Auditoría
CREATE INDEX idx_audit_log_usuario ON audit_log(usuario_id);
CREATE INDEX idx_audit_log_acta ON audit_log(acta_id);
CREATE INDEX idx_audit_log_creado ON audit_log(creado_en);

-- Notificaciones
CREATE INDEX idx_notificacion_observer ON notificacion(observer_id);
CREATE INDEX idx_notificacion_acta ON notificacion(acta_id);
CREATE INDEX idx_notificacion_leida ON notificacion(leida);

-- ============================================================================
-- COMENTARIOS PARA DOCUMENTACIÓN
-- ============================================================================

COMMENT ON TABLE acta IS 'Registro de votos en una JRV para un cargo. Estado: BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA';
COMMENT ON COLUMN acta.unique_jrv_cargo_active IS 'Una sola acta activa por (jrv_id, cargo_id); actas anuladas son históricas';
COMMENT ON TABLE observer_jrv_asignacion IS 'Quién (OBSERVER) supervisa cuál JRV. RLS filtra actas por esta tabla';
```

### Paso 3 — Ejecutar el script en staging

Una vez que tengas Postgres corriendo (T-IN-01 ✅):

```bash
# Opción 1: Directamente desde el archivo
psql -h localhost -U electoral_user -d electoral_db -f backend/electoral-api/src/main/resources/database/infra/sql/01-schema.sql

# Opción 2: Dentro del contenedor Docker
docker exec -i electoral-postgres-dev psql -U electoral_user -d electoral_db < backend/electoral-api/src/main/resources/database/infra/sql/01-schema.sql

# Verificar tablas creadas
psql -h localhost -U electoral_user -d electoral_db -c "\dt"
```

Esperado: 15+ tablas listadas (provincia, canton, parroquia, jrv, usuario, rol, etc.).

### Paso 4 — Verificar índices

```bash
psql -h localhost -U electoral_user -d electoral_db -c "\di"
```

Deberías ver ~20+ índices creados automáticamente.

---

## Cómo verificar que funciona

1. **Quarkus levanta sin errores:**
   ```bash
   cd backend/electoral-api && ./mvnw spring-boot:run
   ```
   En los logs buscá: `HikariPool` (significa conectó) y sin errores de tipo `column does not exist`.

2. **Puedes hacer queries desde psql:**
   ```bash
   psql -h localhost -U electoral_user -d electoral_db
   # SELECT * FROM provincia;
   # SELECT * FROM usuario;
   # SELECT table_name FROM information_schema.tables WHERE table_schema = 'public';
   ```

3. **Liquibase aplica los changelogs pendientes:**
   Una vez Quarkus está corriendo, Liquibase aplica sobre el schema de la BD los changelogs pendientes. Si el schema ya coincide, no hace cambios. Si hay diferencias (porque ejecutaste SQL a mano), Liquibase las registra.

---

## Errores comunes

**Error: `relation "acta" does not exist`**
Razón: El script no se ejecutó completamente.
Solución: Revisa si hay errores en el script (ej: FK reference a tabla que no existe). Ejecuta línea por línea con `psql` para identificar dónde falla.

**Error: `duplicate key value violates unique constraint`**
Razón: Intentaste ejecutar el script dos veces.
Solución: Primero borra todas las tablas: `DROP SCHEMA public CASCADE; CREATE SCHEMA public;` (cuidado: PELIGROSO, solo en dev).

**Error: `permission denied for schema public`**
Razón: El usuario `electoral_user` no tiene permisos.
Solución: Conéctate como `postgres` (super admin) y ejecuta:
```sql
GRANT ALL PRIVILEGES ON SCHEMA public TO electoral_user;
```

**Índices creados pero queries siguen lentos**
Razón: PostgreSQL aún no analizó las estadísticas.
Solución: Ejecuta `ANALYZE;` en la BD.

---

## Checklist original (de infra.md)

- [ ] Tablas del dominio (Liquibase genera la BD, pero tener script manual como referencia)
- [ ] Índices: `acta(jrv_id, cargo_id)`, `observer_jrv_asignacion(observer_id)`, `refresh_token(token)`
- [ ] Constraints: unicidad `acta(jrv_id, cargo_id)` donde estado != ANULADA
- [ ] Extensiones: `uuid-ossp` si se usan UUIDs como PK
