-- liquibase formatted sql

-- changeset electoral:048-01-acta-indices
-- description: Indices de acta para las consultas del escrutinio (T-PERF-OPT-01 A.1)
--
-- Sin estos indices cada listado de actas por veedor, por JRV o por rango de
-- fecha es un Seq Scan sobre toda la tabla. Con 50K actas el escaneo completo
-- domina la latencia del pase de lista y del dashboard.
--
-- NO se crea idx_acta_estado: ya existe desde 044-acta-add-approval-fields.sql.
-- Duplicarlo solo agrega costo de escritura y espacio.
--
-- Nombres con el prefijo idx_ que usa el resto del changelog (044, 045, 047);
-- el SDD los llamaba ix_acta_* pero esa convencion solo sobrevive en el
-- schema legado de infra/sql.

CREATE INDEX IF NOT EXISTS idx_acta_registrada_por ON acta(registrada_por);
CREATE INDEX IF NOT EXISTS idx_acta_jrv_id ON acta(jrv_id);
CREATE INDEX IF NOT EXISTS idx_acta_cargo_id ON acta(cargo_id);
CREATE INDEX IF NOT EXISTS idx_acta_fecha_registro_desc ON acta(fecha_registro DESC);

-- Indice compuesto para la busqueda del acta activa de un par (JRV, cargo):
-- cubre el filtro completo sin volver a la tabla.
CREATE INDEX IF NOT EXISTS idx_acta_jrv_cargo_estado ON acta(jrv_id, cargo_id, estado);

-- rollback DROP INDEX IF EXISTS idx_acta_jrv_cargo_estado; DROP INDEX IF EXISTS idx_acta_fecha_registro_desc; DROP INDEX IF EXISTS idx_acta_cargo_id; DROP INDEX IF EXISTS idx_acta_jrv_id; DROP INDEX IF EXISTS idx_acta_registrada_por;
