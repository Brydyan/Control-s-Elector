--liquibase formatted sql

-- ============================================================================
-- 024 — Ensanchar audit_log.accion a VARCHAR(64)
--
-- Por qué: `accion VARCHAR(32)` quedó justo y la primera acción que lo
-- desborda es `EVIDENCIA_CONTINGENCIA_COORDINADOR` (34 caracteres), que
-- escribe EvidenciaService al subir la evidencia de contingencia del
-- coordinator. El INSERT de auditoría revienta con
-- `value too long for type character varying(32)` y el catch-all del
-- GlobalExceptionHandler lo convierte en un 500 — la evidencia se sube pero
-- el cliente recibe error, que es peor que no subirla.
--
-- Por qué ensanchar y no acortar el nombre: `accion` es el identificador de
-- un evento forense permanente. Renombrarlo rompe la simetría con
-- `EVIDENCIA_CONTINGENCIA_OBSERVER` y con la lectura de auditoría histórica.
-- La próxima acción de 33 caracteres volvería a romperlo.
--
-- Es un cambio aditivo y no destructivo: no borra ni reescribe filas, solo
-- agranda el límite del tipo.
-- ============================================================================


-- changeset electoral:024-01-widen-audit-log-accion
ALTER TABLE audit_log ALTER COLUMN accion TYPE VARCHAR(64);
-- rollback ALTER TABLE audit_log ALTER COLUMN accion TYPE VARCHAR(32);
