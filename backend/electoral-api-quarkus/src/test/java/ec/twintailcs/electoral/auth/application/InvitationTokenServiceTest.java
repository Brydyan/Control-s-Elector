package ec.twintailcs.electoral.auth.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.auth.domain.InvitacionException;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Emision y validacion del token de activacion.
 *
 * <p>El token es la garantia de que solo quien recibio el email puede activar la cuenta, asi que el
 * test se concentra en las tres cosas que lo sostienen: la firma, la expiracion a 24 horas y el
 * hecho de que un token de activacion no sirva como credencial de sesion.
 *
 * <p>El {@link JwtService} y el {@link TokenHasher} se construyen a mano, con su constructor
 * publico: el test no necesita el contenedor de CDI para probar el token, y asi sigue siendo un
 * test unitario.
 */
class InvitationTokenServiceTest {

  private static final String SECRETO = "secreto-de-prueba-de-32-caracteres-minimo-para-hs256";

  private final JwtService jwt =
      new JwtService(SECRETO, "15m", "7d", "24h", "control-elector-test");

  private final InvitationTokenService tokenService =
      new InvitationTokenService(jwt, new TokenHasher("pepper-de-prueba"));

  @Test
  @DisplayName("el token generado expira en 24 horas")
  void elTokenGeneradoExpiraEn24Horas() {
    Instant antes = Instant.now();
    String token = tokenService.generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");
    Instant expira = tokenService.expiresAt(token);

    Duration ventana = Duration.between(antes, expira);
    assertThat(ventana.toHours())
        .as("el token debería vivir 24 horas, no %s", ventana)
        .isBetween(23L, 24L);
  }

  @Test
  @DisplayName("un token válido devuelve el id público del usuario")
  void unTokenValidoDevuelveElIdPublico() {
    UUID publicId = UUID.randomUUID();
    String token = tokenService.generateToken(publicId, "a@b.ec", "OBSERVER");

    assertThat(tokenService.validateToken(token)).isEqualTo(publicId);
  }

  @Test
  @DisplayName("un token expirado se rechaza")
  void unTokenExpiradoSeRechaza() {
    // "PT-1S" en ISO-8601: una vigencia negativa de un segundo, de modo que el token
    // nace vencido. El formato corto no admite negativos ("-1s" no es una unidad
    // reconocida por el parser de duraciones).
    JwtService jwtVencido = new JwtService(SECRETO, "15m", "7d", "PT-1S", "control-elector-test");
    InvitationTokenService servicioVencido =
        new InvitationTokenService(jwtVencido, new TokenHasher("pepper-de-prueba"));

    String token = servicioVencido.generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");

    assertThatThrownBy(() -> servicioVencido.validateToken(token))
        .isInstanceOf(InvitacionException.class)
        .hasMessageContaining("invalido");
  }

  @Test
  @DisplayName("un token con firma inválida se rechaza")
  void unTokenConFirmaInvalidaSeRechaza() {
    JwtService jwtOtro =
        new JwtService(
            "otro-secreto-de-prueba-de-32-caracteres-minimo-hs256",
            "15m",
            "7d",
            "24h",
            "control-elector-test");
    String tokenFalso =
        new InvitationTokenService(jwtOtro, new TokenHasher("pepper-de-prueba"))
            .generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");

    assertThatThrownBy(() -> tokenService.validateToken(tokenFalso))
        .isInstanceOf(InvitacionException.class)
        .hasMessageContaining("invalido");
  }

  @Test
  @DisplayName("un token vacío o mal formado se rechaza")
  void unTokenVacioOMalFormadoSeRechaza() {
    assertThatThrownBy(() -> tokenService.validateToken(""))
        .isInstanceOf(InvitacionException.class);
    assertThatThrownBy(() -> tokenService.validateToken("no-es-un-jwt"))
        .isInstanceOf(InvitacionException.class);
    assertThatThrownBy(() -> tokenService.validateToken(null))
        .isInstanceOf(InvitacionException.class);
  }

  @Test
  @DisplayName("un token de activación no sirve como credencial de sesión")
  void unTokenDeActivacionNoSirveComoCredencialDeSesion() {
    // El `typ` es lo que separa los dos dominios. Si este test cae, un token que viaja en un
    // email podría presentarse como credencial de sesión y entrar al sistema sin activarlo.
    String token = tokenService.generateToken(UUID.randomUUID(), "a@b.ec", "MASTER");

    assertThatThrownBy(() -> jwt.parseAccess(token))
        .isInstanceOf(JwtService.InvalidJwtException.class)
        .hasMessageContaining("access");
  }

  @Test
  @DisplayName("el hash del token no es el token: la base no guarda bearer secrets")
  void elHashDelTokenNoEsElToken() {
    String token = tokenService.generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");
    String hash = tokenService.hashOf(token);

    assertThat(hash).as("el hash no puede ser el token").isNotEqualTo(token);
    assertThat(hash).as("el hash es determinístico").isEqualTo(tokenService.hashOf(token));
    assertThat(tokenService.coincideConHash(token, hash))
        .as("el token coincide con su hash")
        .isTrue();
    assertThat(tokenService.coincideConHash("otro-token", hash))
        .as("otro token no coincide")
        .isFalse();
  }

  @Test
  @DisplayName("un hash nulo nunca coincide: es la marca de token consumido")
  void unHashNuloNuncaCoincide() {
    String token = tokenService.generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");

    assertThat(tokenService.coincideConHash(token, null))
        .as("un hash nulo significa que el token ya se consumió")
        .isFalse();
  }

  @Test
  @DisplayName("el token que viaja en el link no tiene caracteres que el correo pueda corromper")
  void elTokenDelLinkNoTieneCaracteresQueElCorreoPuedaCorromper() {
    // Regresión del bug real: el JWT pelado puede terminar en "=" (relleno base64), y
    // el transporte quoted-printable del correo corta las líneas largas con un salto
    // suave. Si el corte cae sobre ese "=", el correo entrega un token corrupto y el
    // enlace del usuario no sirve. El envoltorio en base64url sin relleno deja solo
    // [A-Za-z0-9_-].
    String jwt = tokenService.generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");
    String envuelto = tokenService.envolverParaLink(jwt);

    assertThat(envuelto)
        .as("no debe tener relleno, que es lo que el correo se come")
        .doesNotContain("=");
    assertThat(envuelto).as("no debe tener puntos, que cortan la URL").doesNotContain(".");
    assertThat(envuelto).as("solo caracteres URL-safe y QP-safe").matches("[A-Za-z0-9_-]+");
  }

  @Test
  @DisplayName("el envoltorio devuelve el JWT exacto, con su firma intacta")
  void elEnvoltorioDevuelveElJwtExacto() {
    String jwt = tokenService.generateToken(UUID.randomUUID(), "a@b.ec", "OBSERVER");

    String jwtDesenvuelto = tokenService.desenvolverDeLink(tokenService.envolverParaLink(jwt));

    assertThat(jwtDesenvuelto).as("el JWT debe volver byte a byte igual").isEqualTo(jwt);
    // Y tiene que seguir validando: si la recomposicion alterara una sola base64,
    // la firma dejaria de verificar y el token seria inservible.
    assertThat(tokenService.validateToken(jwtDesenvuelto))
        .as("el token desenvuelto tiene que seguir siendo valido")
        .isNotNull();
  }
}
