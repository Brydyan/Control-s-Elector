package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Carga el usuario admin inicial en la DB al arrancar si no existe.
 *
 * Liquibase ya maneja el schema (001-initial-schema.sql) y los datos de
 * referencia de RBAC (002-rbac-seed.sql: permisos + rol_permiso).
 * Este seeder solo crea el admin inicial cuyo password_hash depende de
 * {@code app.seed.admin-password} (variable de entorno / secret).
 */
@Component
public class DataSeeder implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

    private final UsuarioRepository usuarios;
    private final RolRepository roles;
    private final PasswordHasher hasher;

    @Value("${app.seed.admin-email:admin@control-elector.local}")
    private String adminEmail;
    @Value("${app.seed.admin-password:admin12345}")
    private String adminPassword;

    public DataSeeder(UsuarioRepository usuarios, RolRepository roles, PasswordHasher hasher) {
        this.usuarios = usuarios;
        this.roles = roles;
        this.hasher = hasher;
    }

    @Override
    @Transactional
    public void run(String... args) {
        if (usuarios.findByEmail(adminEmail).isPresent()) return;

        var masterRol = roles.findByNombre("MASTER")
                .orElseThrow(() -> new IllegalStateException(
                        "Rol MASTER no encontrado. Ejecutar migracion 005-fks primero."));

        Usuario admin = new Usuario();
        admin.setEmail(adminEmail);
        admin.setPasswordHash(hasher.hash(adminPassword));
        admin.setRol(masterRol);
        admin.setHabilitado(true);
        usuarios.save(admin);
        log.warn("Seed: usuario ADMIN creado ({}) con password '{}' — CAMBIAR EN PROD",
                adminEmail, adminPassword);
    }
}
