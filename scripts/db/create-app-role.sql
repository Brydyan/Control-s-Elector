-- =============================================================================
-- Crea el rol con el que conecta la APLICACIÓN.
--
-- Por qué existe: el backend tiene que correr con un rol que NO pueda saltarse
-- las políticas de RLS. El rol que crea la imagen de Postgres (`POSTGRES_USER`)
-- es SUPERUSUARIO, y Postgres no le aplica RLS a un superusuario: ni siquiera
-- con `FORCE ROW LEVEL SECURITY` (FORCE ata al dueño de la tabla, no al
-- superusuario). Con ese rol, la capa RLS del proyecto no se ejecuta en ningún
-- entorno y ninguna prueba puede demostrar que filtra.
--
-- Qué hace este script:
--   1. Crea el rol si no existe.
--   2. Refuerza sus atributos y fija su contraseña (corre siempre).
--
-- Es IDEMPOTENTE: se puede correr en cada arranque sin romper nada.
--
-- Uso (la contraseña NUNCA va en este archivo: llega por variable de entorno):
--   psql -v ON_ERROR_STOP=1 \
--        -v app_role=electoral_app \
--        -v app_password="$APP_PASSWORD" \
--        -f create-app-role.sql
--
-- `NOSUPERUSER` y `NOBYPASSRLS` son los dos atributos que importan: sin ellos
-- el rol puede saltarse RLS y el arreglo no arregla nada.
-- =============================================================================

-- El CREATE va condicionado: si el rol ya existe, la sentencia no se genera.
SELECT format(
         'CREATE ROLE %I NOSUPERUSER NOBYPASSRLS LOGIN PASSWORD %L',
         :'app_role',
         :'app_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = :'app_role')
\gexec

-- El ALTER corre siempre: deja los atributos en su sitio y rota la contraseña.
SELECT format(
         'ALTER ROLE %I NOSUPERUSER NOBYPASSRLS LOGIN PASSWORD %L',
         :'app_role',
         :'app_password')
\gexec
