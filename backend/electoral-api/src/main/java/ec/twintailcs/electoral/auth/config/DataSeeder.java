package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.auth.application.EmailGenerator;
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
 * <p>Liquibase ya maneja el schema (001-initial-schema.sql) y los datos de referencia de RBAC
 * (002-rbac-seed.sql: permisos + rol_permiso). Este seeder solo crea el admin inicial. Email se
 * genera automáticamente a partir de nombre, apellido y rol con el formato: {3 letras nombre}{3
 * letras apellido}{3 letras rol}@control.ttcs
 */
@Component
public class DataSeeder implements CommandLineRunner {

  private static final Logger log = LoggerFactory.getLogger(DataSeeder.class);

  private final UsuarioRepository usuarios;
  private final RolRepository roles;
  private final PasswordHasher hasher;
  private final EmailGenerator emailGenerator;

  @Value("${app.seed.admin-nombre:Admin}")
  private String adminNombre;

  @Value("${app.seed.admin-apellido:Sistema}")
  private String adminApellido;

  @Value("${app.seed.admin-password:admin12345}")
  private String adminPassword;

  public DataSeeder(
      UsuarioRepository usuarios,
      RolRepository roles,
      PasswordHasher hasher,
      EmailGenerator emailGenerator) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.hasher = hasher;
    this.emailGenerator = emailGenerator;
  }

  @Override
  @Transactional
  public void run(String... args) {
    String emailGenerado = emailGenerator.generar(adminNombre, adminApellido, "MASTER");
    if (usuarios.findByEmail(emailGenerado).isPresent()) return;

    var masterRol =
        roles
            .findByNombre("MASTER")
            .orElseThrow(
                () ->
                    new IllegalStateException(
                        "Rol MASTER no encontrado. Ejecutar migracion 005-fks primero."));

    Usuario admin = new Usuario();
    admin.setNombre(adminNombre);
    admin.setApellido(adminApellido);
    admin.setEmail(emailGenerado);
    admin.setPasswordHash(hasher.hash(adminPassword));
    admin.setRol(masterRol);
    admin.setHabilitado(true);
    usuarios.save(admin);
    log.warn(
        "Seed: usuario MASTER creado ({}) con email '{}' y password '{}' — CAMBIAR EN PROD",
        adminNombre + " " + adminApellido,
        emailGenerado,
        adminPassword);
  }
}
