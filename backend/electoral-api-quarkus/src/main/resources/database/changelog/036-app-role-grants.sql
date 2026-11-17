--liquibase formatted sql

-- ============================================================================
-- 036 - Rol de aplicación: permisos y funciones de RLS.
--
-- El backend conecta con un rol propio (`electoral_app`, NOSUPERUSER y
-- NOBYPASSRLS) en vez de con el dueño de la base. El motivo está medido:
-- con el dueño —que es superusuario— Postgres NO aplica las políticas de RLS,
-- ni siquiera con `FORCE ROW LEVEL SECURITY`, porque FORCE ata al dueño de la
-- tabla y no al superusuario. Con el rol de aplicación, las políticas sí se
-- aplican y la capa RLS deja de ser decorativa.
--
-- Este changeset le da al rol de aplicación los permisos que necesita y
-- vuelve SECURITY DEFINER a las dos funciones que las políticas evaluan.
-- ============================================================================

-- changeset electoral:036-01-grants-rol-aplicacion splitStatements:false
-- splitStatements:false evita que Liquibase parta el DO $$ por ';' y rompa el dollar-quote.
-- Permisos del rol de aplicación sobre las tablas de la aplicación.
--
-- Va GUARDADO por la existencia del rol: un entorno que todavía no lo tiene
-- (una base de desarrollo vieja, un ambiente antes de adoptar el rol) no
-- debe romperse por un GRANT a un rol inexistente. En esos entornos los
-- permisos no se aplican y la migración sigue.
--
-- `ALTER DEFAULT PRIVILEGES` es lo que hace que las tablas que se creen en
-- el FUTURO (todo changeset nuevo) nazcan con los permisos puestos: sin eso,
-- el próximo changelog que cree una tabla deja al rol de aplicación sin
-- poder usarla, y el fallo aparece en producción y no en el PR.
DO $$ BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electoral_app') THEN
        EXECUTE 'GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA public TO electoral_app';
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO electoral_app';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE electoral_user IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO electoral_app';
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE electoral_user IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO electoral_app';
    END IF;
END $$;
-- rollback REVOKE ALL ON ALL TABLES IN SCHEMA public FROM electoral_app; REVOKE ALL ON ALL SEQUENCES IN SCHEMA public FROM electoral_app;

-- changeset electoral:036-02-funciones-rls-security-definer
-- Las dos funciones que evalúan las políticas pasan a SECURITY DEFINER.
--
-- `get_current_user_rol_name()` lee `usuario` y `rol` para devolver el rol de
-- la persona con la sesión abierta. Sin SECURITY DEFINER esa lectura corre con
-- los permisos de QUIEN EVALÚA LA POLÍTICA (el rol de aplicación), así que
-- bastaría con que el rol de aplicación no tuviera SELECT sobre `usuario` para
-- que TODAS las políticas de RLS dejaran de poder evaluarse: el error sería
-- "permission denied for table usuario" en vez de un 0 filas silencioso.
--
-- Con SECURITY DEFINER la función corre con los permisos de su dueño (el
-- dueño de la base), que sí puede leer `usuario`. Y `SET search_path` fijo
-- evita que alguien cambie el search_path para que la función resuelva el
-- nombre de otra tabla.
--
-- ALTER FUNCTION y no CREATE OR REPLACE: CREATE OR REPLACE conserva los
-- atributos de seguridad de la función existente, así que no los cambiaría.
ALTER FUNCTION get_current_user_id() SECURITY DEFINER;
ALTER FUNCTION get_current_user_rol_name() SECURITY DEFINER;
ALTER FUNCTION get_current_user_id() SET search_path = public, pg_temp;
ALTER FUNCTION get_current_user_rol_name() SET search_path = public, pg_temp;
-- rollback ALTER FUNCTION get_current_user_id() NO SECURITY DEFINER; ALTER FUNCTION get_current_user_rol_name() NO SECURITY DEFINER; ALTER FUNCTION get_current_user_id() RESET search_path; ALTER FUNCTION get_current_user_rol_name() RESET search_path;
