package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * Construccion de claves de almacenamiento del rate limiting.
 *
 * <p>Formato: {@code rl:v1:<politica>:<sha256(identidad)>}. La identidad (email, IP, subject) se
 * guarda hasheada para que Redis no contenga datos personales en claro. El prefijo {@code rl:} lo
 * separa de las claves de cache ({@code cache:}), y {@code v1} permite cambiar el esquema sin
 * colisiones.
 */
final class RateLimitKeys {

  static final String PREFIX = "rl:v1:";

  /** 128 bits del hash: suficiente para no colisionar entre identidades de una politica. */
  private static final int HASH_HEX_CHARS = 32;

  private RateLimitKeys() {}

  static String of(String policy, String identity) {
    if (identity == null || identity.isBlank()) {
      throw new IllegalArgumentException("La identidad de rate limit no puede ser vacia");
    }
    return PREFIX + policy + ":" + sha256(identity).substring(0, HASH_HEX_CHARS);
  }

  private static String sha256(String value) {
    try {
      MessageDigest digest = MessageDigest.getInstance("SHA-256");
      return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
    } catch (NoSuchAlgorithmException e) {
      throw new IllegalStateException("SHA-256 no disponible en la JVM", e);
    }
  }
}
