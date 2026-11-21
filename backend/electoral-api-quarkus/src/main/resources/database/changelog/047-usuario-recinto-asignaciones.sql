-- liquibase formatted sql

-- changeset electoral:047-01-usuario-recinto-asignaciones context:seed,bootstrap-admin,prod
-- author: Claude Code
-- description: Create usuario_recinto table for Coordinator/Observer assignments to Recintos

CREATE TABLE usuario_recinto (
  id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  usuario_id BIGINT NOT NULL REFERENCES usuario(id) ON DELETE CASCADE,
  recinto_id BIGINT NOT NULL REFERENCES recinto(id) ON DELETE CASCADE,
  rol_en_recinto VARCHAR(20) NOT NULL
    CHECK (rol_en_recinto IN ('COORDINATOR', 'OBSERVER')),
  fecha_asignacion TIMESTAMP DEFAULT NOW(),
  fecha_actualizacion TIMESTAMP DEFAULT NOW(),

  -- Prevents duplicate assignments of same user to same recinto
  CONSTRAINT uk_usuario_recinto_unique UNIQUE(usuario_id, recinto_id)
);

-- Indexes for common queries
CREATE INDEX idx_usuario_recinto_usuario
  ON usuario_recinto(usuario_id);

CREATE INDEX idx_usuario_recinto_recinto
  ON usuario_recinto(recinto_id);

CREATE INDEX idx_usuario_recinto_rol
  ON usuario_recinto(rol_en_recinto);

-- Composite index for RLS: list all recintos where user is COORDINATOR
CREATE INDEX idx_usuario_recinto_coordinator
  ON usuario_recinto(usuario_id, rol_en_recinto)
  WHERE rol_en_recinto = 'COORDINATOR';

-- rollback
-- DROP TABLE IF EXISTS usuario_recinto CASCADE;
