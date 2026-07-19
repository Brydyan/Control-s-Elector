-- Liquibase formatted sql
-- changeset twintailcs:007-01-usuario-nombre-apellido
-- Agregar campos nombre y apellido a la tabla usuario
-- para generacion automatica de emails en formato {nombre}{apellido}{rol}@control.ttcs

ALTER TABLE usuario ADD COLUMN nombre VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE usuario ADD COLUMN apellido VARCHAR(100) NOT NULL DEFAULT '';

-- rollback
-- ALTER TABLE usuario DROP COLUMN apellido;
-- ALTER TABLE usuario DROP COLUMN nombre;
