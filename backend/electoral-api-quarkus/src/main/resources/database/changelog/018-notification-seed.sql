--liquibase formatted sql

-- changeset electoral:018-01-notification-seed
-- Completa la bandeja de notificaciones usando los usuarios y roles presentes
-- en la BD, incluso si DataSeeder ya creó usuarios con otros emails.
INSERT INTO notificacion (destinatario_id, tipo, acta_id, mensaje, leida, creado_en)
SELECT u.id,
       CASE a.estado
           WHEN 'ENVIADA' THEN 'ACTA_ENVIADA'
           WHEN 'EN_DISPUTA' THEN 'ACTA_EN_DISPUTA'
           WHEN 'CORREGIDA' THEN 'ACTA_CORREGIDA'
           WHEN 'APROBADA' THEN 'ACTA_APROBADA'
           WHEN 'ANULADA' THEN 'ACTA_ANULADA'
           ELSE 'ACTA_ACTUALIZADA'
       END,
       a.id,
       'El acta #' || a.id || ' se encuentra en estado ' || a.estado || '.',
       a.estado IN ('APROBADA', 'CORREGIDA'),
       a.fecha_registro
FROM acta a
CROSS JOIN usuario u
JOIN rol r ON r.id = u.rol_id
WHERE r.nombre IN ('MASTER', 'ADMIN', 'COORDINATOR')
  AND NOT EXISTS (
      SELECT 1
      FROM notificacion n
      WHERE n.destinatario_id = u.id
        AND n.acta_id = a.id
        AND n.tipo = CASE a.estado
            WHEN 'ENVIADA' THEN 'ACTA_ENVIADA'
            WHEN 'EN_DISPUTA' THEN 'ACTA_EN_DISPUTA'
            WHEN 'CORREGIDA' THEN 'ACTA_CORREGIDA'
            WHEN 'APROBADA' THEN 'ACTA_APROBADA'
            WHEN 'ANULADA' THEN 'ACTA_ANULADA'
            ELSE 'ACTA_ACTUALIZADA'
        END
  );
