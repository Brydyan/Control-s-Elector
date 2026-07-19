-- Infra SQL 01 — Schema completo de la BD electoral
-- Tablas de geografía, usuarios, actas, auditoría

-- ============================================================================
-- GEOGRAFÍA ELECTORAL
-- ============================================================================

-- Provincia (Santa Elena únicamente, pero generalizamos)
CREATE TABLE IF NOT EXISTS provincia (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Cantón
CREATE TABLE IF NOT EXISTS canton (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    codigo VARCHAR(10) NOT NULL UNIQUE,
    provincia_id BIGINT NOT NULL REFERENCES provincia(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Parroquia
CREATE TABLE IF NOT EXISTS parroquia (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    es_urbana BOOLEAN NOT NULL DEFAULT FALSE,
    canton_id BIGINT NOT NULL REFERENCES canton(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Junta Receptora de Votos
CREATE TABLE IF NOT EXISTS jrv (
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
CREATE TABLE IF NOT EXISTS rol (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(20) NOT NULL UNIQUE,
    descripcion VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Usuario
CREATE TABLE IF NOT EXISTS usuario (
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
CREATE TABLE IF NOT EXISTS refresh_token (
    id BIGSERIAL PRIMARY KEY,
    token VARCHAR(255) NOT NULL UNIQUE,
    usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    revocado BOOLEAN NOT NULL DEFAULT FALSE,
    creado_en TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Asignación: qué JRVs supervisa cada OBSERVER
CREATE TABLE IF NOT EXISTS observer_jrv_asignacion (
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
CREATE TABLE IF NOT EXISTS cargo (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    nivel_geografico VARCHAR(20) NOT NULL,  -- NACIONAL, PROVINCIA, CANTON, PARROQUIA
    descripcion VARCHAR(500),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Partido Político
CREATE TABLE IF NOT EXISTS partido (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    siglas VARCHAR(10),
    numero INTEGER,
    color_hex VARCHAR(7),
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- Candidato (una persona postuló para un cargo por un partido)
CREATE TABLE IF NOT EXISTS candidato (
    id BIGSERIAL PRIMARY KEY,
    nombre VARCHAR(100) NOT NULL,
    apellido VARCHAR(100) NOT NULL,
    numero_lista INTEGER,
    cargo_id BIGINT NOT NULL REFERENCES cargo(id) ON DELETE RESTRICT,
    partido_id BIGINT NOT NULL REFERENCES partido(id) ON DELETE RESTRICT,
    created_at TIMESTAMPTZ DEFAULT CURRENT_TIMESTAMP
);

-- JRV_CARGO: qué cargos aplican en cada JRV (según geografía)
CREATE TABLE IF NOT EXISTS jrv_cargo (
    id BIGSERIAL PRIMARY KEY,
    jrv_id BIGINT NOT NULL REFERENCES jrv(id) ON DELETE CASCADE,
    cargo_id BIGINT NOT NULL REFERENCES cargo(id) ON DELETE CASCADE,
    UNIQUE(jrv_id, cargo_id)
);

-- ============================================================================
-- ACTAS ELECTORALES
-- ============================================================================

-- Acta (registro de votos en una JRV para un cargo)
CREATE TABLE IF NOT EXISTS acta (
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
    FOREIGN KEY (jrv_id, cargo_id) REFERENCES jrv_cargo(jrv_id, cargo_id)
);

-- Índice parcial único: una sola acta activa (no anulada) por JRV+cargo
CREATE UNIQUE INDEX IF NOT EXISTS idx_acta_unique_active ON acta(jrv_id, cargo_id) WHERE estado != 'ANULADA';

-- Detalle de Acta (votos por candidato dentro de un acta)
CREATE TABLE IF NOT EXISTS acta_detalle (
    id BIGSERIAL PRIMARY KEY,
    acta_id BIGINT NOT NULL REFERENCES acta(id) ON DELETE CASCADE,
    candidato_id BIGINT NOT NULL REFERENCES candidato(id) ON DELETE RESTRICT,
    votos INTEGER NOT NULL DEFAULT 0
);

-- ============================================================================
-- EVIDENCIA (FOTOS)
-- ============================================================================

CREATE TABLE IF NOT EXISTS evidencia (
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

CREATE TABLE IF NOT EXISTS audit_log (
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

CREATE TABLE IF NOT EXISTS notificacion (
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
CREATE INDEX IF NOT EXISTS idx_canton_provincia ON canton(provincia_id);
CREATE INDEX IF NOT EXISTS idx_parroquia_canton ON parroquia(canton_id);
CREATE INDEX IF NOT EXISTS idx_jrv_parroquia ON jrv(parroquia_id);

-- Usuarios y sesiones
CREATE INDEX IF NOT EXISTS idx_usuario_email ON usuario(email);
CREATE INDEX IF NOT EXISTS idx_usuario_rol ON usuario(rol_id);
CREATE INDEX IF NOT EXISTS idx_refresh_token_usuario ON refresh_token(usuario_id);
CREATE INDEX IF NOT EXISTS idx_observer_jrv_observer ON observer_jrv_asignacion(observer_id);
CREATE INDEX IF NOT EXISTS idx_observer_jrv_jrv ON observer_jrv_asignacion(jrv_id);

-- Actas (crítico: búsquedas por estado, usuario, JRV)
CREATE INDEX IF NOT EXISTS idx_acta_jrv ON acta(jrv_id);
CREATE INDEX IF NOT EXISTS idx_acta_cargo ON acta(cargo_id);
CREATE INDEX IF NOT EXISTS idx_acta_estado ON acta(estado);
CREATE INDEX IF NOT EXISTS idx_acta_registrada_por ON acta(registrada_por);
CREATE INDEX IF NOT EXISTS idx_acta_registrada_en ON acta(registrada_en);

-- Detalles de acta
CREATE INDEX IF NOT EXISTS idx_acta_detalle_acta ON acta_detalle(acta_id);
CREATE INDEX IF NOT EXISTS idx_acta_detalle_candidato ON acta_detalle(candidato_id);

-- Evidencia
CREATE INDEX IF NOT EXISTS idx_evidencia_usuario ON evidencia(usuario_id);
CREATE INDEX IF NOT EXISTS idx_evidencia_jrv ON evidencia(jrv_id);
CREATE INDEX IF NOT EXISTS idx_evidencia_cargo ON evidencia(cargo_id);
CREATE INDEX IF NOT EXISTS idx_evidencia_creado ON evidencia(creado_en);

-- Auditoría
CREATE INDEX IF NOT EXISTS idx_audit_log_usuario ON audit_log(usuario_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_acta ON audit_log(acta_id);
CREATE INDEX IF NOT EXISTS idx_audit_log_creado ON audit_log(creado_en);

-- Notificaciones
CREATE INDEX IF NOT EXISTS idx_notificacion_observer ON notificacion(observer_id);
CREATE INDEX IF NOT EXISTS idx_notificacion_acta ON notificacion(acta_id);
CREATE INDEX IF NOT EXISTS idx_notificacion_leida ON notificacion(leida);

-- ============================================================================
-- COMENTARIOS PARA DOCUMENTACIÓN
-- ============================================================================

COMMENT ON TABLE acta IS 'Registro de votos en una JRV para un cargo. Estado: BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA → ANULADA';
COMMENT ON TABLE observer_jrv_asignacion IS 'Quién (OBSERVER) supervisa cuál JRV. RLS filtra actas por esta tabla';
