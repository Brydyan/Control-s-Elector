package ec.twintailcs.electoral.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import org.mindrot.jbcrypt.BCrypt;

/** BCrypt con costo 12, igual que el backend Spring. */
@ApplicationScoped
public class PasswordHasher {

  private static final int COST = 12;

  public String hash(String passwordPlano) {
    return BCrypt.hashpw(passwordPlano, BCrypt.gensalt(COST));
  }

  public boolean matches(String passwordPlano, String hashGuardado) {
    if (passwordPlano == null || hashGuardado == null) {
      return false;
    }
    try {
      return BCrypt.checkpw(passwordPlano, hashGuardado);
    } catch (IllegalArgumentException e) {
      return false;
    }
  }
}
