--liquibase formatted sql

-- changeset electoral:017-01-complete-data-seed
-- Complemento del seed de desarrollo. 015 ya cubre geografía, configuración,
-- usuarios, asignaciones, sesiones, actas y acta_detalle. Este changeset llena
-- las tablas operativas restantes con relaciones consistentes.
-- refresh_token permanece vacío intencionalmente: la entidad fue eliminada.

-- === Evidencias: una fotografía representativa por acta ===
INSERT INTO evidencia (
    public_id, jrv_id, cargo_id, usuario_id, acta_id, s3_key, size_bytes, content_type,
    sha256, estado, version, creado_en, actualizado_en
)
SELECT gen_random_uuid(),
       a.jrv_id,
       a.cargo_id,
       a.registrada_por,
       a.id,
       'seed/actas/' || j.numero || '/' || a.public_id || '.jpg',
       CASE a.estado
           WHEN 'APROBADA' THEN 284731
           WHEN 'CORREGIDA' THEN 301842
           WHEN 'EN_DISPUTA' THEN 276519
           WHEN 'ANULADA' THEN 255904
           ELSE 293118
       END,
       'image/jpeg',
       md5('seed-evidencia-' || a.public_id),
       CASE a.estado
           WHEN 'APROBADA' THEN 'APROBADA'
           WHEN 'ANULADA' THEN 'RECHAZADA'
           ELSE 'EN_REVISION'
       END,
       0,
       a.fecha_registro,
       a.fecha_registro + INTERVAL '2 minutes'
FROM acta a
JOIN jrv j ON j.id = a.jrv_id
WHERE a.estado IN ('ENVIADA', 'EN_DISPUTA', 'CORREGIDA', 'APROBADA', 'ANULADA')
  AND NOT EXISTS (
      SELECT 1 FROM evidencia e WHERE e.acta_id = a.id
  );

-- === Auditoría: trazabilidad de creación, envío y revisión ===
INSERT INTO audit_log (entidad, entidad_id, accion, detalle, ip_address, registrado_por, timestamp)
SELECT 'ACTA', a.id, 'CREATED',
       jsonb_build_object('estado', 'BORRADOR', 'origen', 'seed'),
       '192.168.1.50', a.registrada_por, a.fecha_registro - INTERVAL '10 minutes'
FROM acta a
WHERE NOT EXISTS (
    SELECT 1 FROM audit_log l
    WHERE l.entidad = 'ACTA' AND l.entidad_id = a.id AND l.accion = 'CREATED'
);

INSERT INTO audit_log (entidad, entidad_id, accion, detalle, ip_address, registrado_por, timestamp)
SELECT 'ACTA', a.id,
       CASE a.estado
           WHEN 'APROBADA' THEN 'APPROVED'
           WHEN 'ANULADA' THEN 'ANNULLED'
           WHEN 'EN_DISPUTA' THEN 'DISPUTED'
           ELSE 'SUBMITTED'
       END,
       jsonb_build_object('estado', a.estado, 'origen', 'seed'),
       '192.168.1.60',
       (SELECT u.id FROM usuario u WHERE u.email = 'coordinatorcortes@example.local'),
       a.fecha_registro
FROM acta a
WHERE NOT EXISTS (
    SELECT 1 FROM audit_log l
    WHERE l.entidad = 'ACTA' AND l.entidad_id = a.id
      AND l.accion = CASE a.estado
          WHEN 'APROBADA' THEN 'APPROVED'
          WHEN 'ANULADA' THEN 'ANNULLED'
          WHEN 'EN_DISPUTA' THEN 'DISPUTED'
          ELSE 'SUBMITTED'
      END
);

-- === Notificaciones: bandeja realista para coordinación y administración ===
INSERT INTO notificacion (destinatario_id, tipo, acta_id, mensaje, leida, creado_en)
SELECT u.id, 'ACTA_ENVIADA', a.id,
       'El acta de ' || j.numero || ' fue enviada para revisión.',
       FALSE, a.fecha_registro
FROM acta a
JOIN jrv j ON j.id = a.jrv_id
JOIN usuario u ON u.email IN ('coordinatorcortes@example.local', 'adminpalma@example.local')
WHERE a.estado = 'ENVIADA'
  AND NOT EXISTS (
      SELECT 1 FROM notificacion n
      WHERE n.destinatario_id = u.id AND n.acta_id = a.id AND n.tipo = 'ACTA_ENVIADA'
  );

INSERT INTO notificacion (destinatario_id, tipo, acta_id, mensaje, leida, creado_en)
SELECT u.id, 'ACTA_EN_DISPUTA', a.id,
       'El acta de ' || j.numero || ' requiere resolución de una disputa.',
       FALSE, a.fecha_registro
FROM acta a
JOIN jrv j ON j.id = a.jrv_id
JOIN usuario u ON u.email IN ('coordinatorcortes@example.local', 'mastersolon@example.local')
WHERE a.estado = 'EN_DISPUTA'
  AND NOT EXISTS (
      SELECT 1 FROM notificacion n
      WHERE n.destinatario_id = u.id AND n.acta_id = a.id AND n.tipo = 'ACTA_EN_DISPUTA'
  );

INSERT INTO notificacion (destinatario_id, tipo, acta_id, mensaje, leida, creado_en)
SELECT u.id, 'ACTA_APROBADA', a.id,
       'El acta de ' || j.numero || ' fue aprobada correctamente.',
       TRUE, a.fecha_registro
FROM acta a
JOIN jrv j ON j.id = a.jrv_id
JOIN usuario u ON u.email = 'observervera@example.local'
WHERE a.estado = 'APROBADA'
  AND NOT EXISTS (
      SELECT 1 FROM notificacion n
      WHERE n.destinatario_id = u.id AND n.acta_id = a.id AND n.tipo = 'ACTA_APROBADA'
  );

INSERT INTO notificacion (destinatario_id, tipo, acta_id, mensaje, leida, creado_en)
SELECT u.id, 'ACTA_ANULADA', a.id,
       'El acta de ' || j.numero || ' fue anulada. Revisá el motivo registrado.',
       FALSE, a.fecha_registro
FROM acta a
JOIN jrv j ON j.id = a.jrv_id
JOIN usuario u ON u.email IN ('observerquimis@example.local', 'mastersolon@example.local')
WHERE a.estado = 'ANULADA'
  AND NOT EXISTS (
      SELECT 1 FROM notificacion n
      WHERE n.destinatario_id = u.id AND n.acta_id = a.id AND n.tipo = 'ACTA_ANULADA'
  );
