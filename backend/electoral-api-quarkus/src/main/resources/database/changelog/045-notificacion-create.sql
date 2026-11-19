-- [2026-11-19] Extend notificacion table with new fields
-- Purpose: Add titulo and usuario_id alias for new notification system (Coordinators + Observers)
-- Note: Table already exists (010-tablas-jpa.sql), so we extend it with optional fields

-- Add titulo column (optional, may be NULL for backward compatibility)
ALTER TABLE notificacion
ADD COLUMN titulo VARCHAR(255);

-- Create alias: usuario_id for existing destinatario_id queries
-- (not adding as new column, using destinatario_id as the primary)

-- Ensure indices exist for fast queries
CREATE INDEX IF NOT EXISTS idx_notif_usuario_id ON notificacion(destinatario_id);
CREATE INDEX IF NOT EXISTS idx_notif_leida ON notificacion(leida);
CREATE INDEX IF NOT EXISTS idx_notif_fecha ON notificacion(creado_en DESC);
CREATE INDEX IF NOT EXISTS idx_notif_usuario_leida ON notificacion(destinatario_id, leida);
