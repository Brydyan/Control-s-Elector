package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.InvitacionException;
import jakarta.enterprise.context.ApplicationScoped;
import java.time.Instant;
import java.util.UUID;

/**
 * Emite y valida los tokens de activacion de las invitaciones.
 *
 * <p>El token es un JWT con {@code typ=activation}, firmado por el mismo {@link JwtService} que
 * emite los access tokens. Delegar el firma y la verificacion en un solo lugar evita el error
 * clasico de mantener dos mecanismos de firma: si el token de activacion lo firma una clave
 * distinta, cada cambio de secreto rompe una mitad del sistema sin que ninguna prueba lo note.
 *
 * <p>La verificacion tiene dos capas y ambas tienen que pasar:
 *
 * <ol>
 *   <li>La firma y la expiracion del JWT, que las hace {@code JwtService}.
 *   <li>Que el token siga siendo el vigente para ese usuario, que se comprueba comparando el HMAC
 *       del token recibido con el hash almacenado en {@code usuario.activation_token_hash}.
 * </ol>
 *
 * <p>La segunda capa es la que impide reutilizar un token (decision D4 del design) y la que
 * invalida el token viejo cuando el MASTER reenvia la invitacion: al reenviar se guarda un hash
 * nuevo, y el token anterior deja de coincidir.
 */
@ApplicationScoped
public class InvitationTokenService {

  private final JwtService jwt;
  private final TokenHasher tokenHasher;

  public InvitationTokenService(JwtService jwt, TokenHasher tokenHasher) {
    this.jwt = jwt;
    this.tokenHasher = tokenHasher;
  }

  /**
   * Genera el token de activacion de un usuario.
   *
   * @param publicId id publico del usuario (UUID), nunca el id interno
   * @param email email del usuario
   * @param rol rol asignado
   * @return token JWT serializado, con expiracion a 24 horas
   */
  public String generateToken(UUID publicId, String email, String rol) {
    return jwt.issueActivation(publicId, email, rol);
  }

  /**
   * Valida el token y devuelve el id publico del usuario invitado.
   *
   * @throws InvitacionException 400 si el token esta mal formado, tiene otra firma o expiro
   */
  public UUID validateToken(String token) {
    if (token == null || token.isBlank()) {
      throw InvitacionException.peticionInvalida("Token de activacion vacio");
    }
    try {
      return UUID.fromString(jwt.parseActivation(token).subject());
    } catch (JwtService.InvalidJwtException e) {
      throw InvitacionException.peticionInvalida("Link de activacion invalido o vencido");
    } catch (IllegalArgumentException e) {
      throw InvitacionException.peticionInvalida("Link de activacion invalido o vencido");
    }
  }

  /** Vigencia del token de activacion. */
  public java.time.Duration ttl() {
    return jwt.activationTtl();
  }

  /**
   * Empaqueta el token para viajar en el link del email.
   *
   * <p><b>Por que hace falta.</b> Un JWT en base64url puede terminar con uno o dos caracteres
   * {@code =} de relleno: el relleno depende de la longitud del JSON de los claims, que cambia con
   * el email, el rol y el id del usuario. Ese {@code =} es un problema real en el correo: el
   * transporte quoted-printable corta las lineas largas con un salto suave, y si el corte cae justo
   * sobre el {@code =}, el correo entrega un token <em>corrupto</em> — el enlace llega al usuario y
   * la activacion responde que el link no sirve. No es una hipotesis: se reprodujo con un token
   * real.
   *
   * <p>La solucion es no mandar el JWT pelado. Se manda su codificacion base64url <em>sin</em>
   * relleno, que solo contiene {@code [A-Za-z0-9_-]}: ni un {@code =} que el correo se coma, ni un
   * punto que corte la URL, ni un caracter que haya que escapar. Al llegar al backend se deshace el
   * envoltorio y se recupera el JWT exacto, con su firma intacta.
   *
   * <p>Alternativa descartada — quitarle el relleno al JWT y recomponerlo al validar: la firma del
   * JWT se calcula sobre las cadenas base64 <em>exactas</em> que se emitieron, y al recomponerlas
   * con relleno la cadena que se firma deja de coincidir con la que se emitio. La firma no
   * verifica. Por eso el envoltorio envuelve el JWT completo en vez de tocarlo.
   *
   * @param jwt token JWT completo
   * @return el token listo para poner en la URL
   */
  public String envolverParaLink(String jwt) {
    return java.util.Base64.getUrlEncoder()
        .withoutPadding()
        .encodeToString(jwt.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }

  /**
   * Deshace el envoltorio y devuelve el JWT original.
   *
   * @throws InvitacionException 400 si el envoltorio no es valido
   */
  public String desenvolverDeLink(String envuelto) {
    if (envuelto == null || envuelto.isBlank()) {
      throw InvitacionException.peticionInvalida("Link de activación inválido");
    }
    try {
      return new String(
          java.util.Base64.getUrlDecoder().decode(envuelto),
          java.nio.charset.StandardCharsets.UTF_8);
    } catch (IllegalArgumentException e) {
      throw InvitacionException.peticionInvalida("Link de activación inválido");
    }
  }

  /**
   * Momento en que expira un token recien emitido.
   *
   * <p>Lo calcula el servicio en vez de releer el JWT: el token es freshly issued y su expiracion
   * es exactamente ahora mas la vigencia.
   */
  public Instant expiraEn() {
    return Instant.now().plus(jwt.activationTtl());
  }

  /**
   * Momento en que expira el token.
   *
   * @throws InvitacionException 400 si el token no se puede leer
   */
  public Instant expiresAt(String token) {
    try {
      return jwt.parseActivation(token).claims().getExpirationTime().toInstant();
    } catch (JwtService.InvalidJwtException e) {
      throw InvitacionException.peticionInvalida("Link de activacion invalido o vencido");
    }
  }

  /**
   * {@code true} si el token ya expiro.
   *
   * <p>Un token que ni siquiera se puede leer no esta expirado: esta roto. La distincion importa
   * porque el mensaje que ve el usuario final es distinto ("el link vencio" vs "el link no sirve").
   */
  public boolean isExpired(String token) {
    try {
      return !jwt.parseActivation(token)
          .claims()
          .getExpirationTime()
          .toInstant()
          .isAfter(Instant.now());
    } catch (JwtService.InvalidJwtException e) {
      return false;
    }
  }

  /**
   * HMAC del token, que es lo que se guarda en {@code usuario.activation_token_hash}.
   *
   * <p>Guardar el hash y no el token evita que un dump de la base entregue invitaciones activables.
   */
  public String hashOf(String token) {
    return tokenHasher.hashOf(token);
  }

  /**
   * Compara el token recibido con el hash almacenado.
   *
   * <p>La comparacion es en tiempo constante: es la misma razon por la que el HMAC se calcula sobre
   * el token entero.
   */
  public boolean coincideConHash(String token, String hashAlmacenado) {
    if (hashAlmacenado == null || token == null) {
      return false;
    }
    return java.security.MessageDigest.isEqual(
        hashOf(token).getBytes(java.nio.charset.StandardCharsets.UTF_8),
        hashAlmacenado.getBytes(java.nio.charset.StandardCharsets.UTF_8));
  }
}
