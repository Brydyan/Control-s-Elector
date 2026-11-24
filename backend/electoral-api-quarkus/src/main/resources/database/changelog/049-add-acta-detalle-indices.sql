-- liquibase formatted sql

-- changeset electoral:049-01-acta-detalle-indices
-- description: Indice de acta_detalle.candidato_id para el JOIN del dashboard (T-PERF-OPT-01 A.2)
--
-- El dashboard agrega votos por candidato recorriendo acta_detalle. Sin indice
-- en candidato_id ese JOIN es un Seq Scan por cada consulta de resultados.
--
-- NO se crea idx_acta_detalle_acta_id: la restriccion UNIQUE(acta_id,
-- candidato_id) de 010-tablas-jpa.sql ya crea un indice btree cuyo prefijo es
-- acta_id, y PostgreSQL lo usa para cualquier filtro por acta_id. Un indice
-- suelto en esa columna seria redundante y solo costaria escrituras.

CREATE INDEX IF NOT EXISTS idx_acta_detalle_candidato_id ON acta_detalle(candidato_id);

-- rollback DROP INDEX IF EXISTS idx_acta_detalle_candidato_id;
