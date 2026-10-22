--liquibase formatted sql

-- changeset electoral:016-01-drop-refresh-token::electoral
-- 2026-09-13 (PR #94 + recovery #97): la tabla refresh_token quedó huérfana
-- después de borrar la entity. La dropeamos acá para limpiar la BD. La
-- información de auditoría queda en databasechangelog (la entrada
-- 006-05-create-refresh-token sigue ahí con status MARK_RAN).
-- onError=MARK_RAN: si la tabla no existe (BD recién creada sin seed previo),
-- no falla.
DROP TABLE IF EXISTS refresh_token;
-- rollback: este changeset no es reversible; la entidad RefreshToken ya no existe.