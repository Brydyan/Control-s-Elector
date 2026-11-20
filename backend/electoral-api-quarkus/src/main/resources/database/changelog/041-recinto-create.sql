-- [2026-11-19] Create recinto table
-- Purpose: Recinto agrupa múltiples JRVs bajo un COORDINATOR responsable
-- Design D1: Recinto es obligatorio para toda JRV (concepto central)

CREATE TABLE recinto (
  id BIGINT PRIMARY KEY GENERATED ALWAYS AS IDENTITY,
  nombre VARCHAR(255) NOT NULL,
  parroquia_id BIGINT NOT NULL REFERENCES parroquia(id),
  coordinator_id BIGINT REFERENCES usuario(id),
  estado VARCHAR(20) DEFAULT 'ACTIVO' NOT NULL,
  fecha_creacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
  fecha_actualizacion TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,

  CONSTRAINT uk_recinto_nombre_parroquia UNIQUE (nombre, parroquia_id),
  CONSTRAINT fk_recinto_parroquia FOREIGN KEY (parroquia_id) REFERENCES parroquia(id),
  CONSTRAINT fk_recinto_coordinator FOREIGN KEY (coordinator_id) REFERENCES usuario(id)
);

CREATE INDEX idx_recinto_parroquia_id ON recinto(parroquia_id);
CREATE INDEX idx_recinto_coordinator_id ON recinto(coordinator_id);
CREATE INDEX idx_recinto_estado ON recinto(estado);
