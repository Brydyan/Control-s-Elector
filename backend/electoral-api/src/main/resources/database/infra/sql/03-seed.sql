-- Infra SQL 03 — Datos semilla (seed)
-- Provincia Santa Elena, cantones, parroquias, JRVs, cargos, partidos, candidatos
-- Para staging/development: usuarios MASTER + OBSERVER de ejemplo

-- ============================================================================
-- NOTA: ROLES ya están sembrados en 005-fks.sql del changelog.
-- Este script asume que la tabla rol ya tiene: MASTER, COORDINATOR, ADMIN, OBSERVER
-- ============================================================================

-- ============================================================================
-- GEOGRAFÍA: PROVINCIA SANTA ELENA
-- ============================================================================

-- Provincia
INSERT INTO provincia (nombre, codigo) VALUES
  ('Santa Elena', '24')
ON CONFLICT (codigo) DO NOTHING;

-- Cantones
INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Santa Elena', '2401', id FROM provincia WHERE codigo = '24'
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'La Libertad', '2402', id FROM provincia WHERE codigo = '24'
ON CONFLICT (codigo) DO NOTHING;

INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Salinas', '2403', id FROM provincia WHERE codigo = '24'
ON CONFLICT (codigo) DO NOTHING;

-- Parroquias de Santa Elena cantón (2401)
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Santa Elena', true, id FROM canton WHERE codigo = '2401'
ON CONFLICT DO NOTHING;

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Chanduy', false, id FROM canton WHERE codigo = '2401'
ON CONFLICT DO NOTHING;

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Manglaralto', false, id FROM canton WHERE codigo = '2401'
ON CONFLICT DO NOTHING;

-- Parroquias de La Libertad cantón (2402)
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'La Libertad', true, id FROM canton WHERE codigo = '2402'
ON CONFLICT DO NOTHING;

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Atahualpa', false, id FROM canton WHERE codigo = '2402'
ON CONFLICT DO NOTHING;

-- Parroquias de Salinas cantón (2403)
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Salinas', true, id FROM canton WHERE codigo = '2403'
ON CONFLICT DO NOTHING;

-- ============================================================================
-- JRVs DE EJEMPLO (5 por cantón = 15 total para testing)
-- ============================================================================

-- JRVs en Santa Elena cantón (parroquia Santa Elena urbana)
DO $$
DECLARE
  parroquia_id BIGINT;
BEGIN
  SELECT id INTO parroquia_id FROM parroquia WHERE nombre = 'Santa Elena' AND canton_id = (SELECT id FROM canton WHERE codigo = '2401');

  FOR i IN 1..5 LOOP
    INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
    VALUES (LPAD(i::text, 3, '0'), 'Escuela ' || i || ' - Santa Elena', 100 + (i * 10), parroquia_id)
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- JRVs en La Libertad cantón
DO $$
DECLARE
  parroquia_id BIGINT;
BEGIN
  SELECT id INTO parroquia_id FROM parroquia WHERE nombre = 'La Libertad' AND canton_id = (SELECT id FROM canton WHERE codigo = '2402');

  FOR i IN 6..10 LOOP
    INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
    VALUES (LPAD(i::text, 3, '0'), 'Colegio ' || i || ' - La Libertad', 120 + (i * 10), parroquia_id)
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- JRVs en Salinas cantón
DO $$
DECLARE
  parroquia_id BIGINT;
BEGIN
  SELECT id INTO parroquia_id FROM parroquia WHERE nombre = 'Salinas' AND canton_id = (SELECT id FROM canton WHERE codigo = '2403');

  FOR i IN 11..15 LOOP
    INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
    VALUES (LPAD(i::text, 3, '0'), 'Instituto ' || i || ' - Salinas', 110 + (i * 10), parroquia_id)
    ON CONFLICT DO NOTHING;
  END LOOP;
END $$;

-- ============================================================================
-- CARGOS ELECTORALES
-- ============================================================================

INSERT INTO cargo (nombre, nivel_geografico, descripcion) VALUES
  ('Prefecto/a de la Provincia', 'PROVINCIA', 'Jefe ejecutivo de la provincia'),
  ('Consejero/a CPCCS', 'NACIONAL', 'Consejo de Participación Ciudadana'),
  ('Alcalde/sa del Cantón', 'CANTON', 'Jefe ejecutivo del cantón'),
  ('Concejal Urbano', 'CANTON', 'Concejal en zona urbana'),
  ('Concejal Rural', 'CANTON', 'Concejal en zona rural'),
  ('Vocal de Junta Parroquial', 'PARROQUIA', 'Miembro de la junta parroquial rural')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- PARTIDOS POLÍTICOS (ejemplo)
-- ============================================================================

INSERT INTO partido (nombre, siglas, numero, color_hex) VALUES
  ('Unidad Popular', 'UP', 1, '#FF0000'),
  ('Movimiento CREO', 'CREO', 2, '#0066FF'),
  ('Revolución Ciudadana', 'RC', 3, '#FFD700'),
  ('Izquierda Democrática', 'ID', 4, '#FF6600'),
  ('Construcción Solidaria', 'CS', 5, '#00AA00')
ON CONFLICT DO NOTHING;

-- ============================================================================
-- CANDIDATOS DE EJEMPLO (Prefecto/a solamente, para simplificar)
-- ============================================================================

INSERT INTO candidato (nombre, apellido, numero_lista, cargo_id, partido_id)
SELECT 'Juan', 'Pérez', 1, (SELECT id FROM cargo WHERE nombre = 'Prefecto/a de la Provincia' LIMIT 1), (SELECT id FROM partido WHERE siglas = 'UP' LIMIT 1)
ON CONFLICT DO NOTHING;

INSERT INTO candidato (nombre, apellido, numero_lista, cargo_id, partido_id)
SELECT 'María', 'García', 2, (SELECT id FROM cargo WHERE nombre = 'Prefecto/a de la Provincia' LIMIT 1), (SELECT id FROM partido WHERE siglas = 'CREO' LIMIT 1)
ON CONFLICT DO NOTHING;

INSERT INTO candidato (nombre, apellido, numero_lista, cargo_id, partido_id)
SELECT 'Carlos', 'López', 3, (SELECT id FROM cargo WHERE nombre = 'Prefecto/a de la Provincia' LIMIT 1), (SELECT id FROM partido WHERE siglas = 'RC' LIMIT 1)
ON CONFLICT DO NOTHING;

-- ============================================================================
-- JRV_CARGO: Mapear qué cargos aplican en cada JRV
-- ============================================================================

-- Todos los cargos aplican en todas las JRVs (simplificado para testing)
-- En prod, filtraría por nivel geográfico (NACIONAL, PROVINCIA, etc)

INSERT INTO jrv_cargo (jrv_id, cargo_id)
SELECT jrv.id, cargo.id
FROM jrv
CROSS JOIN cargo
WHERE NOT EXISTS (
  SELECT 1 FROM jrv_cargo
  WHERE jrv_cargo.jrv_id = jrv.id
  AND jrv_cargo.cargo_id = cargo.id
);

-- ============================================================================
-- USUARIO OBSERVER DE EJEMPLO (para testing RLS)
-- ============================================================================

-- Email generado: juapeperobs@control.ttcs (Juan + Pérez + OBSERVER)
-- Asignamos algunas JRVs para que pueda registrar actas

INSERT INTO usuario (public_id, nombre, apellido, email, password_hash, rol_id, habilitado, creado_en)
SELECT
  gen_random_uuid(),
  'Juan',
  'Pérez',
  'juapeperobs@control.ttcs',
  '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36P4/PFm',
  (SELECT id FROM rol WHERE nombre = 'OBSERVER'),
  true,
  CURRENT_TIMESTAMP
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'juapeperobs@control.ttcs')
ON CONFLICT (email) DO NOTHING;

-- Asignar algunas JRVs al observer (solo si no existen aún)
WITH observer_id AS (
  SELECT id FROM usuario WHERE email = 'juapeperobs@control.ttcs' LIMIT 1
)
INSERT INTO observer_jrv_asignacion (observer_id, jrv_id)
SELECT
  observer_id.id,
  jrv.id
FROM observer_id, jrv
WHERE jrv.numero IN ('001', '002', '003')
  AND NOT EXISTS (
    SELECT 1 FROM observer_jrv_asignacion oja
    WHERE oja.observer_id = observer_id.id
    AND oja.jrv_id = jrv.id
  )
ON CONFLICT DO NOTHING;

-- ============================================================================
-- VERIFICACIÓN: Contar registros
-- ============================================================================

-- SELECT COUNT(*) FROM provincia;     -- 1
-- SELECT COUNT(*) FROM canton;        -- 3
-- SELECT COUNT(*) FROM parroquia;     -- 6
-- SELECT COUNT(*) FROM jrv;           -- 15
-- SELECT COUNT(*) FROM usuario;       -- 2+ (MASTER + OBSERVER)
-- SELECT COUNT(*) FROM cargo;         -- 6
-- SELECT COUNT(*) FROM partido;       -- 5
-- SELECT COUNT(*) FROM candidato;     -- 3
-- SELECT COUNT(*) FROM jrv_cargo;     -- 90 (15 jrvs * 6 cargos)
