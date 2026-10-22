package ec.twintailcs.electoral.auth.application;

import jakarta.enterprise.context.ApplicationScoped;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/** Hashea el refresh token con un pepper para poder revocarlo sin guardarlo en claro. */
@ApplicationScoped
public class TokenHasher {

  private final String refreshPepper;

  public TokenHasher(@ConfigProperty(name = "app.jwt.refresh-pepper") String refreshPepper) {
    this.refreshPepper = refreshPepper;
  }

  public String hashOf(String token) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(refreshPepper.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
      byte[] raw = mac.doFinal(token.getBytes(StandardCharsets.UTF_8));
      return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    } catch (NoSuchAlgorithmException | java.security.InvalidKeyException e) {
      throw new IllegalStateException("HmacSHA256 indisponible", e);
    }
  }
}
