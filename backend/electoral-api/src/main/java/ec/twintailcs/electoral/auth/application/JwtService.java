package ec.twintailcs.electoral.auth.application;

import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.crypto.MACSigner;
import com.nimbusds.jose.crypto.MACVerifier;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.UUID;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Emite y valida JWT firmados con HMAC-SHA256 (HS256).
 *
 * <p>Strategia: dos tokens separados. - access (TTL 15m): se valida en cada request via el filter.
 * Header. - refresh (TTL 7d): se envia una sola vez al login. Se hashea y se guarda en la tabla
 * sesion (jamas el token plano).
 *
 * <p>Cuando el access expira, el cliente llama /auth/refresh con el refresh; si el hash coincide y
 * la sesion no esta revocada, se emite un nuevo par y se rota el refresh (old hash invalidado).
 *
 * <p>Algoritmo: HS256 (simetrico, ideal para backend unico). Si en el futuro se quiere OAuth/OIDC
 * con terceros, migrar a RS256 y publicar JWKS. Por ahora HS256 basta y sobra.
 */
@Service
public class JwtService {

  private final SecretKey signingKey;
  private final Duration accessTtl;
  private final Duration refreshTtl;
  private final String issuer;

  public JwtService(
      @Value("${app.jwt.secret}") String secret,
      @Value("${app.jwt.access-ttl:15m}") Duration accessTtl,
      @Value("${app.jwt.refresh-ttl:7d}") Duration refreshTtl,
      @Value("${app.jwt.issuer:control-elector}") String issuer) {
    if (secret == null || secret.length() < 32) {
      throw new IllegalStateException(
          "app.jwt.secret debe tener al menos 32 caracteres (HS256 requires >= 256 bits)");
    }
    this.signingKey = new SecretKeySpec(secret.getBytes(), "HmacSHA256");
    this.accessTtl = accessTtl;
    this.refreshTtl = refreshTtl;
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
      if (claims.getExpirationTime() == null) throw new InvalidJwtException("Falta exp");
      if (!claims.getExpirationTime().toInstant().isAfter(Instant.now()))
        throw new InvalidJwtException("JWT expirado");
      if (claims.getClaim("typ") == null) throw new InvalidJwtException("Falta typ");
      return new ParsedJwt(claims);
    } catch (java.text.ParseException | JOSEException e) {
      throw new InvalidJwtException("No se pudo parsear JWT: " + e.getMessage());
    }
  }

  public ParsedJwt parseAccess(String token) {
    ParsedJwt parsed = parse(token);
    if (!"access".equals(parsed.type()))
      throw new InvalidJwtException("Token no es de tipo access");
    return parsed;
  }

  public ParsedJwt parseRefresh(String token) {
    ParsedJwt parsed = parse(token);
    if (!"refresh".equals(parsed.type()))
      throw new InvalidJwtException("Token no es de tipo refresh");
    return parsed;
  }

  /** Token invalido (firma rota, expirado o malformado). Mapea a 401. */
  public static class InvalidJwtException extends RuntimeException {
    public InvalidJwtException(String msg) {
      super(msg);
    }
  }

  private String sign(java.util.function.UnaryOperator<JWTClaimsSet.Builder> claimsBuilder) {
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
