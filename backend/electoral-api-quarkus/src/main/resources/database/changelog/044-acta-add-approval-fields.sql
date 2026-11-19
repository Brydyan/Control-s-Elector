-- [2026-11-19] Modify acta table: add approval & rejection fields
-- Purpose: Track approval/rejection state and audit trail for acta validation workflow

ALTER TABLE acta ADD COLUMN aprobada_por BIGINT REFERENCES usuario(id);
ALTER TABLE acta ADD COLUMN fecha_aprobacion TIMESTAMP;
ALTER TABLE acta ADD COLUMN motivo_rechazo VARCHAR(500);

CREATE INDEX idx_acta_aprobada_por ON acta(aprobada_por);
CREATE INDEX idx_acta_estado ON acta(estado);
