package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Permiso;
import ec.twintailcs.electoral.auth.domain.PermisoRepository;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga datos iniciales en la DB al arrancar:
 *  - Lista canonica de permisos del sistema (recurso+accion).
 *  - Asignaciones de permisos por rol.
 *  - Usuario admin inicial (credenciales por env vars; defaults para dev).
 *
 * Los recursos/acciones del modelo electoral:
 *   evidencia (upload, read, download, delete)
 *   acta      (read, aprobar, rechazar)
 *   usuario   (read, create, update, delete)
 *   sesion    (list, revoke)   <- revocar la sesion de otro
 *
 * Idempotente: si los permisos ya existen, no falla. Si los permisos
 * nuevos se agregan al seed en una nueva version, se insertan solos
 * porque se hace un SELECT-or-INSERT por cada (recurso, accion).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    /** Permisos del sistema. Editar aca + rebuildear = alta. */
    private static final List<String[]> PERMISOS = List.of(
            // Evidencia
            new String[]{"evidencia", "upload"},
            new String[]{"evidencia", "read"},
            new String[]{"evidencia", "download"},
            new String[]{"evidencia", "delete"},
            // Acta
            new String[]{"acta", "read"},
            new String[]{"acta", "aprobar"},
            new String[]{"acta", "rechazar"},
            // Usuario
            new String[]{"usuario", "read"},
            new String[]{"usuario", "create"},
            new String[]{"usuario", "update"},
            new String[]{"usuario", "delete"},
            // Sesion (S solo ve sus propias; admin lo ve todo)
            new String[]{"sesion", "list"},
            new String[]{"sesion", "revoke"}
    );

    /** Asignacion: rol -> permisos. */
    private static final List<String[]> PERMISOS_POR_ROL = List.of(
            // MASTER = todo
            new String[]{"MASTER", "evidencia:upload"},
            new String[]{"MASTER", "evidencia:read"},
            new String[]{"MASTER", "evidencia:download"},
            new String[]{"MASTER", "evidencia:delete"},
            new String[]{"MASTER", "acta:read"},
            new String[]{"MASTER", "acta:aprobar"},
            new String[]{"MASTER", "acta:rechazar"},
            new String[]{"MASTER", "usuario:read"},
            new String[]{"MASTER", "usuario:create"},
            new String[]{"MASTER", "usuario:update"},
            new String[]{"MASTER", "usuario:delete"},
            new String[]{"MASTER", "sesion:list"},
            new String[]{"MASTER", "sesion:revoke"},

            // COORDINATOR = aprobar/rechazar/ver; no elimina
            new String[]{"COORDINATOR", "evidencia:read"},
            new String[]{"COORDINATOR", "evidencia:download"},
            new String[]{"COORDINATOR", "acta:read"},
            new String[]{"COORDINATOR", "acta:aprobar"},
            new String[]{"COORDINATOR", "acta:rechazar"},
            new String[]{"COORDINATOR", "usuario:read"},
            new String[]{"COORDINATOR", "sesion:list"},

            // ADMIN = solo lectura
            new String[]{"ADMIN", "evidencia:read"},
            new String[]{"ADMIN", "evidencia:download"},
            new String[]{"ADMIN", "acta:read"},
            new String[]{"ADMIN", "usuario:read"},
            new String[]{"ADMIN", "sesion:list"},

            // OBSERVER = sube y ve sus propias
            new String[]{"OBSERVER", "evidencia:upload"},
            new String[]{"OBSERVER", "evidencia:read"},
            new String[]{"OBSERVER", "evidencia:download"},
            new String[]{"OBSERVER", "acta:read"}
    );

    private final PermisoRepository permisos;
    private final RolPermisoRepository rolPermisos;
    private final UsuarioRepository usuarios;
    private final PasswordHasher hasher;

    @Value("${app.seed.admin-email:admin@control-elector.local}")
    private String adminEmail;
    @Value("${app.seed.admin-password:admin12345}")
    private String adminPassword;

    public DataSeeder(PermisoRepository permisos, RolPermisoRepository rolPermisos,
                      UsuarioRepository usuarios, PasswordHasher hasher) {
        this.permisos = permisos;
        this.rolPermisos = rolPermisos;
        this.usuarios = usuarios;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void run(String... args) {
        // 1. permisos
        for (String[] p : PERMISOS) {
            String recurso = p[0];
            String accion = p[1];
            permisos.findByRecursoAndAccion(recurso, accion).orElseGet(() -> {
                Permiso np = new Permiso(recurso, accion, null);
                Permiso saved = permisos.save(np);
                log.info("Seed: permiso creado {}/{}", recurso, accion);
                return saved;
            });
        }

        // 2. asignaciones rol<->permiso. Si ya existe, no falla.
        for (String[] rp : PERMISOS_POR_ROL) {
            String rol = rp[0];
            String[] parts = rp[1].split(":");
            permisos.findByRecursoAndAccion(parts[0], parts[1]).ifPresent(p -> {
                // Si ya existe la asignacion, no-op. Si no, la crea.
                boolean existe = rolPermisos.findByRol(rol).stream()
                        .anyMatch(rp2 -> rp2.getPermisoId().equals(p.getId()));
                if (!existe) {
                    rolPermisos.save(new RolPermiso(rol, p.getId()));
                    log.info("Seed: rol={} -> {}:{}", rol, parts[0], parts[1]);
                }
            });
        }

        // 3. usuario admin inicial (si no existe)
        if (usuarios.findByEmail(adminEmail).isEmpty()) {
            Usuario admin = new Usuario();
            admin.setEmail(adminEmail);
            admin.setPasswordHash(hasher.hash(adminPassword));
            admin.setRol("MASTER");
            admin.setHabilitado(true);
            usuarios.save(admin);
            log.warn("Seed: usuario ADMIN creado ({}) con password '{}' — CAMBIAR EN PROD",
                    adminEmail, adminPassword);
        }
    }
}
