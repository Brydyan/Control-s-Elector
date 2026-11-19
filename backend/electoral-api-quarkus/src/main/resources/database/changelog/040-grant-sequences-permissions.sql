--liquibase formatted sql

-- ============================================================================
-- 040 — Otorgar permisos explícitos en SEQUENCES a electoral_app
--
-- El changeset 036 intenta otorgar permisos en secuencias, pero SOLO se
-- ejecuta si el rol electoral_app existe en el momento de la migración.
--
-- En Neon (producción), electoral_app se creó MANUALMENTE después de que
-- Liquibase corrió el 036. Resultado: el 036 quedó registrado como "done"
-- sin ejecutar el GRANT en SEQUENCES.
--
-- Este changeset fixa bases ya migradas otorgando permisos explícitos a
-- todas las secuencias y configurando DEFAULT PRIVILEGES para las nuevas.
--
-- Notas:
--   - Es idempotente: puede ejecutarse múltiples veces sin error
--   - Cubre todas las secuencias existentes (audit_log_id_seq, usuario_id_seq, etc)
--   - Configura DEFAULT PRIVILEGES para secuencias futuras
--   - Solo ejecuta si el rol electoral_app existe
-- ============================================================================

-- changeset electoral:040-01-grant-sequences-to-electoral-app splitStatements:false
DO $$
DECLARE
    current_role TEXT;
BEGIN
    IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'electoral_app') THEN
        -- Otorgar permisos en TODAS las secuencias existentes
        EXECUTE 'GRANT USAGE, SELECT ON ALL SEQUENCES IN SCHEMA public TO electoral_app';
        RAISE NOTICE 'Permisos en SEQUENCES otorgados a electoral_app';

        -- Configurar DEFAULT PRIVILEGES para que secuencias NUEVAS
        -- creadas por el rol que ejecuta Liquibase hereden permisos para electoral_app
        -- Esto previene el problema en el futuro: nuevas migraciones que creen
        -- secuencias implícitamente (via BIGSERIAL) ya tendrán permisos.
        --
        -- IMPORTANTE: Usamos CURRENT_USER en lugar de hardcodear electoral_user.
        -- En desarrollo, CURRENT_USER es electoral_user. En Railway/Neon puede ser
        -- neon_owner_xyz u otro nombre. Así el changeset funciona en cualquier ambiente
        -- sin necesidad de conocer el nombre exacto del rol owner de la BD.
        GET DIAGNOSTICS current_role = USER;
        EXECUTE 'ALTER DEFAULT PRIVILEGES FOR ROLE ' || current_role || ' IN SCHEMA public GRANT USAGE, SELECT ON SEQUENCES TO electoral_app';
        RAISE NOTICE 'DEFAULT PRIVILEGES configurados para rol: %', current_role;
    ELSE
        RAISE WARNING 'Rol electoral_app no existe aún. Saltando GRANT en SEQUENCES.';
    END IF;
END $$;

-- rollback (no es reversible: el changeset es reparador de bases ya migradas)
