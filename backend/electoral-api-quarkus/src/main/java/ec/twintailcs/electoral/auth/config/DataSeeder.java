package ec.twintailcs.electoral.auth.config;

import ec.twintailcs.electoral.auth.application.EmailGenerator;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.Usuario;
import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Crea el usuario MASTER inicial si no existe.
 *
 * <p>Port del DataSeeder del backend Spring: mismo email derivado
 * ({@code {3 letras nombre}{3 letras apellido}{3 letras rol}@control.ttcs}) y misma password. El
 * usuario tiene que existir con el MISMO email en las dos implementaciones, porque la suite de
 * regresion por contrato usa esas credenciales.
 */
@ApplicationScoped
public class DataSeeder {

  private static final Logger log = Logger.getLogger(DataSeeder.class);

  @Inject EmailGenerator emailGenerator;

  @Inject PasswordHasher hasher;

  @ConfigProperty(name = "app.seed.admin-nombre", defaultValue = "Admin")
  String adminNombre;

  @ConfigProperty(name = "app.seed.admin-apellido", defaultValue = "Sistema")
  String adminApellido;

  @ConfigProperty(name = "app.seed.admin-password", defaultValue = "admin12345")
  String adminPassword;

  void onStart(@Observes StartupEvent event) {
    try {
      crearSiFalta();
    } catch (Exception e) {
      log.error("No se pudo crear el usuario MASTER inicial", e);
    }
  }

  @Transactional
  void crearSiFalta() {
    String email = emailGenerator.generar(adminNombre, adminApellido, "MASTER");
    if (Usuario.find("email", email).firstResult() != null) {
      return;
    }
    Rol master = Rol.find("nombre", "MASTER").firstResult();
    if (master == null) {
      throw new IllegalStateException("Rol MASTER no encontrado. Correr la migracion de RBAC primero.");
    }
    Usuario admin = new Usuario();
    admin.setNombre(adminNombre);
    admin.setApellido(adminApellido);
    admin.setEmail(email);
    admin.setPasswordHash(hasher.hash(adminPassword));
    admin.setRol(master);
    admin.setHabilitado(true);
    admin.persist();
    log.warnf(
        "Seed: usuario MASTER creado (%s %s) con email '%s' y password '%s' - CAMBIAR EN PROD",
        adminNombre, adminApellido, email, adminPassword);
  }
}
