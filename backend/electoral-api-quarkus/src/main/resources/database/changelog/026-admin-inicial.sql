--liquibase formatted sql

-- =============================================================================
-- 026 - Seed del usuario admin inicial.
--
-- Reemplaza a `DataSeeder`, que insertaba un admin nuevo en CADA arranque del
-- backend. Ver /odd/tasks/quarkus-deps-jdk25.md, seccion L3.
--
-- Por que estaba roto: el seeder hacia
--
--     String emailGenerado = emailGenerator.generar(nombre, apellido, "MASTER");
--     if (usuarios.findByEmail(emailGenerado).isPresent()) return;
--
-- pero `EmailGenerator.generar()` devuelve POR CONTRATO el primer email que
-- no existe (`admsismas`, luego `admsismas2`, `admsismas3`, ...). Como devuelve
-- uno libre, el `findByEmail` posterior nunca lo encontraba: la guarda era
-- inalcanzable y cada arranque inseria un admin. Medido en la base local: 17
-- filas MASTER, `admsismas@` a `admsismas16@control.ttcs`, una por reinicio.
--
-- Ademas `EmailGenerator` lanza `IllegalArgumentException` al agotar
-- MAX_RETRIES=100, asi que en el reinicio ~101 el backend no arrancaba mas.
-- Liquibase no tiene ese problema: un changeset corre una vez y queda
-- registrado en `databasechangelog`.
--
-- ─── Seguridad ─────────────────────────────────────────────────────────────
--
-- EL HASH DE ABAJO ES DE UNA CONTRASENA CONOCIDA Y ESTA EN EL HISTORIAL DE
-- GIT. Quien lea el repositorio puede entrar como MASTER.
--
-- Es aceptable SOLO en desarrollo. En staging y produccion este changeset NO
-- debe correr: la cuenta se provisiona a mano con credenciales propias.
--
-- ─── El contexto que lo apaga ──────────────────────────────────────────────
--
-- Este changeset tiene su PROPIO contexto, `bootstrap-admin`, separado del
-- `seed` del seed de referencia. La separacion es deliberada: el `seed` carga
-- los datos de referencia que el producto NECESITA en produccion (cargos,
-- partidos, candidatos, JRVs, geografia), mientras que este admin es un
-- artefacto de desarrollo. Si compartieran contexto no habria forma de dejar
-- correr uno y dejar de correr el otro.
--
-- El default de la app es `QUARKUS_LIQUIDASE_CONTEXTS=seed`: produccion corre
-- el seed de referencia y NO crea este admin. Para el stack local y para los
-- jobs de CI que necesitan esa cuenta, el valor es `seed,bootstrap-admin`.
--
-- OJO, y esto se verifico contra la API de Liquibase 5.0.3 (no de memoria):
-- una lista de contextos de runtime VACIA no excluye nada. Con `Contexts` vacio,
-- `ContextExpression.matches()` devuelve true para cualquier contexto, asi que
-- `QUARKUS_LIQUIDASE_CONTEXTS=` (vacio) corre TODO, incluido este changeset.
-- Para excluirlo hay que pasar una lista NO vacia que no contenga
-- `bootstrap-admin` (por ejemplo `seed`). La version anterior de este archivo
-- afirmaba lo contrario y por eso el admin se creaba en produccion.
--
-- Para rotatear la contrasena en desarrollo:
--
--   UPDATE usuario SET password_hash = '<hash nuevo>' WHERE email = 'admin@control-elector.local';
--
-- donde el hash nuevo se genera con `PasswordHasher` (BCrypt, costo 12, el
-- mismo algoritmo y costo que usa el backend Spring).
-- =============================================================================

-- changeset electoral:026-01-admin-inicial context:bootstrap-admin
INSERT INTO usuario (email, nombre, apellido, password_hash, rol_id, habilitado, creado_en)
SELECT
    'admin@control-elector.local',
    'Admin',
    'Sistema',
    -- BCrypt costo 12 de 'admin12345'. DEV ONLY. Ver el aviso de seguridad arriba.
    '$2a$12$ZB3dCA2ZgChgfCl2xnRpmOn9QVzRTdJF.rxGE9pDv2rJ5MxNvf/aK',
    (SELECT id FROM rol WHERE nombre = 'MASTER'),
    TRUE,
    now()
WHERE NOT EXISTS (SELECT 1 FROM usuario WHERE email = 'admin@control-elector.local');
-- rollback DELETE FROM usuario WHERE email = 'admin@control-elector.local';