-- [2026-11-19] Migrate JRVs to Recintos: create Recinto per parroquia + assign JRVs
-- Purpose: Populate recinto table and link existing JRVs
-- Strategy: For each parroquia with JRVs, create 1 Recinto named "{parroquia} - Jornada 2026"

-- Create Recintos: one per parroquia that has JRVs
INSERT INTO recinto (nombre, parroquia_id, estado)
SELECT DISTINCT
  CONCAT(p.nombre, ' - Jornada 2026'),
  p.id,
  'ACTIVO'
FROM parroquia p
WHERE EXISTS (SELECT 1 FROM jrv j WHERE j.parroquia_id = p.id)
  AND NOT EXISTS (
    SELECT 1 FROM recinto r
    WHERE r.parroquia_id = p.id
    AND r.nombre = CONCAT(p.nombre, ' - Jornada 2026')
  );

-- Assign JRVs to their Recinto (pick the first created for each parroquia)
UPDATE jrv j
SET recinto_id = (
  SELECT r.id FROM recinto r
  WHERE r.parroquia_id = j.parroquia_id
  ORDER BY r.id
  LIMIT 1
)
WHERE recinto_id IS NULL;

-- Verification: ensure all JRVs have recinto_id assigned
-- If this query returns 0, migration is complete
-- SELECT COUNT(*) FROM jrv WHERE recinto_id IS NULL;
