--liquibase formatted sql

-- changeset electoral:039-01-data-jrvs-produccion context:seed
-- Geografía pre-electoral de produccion: JRVs con su jerarquia completa.
--
-- CONTEXTO LIQUIBASE "seed": es DATO de referencia, no esquema. Corre con
-- `quarkus.liquibase.contexts=seed` (que es el default de la app) y NO con
-- `schema-only` (el de la suite de tests que construye su propia geografia).
--
-- Que es esto: el seed 025 deja UNA provincia con 2 cantones, 4 parroquias y
-- 5 JRVs: alcanza para desarrollo y para la suite, pero no para una eleccion real,
-- que necesita las JRVs de todas las provincias donde hay mesa. Este changeset
-- agrega la geografia que se importa del CNE antes de la eleccion, con la misma
-- forma que usan los datos reales: PROVINCIA -> CANTON -> PARROQUIA -> JRV.
--
-- Convenciones (las mismas del seed 025):
--   * Cada INSERT va con su guarda NOT EXISTS, asi que el changeset es
--     idempotente: correrlo dos veces no duplica nada.
--   * Las claves foraneas se resuelven por el codigo del padre (provincia.codigo,
--     canton.codigo), no por id: los ids dependen del orden de insercion y los
--     datos del CNE no los traen.
--   * `jrv.numero` es UNICO en el sistema (indice uk). El prefijo es el de la
--     provincia: JRV-<PROVINCIA>-<N> para las que cubren una parroquia entera,
--     JRV-<PARROQUIA>-<N> cuando la parroquia tiene varias JRVs.
--
-- COMO SE REEMPLAZA POR LOS DATOS REALES: este archivo es la muestra. Para la
-- eleccion se genera `040-data-jrvs-<eleccion>-YYYY.sql` con los datos del CNE
-- (ver documents/OPERATIONS.md y documents/JRVS_IMPORT_GUIDE.md). Los dos
-- archivos conviven: los datos de la muestra no estorban porque los numeros de
-- JRV y los codigos de canton son distintos de los reales.
--
-- El deshacer (`liquibase rollback-count 1`) borra lo que este changeset
-- inserto. Los datos de 025 (provincia '24') NO se tocan: son de otra provincia.

-- === Provincia ===
INSERT INTO provincia (nombre, codigo)
SELECT 'Guayas', '09'
WHERE NOT EXISTS (SELECT 1 FROM provincia WHERE codigo = '09');

-- === Cantones ===
INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Guayaquil', '0901', p.id
FROM provincia p
WHERE p.codigo = '09' AND NOT EXISTS (SELECT 1 FROM canton WHERE codigo = '0901');

INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Durán', '0902', p.id
FROM provincia p
WHERE p.codigo = '09' AND NOT EXISTS (SELECT 1 FROM canton WHERE codigo = '0902');

-- === Parroquias ===
-- El canton de Guayaquil es urbano: sus parroquias son urbanas.
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Carapungo', TRUE, c.id
FROM canton c WHERE c.codigo = '0901'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Carapungo' AND p.canton_id = c.id);

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Los Ceibos', TRUE, c.id
FROM canton c WHERE c.codigo = '0901'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Los Ceibos' AND p.canton_id = c.id);

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Atarazana', TRUE, c.id
FROM canton c WHERE c.codigo = '0901'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Atarazana' AND p.canton_id = c.id);

-- El canton de Durán tiene parroquia urbana y rural.
INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Durán', TRUE, c.id
FROM canton c WHERE c.codigo = '0902'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Durán' AND p.canton_id = c.id);

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Febres Cordero', FALSE, c.id
FROM canton c WHERE c.codigo = '0902'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Febres Cordero' AND p.canton_id = c.id);

-- === JRVs ===
-- El numero sigue el prefijo de la provincia cuando la parroquia tiene una sola
-- JRV, y el de la parroquia cuando tiene varias.
INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-GY-001', 'Unidad Educativa César Chávez', 480, p.id
FROM parroquia p WHERE p.nombre = 'Carapungo' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-GY-001');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-GY-002', 'Colegio Francisco de Orellana', 350, p.id
FROM parroquia p WHERE p.nombre = 'Los Ceibos' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-GY-002');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-GY-003', 'Escuela Virgilio Drolon', 220, p.id
FROM parroquia p WHERE p.nombre = 'Atarazana' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-GY-003');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-DU-001', 'Unidad Educativa Alejandro Pushkar', 430, p.id
FROM parroquia p WHERE p.nombre = 'Durán' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-DU-001');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-DU-002', 'Escuela Juan Falcón', 175, p.id
FROM parroquia p WHERE p.nombre = 'Febres Cordero'
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-DU-002');

-- === Asignación de cargos a las JRVs nuevas ===
-- Sin esto la JRV existe pero no es operable: el observer no puede registrar
-- un acta para un par (JRV, cargo) que la JRV no tenga habilitado.
INSERT INTO jrv_cargo (jrv_id, cargo_id, activo)
SELECT j.id, c.id, TRUE
FROM jrv j, cargo c
WHERE j.numero IN ('JRV-GY-001', 'JRV-GY-002', 'JRV-GY-003', 'JRV-DU-001')
  AND c.nombre IN ('Alcalde/sa', 'Concejal Urbano')
  AND NOT EXISTS (SELECT 1 FROM jrv_cargo jc WHERE jc.jrv_id = j.id AND jc.cargo_id = c.id);

INSERT INTO jrv_cargo (jrv_id, cargo_id, activo)
SELECT j.id, c.id, TRUE
FROM jrv j, cargo c
WHERE j.numero = 'JRV-DU-002' AND c.nombre IN ('Alcalde/sa', 'Concejal Rural')
  AND NOT EXISTS (SELECT 1 FROM jrv_cargo jc WHERE jc.jrv_id = j.id AND jc.cargo_id = c.id);

-- El deshacer va explicito porque Liquibase solo lo inventa para DDL: un
-- bloque de INSERTs planos, sin esto, se quejaba al deshacer y la base se
-- quedaba con la geografia a medias.
--
-- OJO con el formato del parser: toma el deshacer desde la PRIMERA linea de
-- comentario que empieza con la palabra "rollback" hasta el final del bloque, y
-- una linea de comentario que empieza con la palabra "changeset" la toma por
-- una cabecera. Por eso estas lineas evitan las dos palabras al principio, y la
-- del deshacer es la ultima.
--
-- El orden respeta las claves foraneas: jrv_cargo -> jrv -> parroquia ->
-- canton -> provincia. Y solo toca lo que este bloque inserta: la provincia
-- '24' y sus cantones (del seed 025) no se ven afectados.
-- rollback DELETE FROM jrv_cargo WHERE jrv_id IN (SELECT id FROM jrv WHERE numero IN ('JRV-GY-001','JRV-GY-002','JRV-GY-003','JRV-DU-001','JRV-DU-002')); DELETE FROM jrv WHERE numero IN ('JRV-GY-001','JRV-GY-002','JRV-GY-003','JRV-DU-001','JRV-DU-002'); DELETE FROM parroquia WHERE nombre IN ('Carapungo','Los Ceibos','Atarazana','Durán','Febres Cordero') AND canton_id IN (SELECT id FROM canton WHERE codigo IN ('0901','0902')); DELETE FROM canton WHERE codigo IN ('0901','0902'); DELETE FROM provincia WHERE codigo = '09';
