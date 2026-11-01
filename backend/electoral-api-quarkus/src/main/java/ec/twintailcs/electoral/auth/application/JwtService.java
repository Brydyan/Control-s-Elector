package ec.twintailcs.electoral.auth.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import ec.twintailcs.electoral.shared.config.Durations;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import java.util.function.UnaryOperator;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Emite y valida JWT firmados con HMAC-SHA256 (HS256).
 *
 * <p>Port exacto del {@code JwtService} del backend Spring: mismos claims, misma validacion y mismo
 * algoritmo. Los tokens emitidos por una implementacion tienen que ser aceptados por la otra
 * durante el strangler.
 */
@ApplicationScoped
public class JwtService {

  private final SecretKey signingKey;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final String issuer;

  public JwtService(
      @ConfigProperty(name = "app.jwt.secret") String secret,
      @ConfigProperty(name = "app.jwt.access-ttl") String accessTtl,
      @ConfigProperty(name = "app.jwt.refresh-ttl") String refreshTtl,
      @ConfigProperty(name = "app.jwt.issuer") String issuer) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "app.jwt.secret debe tener al menos 32 caracteres (HS256 requires >= 256 bits)");
    }
    this.signingKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    this.accessTtl = Durations.parse(accessTtl);
    this.refreshTtl = Durations.parse(refreshTtl);
    this.issuer = issuer;
  }

  /** Emite access + refresh para un usuario dado. */
  public TokenPair issue(
      UUID publicId, String email, String rol, List<String> permissions, String sesionId) {
    Instant now = Instant.now();
    String access =
        sign(
            builder ->
                builder
                    .subject(publicId.toString())
                    .issuer(issuer)
                    .claim("typ", "access")
                    .claim("email", email)
                    .claim("rol", rol)
                    .claim("sid", sesionId)
                    .claim("perms", permissions)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(accessTtl)))
                    .jwtID(UUID.randomUUID().toString()));

    String refresh =
        sign(
            builder ->
                builder
                    .subject(publicId.toString())
                    .issuer(issuer)
                    .claim("typ", "refresh")
                    .claim("sid", sesionId)
                    .issueTime(Date.from(now))
                    .expirationTime(Date.from(now.plus(refreshTtl)))
                    .jwtID(UUID.randomUUID().toString()));

    return new TokenPair(access, refresh, accessTtl, refreshTtl, sesionId);
  }

  /** Parsea y valida firma + expiracion. Lanza si el token no es valido. */
  public ParsedJwt parse(String token) {
    try {
      SignedJWT jwt = SignedJWT.parse(token);
      if (!jwt.verify(new MACVerifier(signingKey))) {
        throw new InvalidJwtException("Firma JWT invalida");
      }
      JWTClaimsSet claims = jwt.getJWTClaimsSet();
      if (claims.getIssuer() == null || !issuer.equals(claims.getIssuer())) {
        throw new InvalidJwtException("Issuer JWT invalido");
      }
      if (claims.getExpirationTime() == null) {
        throw new InvalidJwtException("Falta exp");
      }
      if (!claims.getExpirationTime().toInstant().isAfter(Instant.now())) {
        throw new InvalidJwtException("JWT expirado");
      }
      if (claims.getClaim("typ") == null) {
        throw new InvalidJwtException("Falta typ");
      }
      return new ParsedJwt(claims);
    } catch (java.text.ParseException | JOSEException e) {
      throw new InvalidJwtException("No se pudo parsear JWT: " + e.getMessage());
    }
  }

  public ParsedJwt parseAccess(String token) {
    ParsedJwt parsed = parse(token);
    if (!"access".equals(parsed.type())) {
      throw new InvalidJwtException("Token no es de tipo access");
    }
    return parsed;
  }

  /** Valida que el token sea de tipo refresh. Es lo que usa el canje de refresh token. */
  public ParsedJwt parseRefresh(String token) {
    ParsedJwt parsed = parse(token);
    if (!"refresh".equals(parsed.type())) {
      throw new InvalidJwtException("Token no es de tipo refresh");
    }
    return parsed;
  }

  private String sign(UnaryOperator<JWTClaimsSet.Builder> claimsBuilder) {
    try {
      JWTClaimsSet.Builder cb = new JWTClaimsSet.Builder();
      JWTClaimsSet claims = claimsBuilder.apply(cb).build();
      SignedJWT jwt = new SignedJWT(new JWSHeader(JWSAlgorithm.HS256), claims);
      jwt.sign(new MACSigner(signingKey));
      return jwt.serialize();
    } catch (JOSEException e) {
      throw new IllegalStateException("Error firmando JWT", e);
    }
  }

  /** Token invalido (firma rota, expirado o malformado). Mapea a 401. */
  public static class InvalidJwtException extends RuntimeException {
    public InvalidJwtException(String msg) {
      super(msg);
    }
  }

  /** Par de tokens emitidos juntos. */
  public record TokenPair(
      String access, String refresh, Duration accessTtl, Duration refreshTtl, String sesionId) {}

  /** Claims parseados y validados. */
  public record ParsedJwt(JWTClaimsSet claims) {
    public String subject() {
      return claims.getSubject();
    }

    public String email() {
      return (String) claims.getClaim("email");
    }

    public String rol() {
      return (String) claims.getClaim("rol");
    }

    public String sesionId() {
      return (String) claims.getClaim("sid");
    }

    public String type() {
      return (String) claims.getClaim("typ");
    }

    @SuppressWarnings("unchecked")
    public List<String> perms() {
      Object p = claims.getClaim("perms");
      return p instanceof List ? (List<String>) p : List.of();
    }
  }
}
