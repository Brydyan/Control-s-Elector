package ec.twintailcs.electoral.auth.application;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Implementacion BCrypt del hasher.
 *
 * <p>Spring Security mantiene la implementacion; nosotros somos apenas un wrapper para poder
 * mockear en tests.
 */
@Component
public class BCryptPasswordHasher implements PasswordHasher {

  private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(12);

  @Override
  public String hash(String passwordPlano) {
    return encoder.encode(passwordPlano);
  }

  @Override
  public boolean matches(String passwordPlano, String hashGuardado) {
    if (passwordPlano == null || hashGuardado == null) return false;
    return encoder.matches(passwordPlano, hashGuardado);
  }
}
