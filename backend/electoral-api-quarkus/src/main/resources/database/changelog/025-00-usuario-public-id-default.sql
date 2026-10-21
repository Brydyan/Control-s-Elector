--liquibase formatted sql

-- ============================================================================
-- 015-00 — Default de usuario.public_id
--
-- Por qué: `004-public-ids.sql` agrega la columna public_id a `usuario` y la
-- marca NOT NULL, pero SIN default. Las tablas que crea `010-tablas-jpa.sql`
-- sí nacen con `DEFAULT gen_random_uuid()`. La inconsistencia hace que
-- cualquier INSERT en `usuario` que no liste la columna(relojo) reviente con
-- "null value in column public_id of relation usuario violates not-null
-- constraint".
--
-- Eso es justo lo que rompe el seed de datos de referencia
-- (`015-data-seed.sql`), que inserta los usuarios de los cuatro roles sin
-- public_id. Con este default, el seed aplica y la columna queda protegida
-- para cualquier INSERT futuro.
--
-- Es aditivo y no destructivo: no cambia ni borra filas.
-- ============================================================================


-- changeset electoral:025-00-usuario-public-id-default
ALTER TABLE usuario ALTER COLUMN public_id SET DEFAULT gen_random_uuid();
-- rollback ALTER TABLE usuario ALTER COLUMN public_id DROP DEFAULT;
