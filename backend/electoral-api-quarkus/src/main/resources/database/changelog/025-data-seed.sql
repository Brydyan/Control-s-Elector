--liquibase formatted sql

-- changeset electoral:025-01-data-seed-dev context:seed
-- Datos de desarrollo: geografía mínima, configuración preelectoral,
-- usuarios de cada rol, JRVs, candidatos y actas en distintos estados.
--
-- CONTEXTO LIQUIBASE "seed": este changeset es DATO de referencia, no esquema.
-- La app lo corre porque `spring.liquibase.contexts=seed`; la suite de tests
-- NO lo corre (el pom le pasa `spring.liquibase.contexts=schema-only`).
--
-- Por qué: la suite del backend está diseñada contra una base con solo el
-- esquema. Afirma "base vacía" (DashboardControllerTest.RNF_DASH_01), espera
-- que el primer partido sea el que crea el test, espera una sola sesión, y
-- construye su propia geografía con códigos fijos (provincia '24', cantón
-- '2401'). Con el seed aplicado en la base de test, esas aserciones no se
-- pueden cumplir y el setup revienta por colisión de índices únicos.
-- Separar esquema de dato de referencia es la razón de que el seed tenga
-- contexto propio.
--
-- Convenciones:
--   * Solo se insertan filas si la tabla está vacía (idempotente).
--   * Passwords de los usuarios seed son hashes BCrypt de "admin12345".
--   * Para regenerar en dev: docker compose down -v && docker compose up -d.

-- === Geografía: 1 provincia, 2 cantones, 4 parroquias, 5 JRVs ===
INSERT INTO provincia (nombre, codigo)
SELECT 'Santa Elena', '24'
WHERE NOT EXISTS (SELECT 1 FROM provincia WHERE codigo = '24');

INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'Santa Elena', '2401', p.id
FROM provincia p
WHERE p.codigo = '24' AND NOT EXISTS (SELECT 1 FROM canton WHERE codigo = '2401');

INSERT INTO canton (nombre, codigo, provincia_id)
SELECT 'La Libertad', '2402', p.id
FROM provincia p
WHERE p.codigo = '24' AND NOT EXISTS (SELECT 1 FROM canton WHERE codigo = '2402');

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Santa Elena', TRUE, c.id
FROM canton c WHERE c.codigo = '2401'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Santa Elena' AND p.canton_id = c.id);

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Ballenita', FALSE, c.id
FROM canton c WHERE c.codigo = '2401'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Ballenita' AND p.canton_id = c.id);

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'La Libertad', TRUE, c.id
FROM canton c WHERE c.codigo = '2402'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'La Libertad' AND p.canton_id = c.id);

INSERT INTO parroquia (nombre, es_urbana, canton_id)
SELECT 'Ayangue', FALSE, c.id
FROM canton c WHERE c.codigo = '2402'
  AND NOT EXISTS (SELECT 1 FROM parroquia p WHERE p.nombre = 'Ayangue' AND p.canton_id = c.id);

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-SE-001', 'Escuela Abdón Calderón', 320, p.id
FROM parroquia p WHERE p.nombre = 'Santa Elena' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-SE-001');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-SE-002', 'Colegio Vicente Rocafuerte', 285, p.id
FROM parroquia p WHERE p.nombre = 'Santa Elena' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-SE-002');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-BAL-001', 'Escuela Simón Bolívar', 198, p.id
FROM parroquia p WHERE p.nombre = 'Ballenita'
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-BAL-001');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-LL-001', 'Unidad Educativa La Libertad', 412, p.id
FROM parroquia p WHERE p.nombre = 'La Libertad' AND p.es_urbana = TRUE
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-LL-001');

INSERT INTO jrv (numero, recinto, total_electores, parroquia_id)
SELECT 'JRV-AYA-001', 'Escuela Ayangue', 156, p.id
FROM parroquia p WHERE p.nombre = 'Ayangue'
  AND NOT EXISTS (SELECT 1 FROM jrv WHERE numero = 'JRV-AYA-001');

-- === Cargos: 4 dignidades ===
INSERT INTO cargo (nombre, nivel, puestos)
SELECT 'Prefecto', 'PROVINCIA', 1
WHERE NOT EXISTS (SELECT 1 FROM cargo WHERE nombre = 'Prefecto');

INSERT INTO cargo (nombre, nivel, puestos)
SELECT 'Alcalde/sa', 'CANTON', 1
WHERE NOT EXISTS (SELECT 1 FROM cargo WHERE nombre = 'Alcalde/sa');

INSERT INTO cargo (nombre, nivel, puestos)
SELECT 'Concejal Urbano', 'CANTON', 5
WHERE NOT EXISTS (SELECT 1 FROM cargo WHERE nombre = 'Concejal Urbano');

INSERT INTO cargo (nombre, nivel, puestos)
SELECT 'Concejal Rural', 'CANTON', 3
WHERE NOT EXISTS (SELECT 1 FROM cargo WHERE nombre = 'Concejal Rural');

-- === Partidos: 4 listas ===
INSERT INTO partido (nombre, siglas, numero_lista)
SELECT 'Movimiento Acción Solidaria', 'MAS', 1
WHERE NOT EXISTS (SELECT 1 FROM partido WHERE numero_lista = 1);

INSERT INTO partido (nombre, siglas, numero_lista)
SELECT 'Partido Unión Provincial', 'PUP', 2
WHERE NOT EXISTS (SELECT 1 FROM partido WHERE numero_lista = 2);

INSERT INTO partido (nombre, siglas, numero_lista)
SELECT 'Frente Independiente Peninsular', 'FIP', 3
WHERE NOT EXISTS (SELECT 1 FROM partido WHERE numero_lista = 3);

INSERT INTO partido (nombre, siglas, numero_lista)
SELECT 'Coalición Costa Verde', 'CCV', 4
WHERE NOT EXISTS (SELECT 1 FROM partido WHERE numero_lista = 4);

-- === Candidatos: 1 por cada combinación (cargo, partido) relevante ===
INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'María Fernanda Solórzano', 1, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Alcalde/sa' AND p.siglas = 'MAS'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 1 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'José Antonio Vera', 2, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Alcalde/sa' AND p.siglas = 'PUP'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 2 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Luisa María Briones', 3, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Alcalde/sa' AND p.siglas = 'FIP'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 3 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Carlos Eduardo Mero', 4, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Alcalde/sa' AND p.siglas = 'CCV'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 4 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Ana Lucía Piloso', 1, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Urbano' AND p.siglas = 'MAS'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 1 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Diego Armando Cantos', 2, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Urbano' AND p.siglas = 'PUP'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 2 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'María José Quimís', 3, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Urbano' AND p.siglas = 'FIP'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 3 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Roberto Carlos Delgado', 4, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Urbano' AND p.siglas = 'CCV'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 4 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Pedro Pablo Cevallos', 1, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Rural' AND p.siglas = 'MAS'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 1 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Rosa Elena Rodríguez', 2, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Rural' AND p.siglas = 'PUP'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 2 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'Manuel Antonio Loor', 3, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Rural' AND p.siglas = 'FIP'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 3 AND cargo_id = c.id AND partido_id = p.id);

INSERT INTO candidato (nombre, orden, cargo_id, partido_id)
SELECT 'María Fernanda Delgado', 4, c.id, p.id
FROM cargo c, partido p WHERE c.nombre = 'Concejal Rural' AND p.siglas = 'CCV'
  AND NOT EXISTS (SELECT 1 FROM candidato WHERE orden = 4 AND cargo_id = c.id AND partido_id = p.id);

-- === Asignación de cargos a JRVs ===
INSERT INTO jrv_cargo (jrv_id, cargo_id, activo)
SELECT j.id, c.id, TRUE
FROM jrv j, cargo c
WHERE j.numero IN ('JRV-SE-001', 'JRV-SE-002', 'JRV-BAL-001', 'JRV-LL-001')
  AND c.nombre IN ('Alcalde/sa', 'Concejal Urbano')
  AND NOT EXISTS (SELECT 1 FROM jrv_cargo jc WHERE jc.jrv_id = j.id AND jc.cargo_id = c.id);

INSERT INTO jrv_cargo (jrv_id, cargo_id, activo)
SELECT j.id, c.id, TRUE
FROM jrv j, cargo c
WHERE j.numero = 'JRV-AYA-001' AND c.nombre IN ('Alcalde/sa', 'Concejal Rural')
  AND NOT EXISTS (SELECT 1 FROM jrv_cargo jc WHERE jc.jrv_id = j.id AND jc.cargo_id = c.id);

-- === Usuarios: 1 por rol + 1 OBSERVER extra para multi-usuario en sesiones.
-- Hash BCrypt de "admin12345" (placeholder — se regenerará en primera carga real).
INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido)
SELECT 'mastersolon@example.local',
       '$2a$10$wH8QHw5yZqJ5Z5ZqJ5Z5OeZ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5',
       r.id, TRUE, NOW(), 'Maria', 'Solorzano'
FROM rol r WHERE r.nombre = 'MASTER'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'mastersolon@example.local');

INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido)
SELECT 'coordinatorcortes@example.local',
       '$2a$10$wH8QHw5yZqJ5Z5ZqJ5Z5OeZ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5',
       r.id, TRUE, NOW(), 'Carlos', 'Cortes'
FROM rol r WHERE r.nombre = 'COORDINATOR'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'coordinatorcortes@example.local');

INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido)
SELECT 'adminpalma@example.local',
       '$2a$10$wH8QHw5yZqJ5Z5ZqJ5Z5OeZ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5',
       r.id, TRUE, NOW(), 'Andres', 'Palma'
FROM rol r WHERE r.nombre = 'ADMIN'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'adminpalma@example.local');

INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido)
SELECT 'observervera@example.local',
       '$2a$10$wH8QHw5yZqJ5Z5ZqJ5Z5OeZ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5',
       r.id, TRUE, NOW(), 'Jose', 'Vera'
FROM rol r WHERE r.nombre = 'OBSERVER'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'observervera@example.local');

INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido)
SELECT 'observerquimis@example.local',
       '$2a$10$wH8QHw5yZqJ5Z5ZqJ5Z5OeZ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5Z5ZqJ5',
       r.id, TRUE, NOW(), 'Maria', 'Quimis'
FROM rol r WHERE r.nombre = 'OBSERVER'
  AND NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'observerquimis@example.local');

-- === Asignación de OBSERVERs a JRVs ===
INSERT INTO observer_jrv_asignacion (usuario_id, jrv_id)
SELECT u.id, j.id
FROM usuario u, jrv j
WHERE u.email IN ('observervera@example.local', 'observerquimis@example.local')
  AND j.numero IN ('JRV-SE-001', 'JRV-SE-002', 'JRV-BAL-001', 'JRV-LL-001', 'JRV-AYA-001')
  AND NOT EXISTS (
    SELECT 1 FROM observer_jrv_asignacion oja
    WHERE oja.usuario_id = u.id AND oja.jrv_id = j.id
  );

-- === Sesiones: 1 activa por cada OBSERVER (para probar la pantalla de sesiones) ===
INSERT INTO sesion (usuario_id, sesion_id, refresh_hash, revocado, ip_cliente, user_agent,
                    creado_en, ultimo_uso_en, expira_en)
SELECT u.id,
       'sess-seed-' || substr(md5(u.email), 1, 16),
       '$2a$10$seedrefreshtokenhash' || substr(md5(u.email), 1, 16),
       FALSE,
       '192.168.1.50',
       'Mozilla/5.0 (X11; Linux x86_64) SeedAgent/1.0',
       NOW() - INTERVAL '15 minutes',
       NOW() - INTERVAL '2 minutes',
       NOW() + INTERVAL '6 hours'
FROM usuario u
WHERE u.email IN ('observervera@example.local', 'observerquimis@example.local')
  AND NOT EXISTS (SELECT 1 FROM sesion s WHERE s.sesion_id = 'sess-seed-' || substr(md5(u.email), 1, 16));

-- === Actas: 5 distribuidas entre JRVs en distintos estados ===
-- Cuadre: candidatos (MAS=120, PUP=80, FIP=50, CCV=30) + blancos 10 + nulos 5 = 295.
-- Distribución: SE-001=ENVIADA, SE-002=EN_DISPUTA, BAL-001=CORREGIDA, LL-001=APROBADA, AYA-001=ANULADA.

INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes,
                  votos_blancos, votos_nulos, fecha_registro)
SELECT j.id, c.id, oja.usuario_id, 'ENVIADA', 295, 10, 5, NOW() - INTERVAL '1 hour'
FROM jrv j, cargo c, observer_jrv_asignacion oja
WHERE j.numero = 'JRV-SE-001' AND c.nombre = 'Alcalde/sa' AND oja.jrv_id = j.id
  AND NOT EXISTS (SELECT 1 FROM acta a WHERE a.jrv_id = j.id AND a.cargo_id = c.id AND a.estado = 'ENVIADA')
LIMIT 1;

INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes,
                  votos_blancos, votos_nulos, fecha_registro)
SELECT j.id, c.id, oja.usuario_id, 'EN_DISPUTA', 295, 10, 5, NOW() - INTERVAL '4 hours'
FROM jrv j, cargo c, observer_jrv_asignacion oja
WHERE j.numero = 'JRV-SE-002' AND c.nombre = 'Alcalde/sa' AND oja.jrv_id = j.id
  AND NOT EXISTS (SELECT 1 FROM acta a WHERE a.jrv_id = j.id AND a.cargo_id = c.id AND a.estado = 'EN_DISPUTA')
LIMIT 1;

INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes,
                  votos_blancos, votos_nulos, fecha_registro)
SELECT j.id, c.id, oja.usuario_id, 'CORREGIDA', 295, 10, 5, NOW() - INTERVAL '12 hours'
FROM jrv j, cargo c, observer_jrv_asignacion oja
WHERE j.numero = 'JRV-BAL-001' AND c.nombre = 'Alcalde/sa' AND oja.jrv_id = j.id
  AND NOT EXISTS (SELECT 1 FROM acta a WHERE a.jrv_id = j.id AND a.cargo_id = c.id AND a.estado = 'CORREGIDA')
LIMIT 1;

INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado,
                  total_sufragantes, votos_blancos, votos_nulos, fecha_registro)
SELECT j.id, c.id, oja.usuario_id, 'APROBADA',
       295, 10, 5, NOW() - INTERVAL '1 day'
FROM jrv j, cargo c, observer_jrv_asignacion oja
WHERE j.numero = 'JRV-LL-001' AND c.nombre = 'Alcalde/sa' AND oja.jrv_id = j.id
  AND NOT EXISTS (SELECT 1 FROM acta a WHERE a.jrv_id = j.id AND a.cargo_id = c.id AND a.estado = 'APROBADA')
LIMIT 1;

INSERT INTO acta (jrv_id, cargo_id, registrada_por, estado, total_sufragantes,
                  votos_blancos, votos_nulos, fecha_registro, motivo_anulacion)
SELECT j.id, c.id, oja.usuario_id, 'ANULADA', 295, 10, 5, NOW() - INTERVAL '2 days',
       'Disputa irresoluble sobre votos de CCV; anulada por MASTER'
FROM jrv j, cargo c, observer_jrv_asignacion oja
WHERE j.numero = 'JRV-AYA-001' AND c.nombre = 'Alcalde/sa' AND oja.jrv_id = j.id
  AND NOT EXISTS (SELECT 1 FROM acta a WHERE a.jrv_id = j.id AND a.cargo_id = c.id AND a.estado = 'ANULADA')
LIMIT 1;

-- Detalle por acta+candidato del Alcalde (MAS=120, PUP=80, FIP=50, CCV=30).
INSERT INTO acta_detalle (acta_id, candidato_id, votos)
SELECT a.id, c.id,
       CASE p.siglas WHEN 'MAS' THEN 120 WHEN 'PUP' THEN 80 WHEN 'FIP' THEN 50 WHEN 'CCV' THEN 30 END
FROM acta a, candidato c, partido p
WHERE c.cargo_id = (SELECT id FROM cargo WHERE nombre = 'Alcalde/sa')
  AND c.partido_id = p.id
  AND a.cargo_id = c.cargo_id
  AND a.estado IN ('ENVIADA', 'EN_DISPUTA', 'CORREGIDA', 'APROBADA', 'ANULADA')
  AND NOT EXISTS (SELECT 1 FROM acta_detalle ad WHERE ad.acta_id = a.id AND ad.candidato_id = c.id);
