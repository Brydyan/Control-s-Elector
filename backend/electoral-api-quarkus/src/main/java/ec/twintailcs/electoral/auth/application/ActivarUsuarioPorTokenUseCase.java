package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.application.port.InvitationEmailQueue;
import ec.twintailcs.electoral.auth.domain.EstadoUsuario;
import ec.twintailcs.electoral.auth.domain.InvitacionException;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.web.dto.ActivarUsuarioResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Activa la cuenta de un usuario a partir del token que recibio por email.
 *
 * <p>Son cinco las condiciones que tienen que cumplirse, y el orden importa:
 *
 * <ol>
 *   <li>El token tiene que ser legible, tener firma valida y no haber expirado.
 *   <li>El usuario tiene que existir.
 *   <li>El usuario no puede estar ya activado.
 *   <li>El token tiene que ser el vigente: el hash guardado tiene que coincidir con el del token
 *       recibido. Esto es lo que impide reutilizar un token (decision D4) y lo que invalida el
 *       token viejo cuando MASTER reenvio la invitacion (decision D5).
 *   <li>La contrasena tiene que cumplir los requisitos y las dos copias tienen que coincidir.
 * </ol>
 *
 * <p>La activacion consume el token: deja el hash en NULL. Un segundo POST con el mismo token cae
 * en el punto 4 y se rechaza.
 */
@ApplicationScoped
public class ActivarUsuarioPorTokenUseCase {

  private final InvitationTokenService tokenService;
  private final PasswordValidationService passwordValidator;
  private final PasswordHasher passwordHasher;
  private final InvitationAuditService auditService;
  private final InvitationEmailQueue emailQueue;
  private final TransactionSynchronizationRegistry synchronizationRegistry;
  private final UserInvitationService invitationService;

  public ActivarUsuarioPorTokenUseCase(
      InvitationTokenService tokenService,
      PasswordValidationService passwordValidator,
      PasswordHasher passwordHasher,
      InvitationAuditService auditService,
      InvitationEmailQueue emailQueue,
      TransactionSynchronizationRegistry synchronizationRegistry,
      UserInvitationService invitationService) {
    this.tokenService = tokenService;
    this.passwordValidator = passwordValidator;
    this.passwordHasher = passwordHasher;
    this.auditService = auditService;
    this.emailQueue = emailQueue;
    this.synchronizationRegistry = synchronizationRegistry;
    this.invitationService = invitationService;
  }

  /**
   * Activa la cuenta.
   *
   * @param token token de activacion recibido por email
   * @param password contrasena elegida por el usuario
   * @param passwordConfirm la misma contrasena
   * @return datos de la cuenta activada
   * @throws InvitacionException 400 si el token no sirve o la contrasena no cumple los requisitos;
   *     409 si la cuenta ya estaba activada o el token ya se consumio
   */
  @Transactional
  public ActivarUsuarioResponse execute(
      String tokenEnviado, String password, String passwordConfirm) {
    // Lo que llega por la URL es el token envuelto en base64url sin relleno. Se
    // deshace aqui para trabajar siempre con el JWT real y su firma intacta.
    String token = tokenService.desenvolverDeLink(tokenEnviado);
    UUID publicId = tokenService.validateToken(token);

    Usuario usuario = Usuario.find("publicId", publicId).firstResult();
    if (usuario == null) {
      // No se distingue del token invalido: el mismo mensaje, para no confirmar
      // que el id existe.
      throw InvitacionException.peticionInvalida("Link de activación inválido");
    }

    if (usuario.estaActivo()) {
      auditService.logActivationRejected(usuario.getId(), "usuario_ya_activado");
      throw InvitacionException.conflicto("El usuario ya está activado");
    }

    if (!tokenService.coincideConHash(token, usuario.getActivationTokenHash())) {
      auditService.logActivationFailed(usuario.getId(), "token_consumed");
      throw InvitacionException.conflicto(
          "El token ya fue consumido o la invitación fue reenviada. Pedí una invitación nueva.");
    }

    if (!Objects.equals(password, passwordConfirm)) {
      auditService.logActivationFailed(usuario.getId(), "password_mismatch");
      throw InvitacionException.peticionInvalida("Las contraseñas no coinciden");
    }

    PasswordValidationResult validacion = passwordValidator.validate(password);
    if (!validacion.isValid()) {
      auditService.logActivationFailed(
          usuario.getId(), "password_weak:" + String.join(",", validacion.getMissing()));
      throw InvitacionException.peticionInvalida(
          "La contraseña no cumple los requisitos: falta "
              + faltantesEnCastellano(validacion.getMissing()));
    }

    usuario.setPasswordHash(passwordHasher.hash(password));
    usuario.setEstadoUsuario(EstadoUsuario.ACTIVO);
    usuario.setActivatedAt(Instant.now());
    // Consumir el token: el hash a NULL es la marca de "ya usado" (decision D4).
    usuario.setActivationTokenHash(null);
    usuario.persist();

    auditService.logActivationSuccess(usuario.getId());

    // La invitacion que trajo a esta persona queda como activada: sin esto, el
    // historico seguiria diciendo "INVITED" para una cuenta que ya funciona.
    invitationService.marcarInvitacionActivada(usuario.getId());

    encolarConfirmacionDespuesDelCommit(usuario);

    return new ActivarUsuarioResponse(
        usuario.getPublicId(),
        usuario.getNombre(),
        usuario.getApellido(),
        usuario.getEmail(),
        usuario.getRolNombre());
  }

  /** Encola el email de confirmacion una vez confirmada la transaccion. */
  private void encolarConfirmacionDespuesDelCommit(Usuario usuario) {
    EmailAEnviar confirmacion =
        new EmailAEnviar(
            usuario.getId(),
            usuario.getEmail(),
            usuario.getEmail(),
            usuario.getNombre(),
            usuario.getApellido(),
            usuario.getRolNombre(),
            null,
            null,
            1,
            EmailAEnviar.TipoEmail.CONFIRMACION);

    synchronizationRegistry.registerInterposedSynchronization(
        new Synchronization() {
          @Override
          public void beforeCompletion() {
            // El email sale despues del commit, nunca antes.
          }

          @Override
          public void afterCompletion(int status) {
            if (status == Status.STATUS_COMMITTED) {
              emailQueue.enqueue(confirmacion);
            }
          }
        });
  }

  /** Traduce los identificadores de requisito a texto para el mensaje de error. */
  private static String faltantesEnCastellano(List<String> faltantes) {
    return faltantes.stream()
        .map(
            requisito ->
                switch (requisito) {
                  case PasswordValidationResult.REQ_LENGTH -> "longitud mínima";
                  case PasswordValidationResult.REQ_UPPERCASE -> "una mayúscula";
                  case PasswordValidationResult.REQ_NUMBER -> "un número";
                  case PasswordValidationResult.REQ_SPECIAL -> "un carácter especial";
                  default -> requisito;
                })
        .reduce((a, b) -> a + ", " + b)
        .orElse("");
  }
}
