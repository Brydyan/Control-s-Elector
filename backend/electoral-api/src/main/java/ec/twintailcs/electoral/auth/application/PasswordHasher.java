package ec.twintailcs.electoral.auth.application;

/**
 * Politica de contrasenas y hashing.
 *
 * <p>BCrypt con cost 12 (default Spring Security actualizado para 2026+). Cost 10 esta bien para
 * dev, 12 recomendado para prod por hardware moderno; ajustar si vemos latencia notable (>200ms
 * p95).
 *
 * <p>NUNCA guardar plain text. NUNCA loguear el hash entero (los primeros 4-8 chars suficiente para
 * debugging).
 */
public interface PasswordHasher {

  /** Hashea la password en claro. Resultado incluye el cost embedido. */
  String hash(String passwordPlano);

  /** Verifica si el hash corresponde a la password en claro. */
  boolean matches(String passwordPlano, String hashGuardado);
}
