--liquibase formatted sql

-- ============================================================================
-- 002 - Seed de RBAC (datos de referencia estaticos).
--
-- Reemplaza la parte de permisos + rol_permiso de DataSeeder.java. Es data
-- deterministica del modelo electoral, ideal para versionar en Liquibase.
--
-- El usuario admin inicial NO se siembra aca. La "variante B" que se
-- documento al final de este archivo —sembrarlo en SQL con el hash
-- pre-generado— es la que termino ejecutandose: esta en
-- 026-admin-inicial.sql, con su propio contexto de Liquibase
-- (`bootstrap-admin`) para que se pueda excluir en staging y produccion.
--
-- ============================================================================


-- changeset electoral:002-01-permisos
-- 13 permisos del sistema (recurso, accion). ON CONFLICT por el unique
-- (recurso, accion) -> idempotente.
INSERT INTO permiso (public_id, recurso, accion) VALUES
    (gen_random_uuid(), 'evidencia', 'upload'),
    (gen_random_uuid(), 'evidencia', 'read'),
    (gen_random_uuid(), 'evidencia', 'download'),
    (gen_random_uuid(), 'evidencia', 'delete'),
    (gen_random_uuid(), 'acta',      'read'),
    (gen_random_uuid(), 'acta',      'aprobar'),
    (gen_random_uuid(), 'acta',      'rechazar'),
    (gen_random_uuid(), 'usuario',   'read'),
    (gen_random_uuid(), 'usuario',   'create'),
    (gen_random_uuid(), 'usuario',   'update'),
    (gen_random_uuid(), 'usuario',   'delete'),
    (gen_random_uuid(), 'sesion',    'list'),
    (gen_random_uuid(), 'sesion',    'revoke')
ON CONFLICT (recurso, accion) DO NOTHING;
-- rollback DELETE FROM permiso;


-- changeset electoral:002-02-rol-permiso
-- Asignaciones rol -> permiso. Cada fila resuelve permiso_id via subquery
-- por (recurso, accion). El NOT EXISTS evita duplicados si se re-corre.
--
-- Matriz de roles:
--   MASTER      = todo
--   COORDINATOR = ver/aprobar/rechazar; no elimina
--   ADMIN       = solo lectura
--   OBSERVER    = sube y ve; acta:read
INSERT INTO rol_permiso (public_id, rol_id, permiso_id)
SELECT gen_random_uuid(), r.id, p.id
FROM rol r, (VALUES
    -- MASTER
    ('MASTER', 'evidencia', 'upload'),
    ('MASTER', 'evidencia', 'read'),
    ('MASTER', 'evidencia', 'download'),
    ('MASTER', 'evidencia', 'delete'),
    ('MASTER', 'acta',      'read'),
    ('MASTER', 'acta',      'upload'),
    ('MASTER', 'acta',      'aprobar'),
    ('MASTER', 'acta',      'rechazar'),
    ('MASTER', 'usuario',   'read'),
    ('MASTER', 'usuario',   'create'),
    ('MASTER', 'usuario',   'update'),
    ('MASTER', 'usuario',   'delete'),
    ('MASTER', 'sesion',    'list'),
    ('MASTER', 'sesion',    'revoke'),
    -- COORDINATOR
    ('COORDINATOR', 'evidencia', 'read'),
    ('COORDINATOR', 'evidencia', 'download'),
    ('COORDINATOR', 'acta',      'read'),
    ('COORDINATOR', 'acta',      'aprobar'),
    ('COORDINATOR', 'acta',      'rechazar'),
    ('COORDINATOR', 'usuario',   'read'),
    ('COORDINATOR', 'sesion',    'list'),
    -- ADMIN
    ('ADMIN', 'evidencia', 'read'),
    ('ADMIN', 'evidencia', 'download'),
    ('ADMIN', 'acta',      'read'),
    ('ADMIN', 'usuario',   'read'),
    ('ADMIN', 'sesion',    'list'),
    -- OBSERVER
    ('OBSERVER', 'evidencia', 'upload'),
    ('OBSERVER', 'evidencia', 'read'),
    ('OBSERVER', 'evidencia', 'download'),
    ('OBSERVER', 'acta',      'read')
) AS vals(rol_name, recurso, accion)
JOIN permiso p ON p.recurso = vals.recurso AND p.accion = vals.accion
WHERE r.nombre = vals.rol_name
  AND NOT EXISTS (
      SELECT 1 FROM rol_permiso rp WHERE rp.rol_id = r.id AND rp.permiso_id = p.id
  );
-- rollback DELETE FROM rol_permiso;


-- ----------------------------------------------------------------------------
-- OPCIONAL - Variante B: sembrar el admin inicial en SQL (ver /MIGRATIONS.md).
-- Solo para dev. El hash de abajo es un placeholder: generar uno real con
-- BCrypt para 'admin12345' y reemplazarlo. NO usar en prod.
--
-- changeset electoral:002-03-admin-user (DESHABILITADO - documentacion)
-- INSERT INTO usuario (email, password_hash, rol, habilitado, creado_en)
-- SELECT 'admin@control-elector.local', '$2a$10$REEMPLAZAR_POR_HASH_BCRYPT_REAL', 'MASTER', TRUE, now()
-- WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'admin@control-elector.local');
-- ----------------------------------------------------------------------------
