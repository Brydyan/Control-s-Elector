package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.application.port.InvitationEmailQueue;
import ec.twintailcs.electoral.auth.domain.InvitacionException;
import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.EnviarInvitacionResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Envio de invitaciones: genera el token, lo deja vigente y encola el email.
 *
 * <p>El envio es a un conjunto de usuarios que ya existen y que estan en estado {@code
 * PENDIENTE_INVITACION}. Los conflictos de estado (un usuario que ya esta activo) se rechazan de
 * forma atomica con un 409 y sin enviar ningun email: es lo que dice el spec, y tiene sentido — si
 * el MASTER selecciono fifty cuentas y una ya estaba activa, no quiere que se_inviten las otras
 * cuarenta y nueve sin querer.
 *
 * <p>El encolado del email ocurre <em>despues</em> del commit, no dentro de la transaccion. Si el
 * email saliera durante la transaccion y esta fallara despues, el usuario recibiria un link con un
 * token que nunca llego a la base. Encolar despues del commit elimina esa ventana.
 */
@ApplicationScoped
public class InvitacionService {

  private final InvitationTokenService tokenService;
  private final InvitationAuditService auditService;
  private final InvitationEmailQueue emailQueue;
  private final TransactionSynchronizationRegistry synchronizationRegistry;
  private final CurrentUser currentUser;
  private final String frontendUrl;

  public InvitacionService(
      InvitationTokenService tokenService,
      InvitationAuditService auditService,
      InvitationEmailQueue emailQueue,
      TransactionSynchronizationRegistry synchronizationRegistry,
      CurrentUser currentUser,
      @ConfigProperty(name = "app.public.frontend-url") String frontendUrl) {
    this.tokenService = tokenService;
    this.auditService = auditService;
    this.emailQueue = emailQueue;
    this.synchronizationRegistry = synchronizationRegistry;
    this.currentUser = currentUser;
    this.frontendUrl = frontendUrl;
  }

  /**
   * Envia las invitaciones a los usuarios indicados.
   *
   * @param publicIds ids publicos de los usuarios a invitar
   * @return cuantos emails quedaron encolados
   * @throws InvitacionException 409 si alguno de los usuarios ya esta activo
   * @throws RecursoNoEncontradoException 404 si algum de los usuarios no existe
   */
  @Transactional
  public EnviarInvitacionResponse enviarInvitaciones(List<UUID> publicIds) {
    List<Usuario> usuarios = resolverUsuarios(publicIds);
    List<EmailAEnviar> aEnviar = new ArrayList<>();
    Long actorId = actorId();

    Instant ahora = Instant.now();
    for (Usuario usuario : usuarios) {
      boolean reenvio = usuario.getActivationTokenHash() != null;
      String token =
          tokenService.generateToken(
              usuario.getPublicId(), usuario.getEmail(), usuario.getRolNombre());
      Instant expira = tokenService.expiraEn();

      usuario.setActivationTokenHash(tokenService.hashOf(token));
      usuario.setInvitationSentAt(ahora);
      usuario.persist();

      aEnviar.add(
          new EmailAEnviar(
              usuario.getId(),
              usuario.getEmailPersonal(),
              usuario.getEmail(),
              usuario.getNombre(),
              usuario.getApellido(),
              usuario.getRolNombre(),
              linkDeActivacion(token),
              expira.toString(),
              reenvio ? 2 : 1,
              reenvio ? EmailAEnviar.TipoEmail.REENVIO : EmailAEnviar.TipoEmail.INVITACION));

      if (reenvio) {
        auditService.logInvitationResent(
            actorId, usuario.getId(), usuario.getEmail(), expira.toString());
      } else {
        auditService.logInvitationSent(
            actorId, usuario.getId(), usuario.getEmail(), expira.toString());
      }
    }

    encolarDespuesDelCommit(aEnviar);

    return new EnviarInvitacionResponse(aEnviar.size(), List.of());
  }

  /**
   * Id del usuario que ejecuto la accion.
   *
   * <p>Es el MASTER que invita. Se resuelve una sola vez por lote: {@code registrado_por} tiene
   * clave foranea a {@code usuario}, asi que tiene que ser alguien ya commiteado — el usuario
   * invitado puede haberse creado en esta misma transaccion y todavia no existe para otras.
   */
  private Long actorId() {
    String email = currentUser == null ? null : currentUser.email();
    if (email == null || email.isBlank()) {
      return null;
    }
    Usuario actor = Usuario.find("email", email.trim().toLowerCase()).firstResult();
    return actor == null ? null : actor.getId();
  }

  /**
   * Resuelve los usuarios y descarta el lote si alguno no cumple.
   *
   * <p>Se resuelve el lote entero antes de tocar nada: el rechazo es atomico.
   */
  private List<Usuario> resolverUsuarios(List<UUID> publicIds) {
    List<Usuario> usuarios = new ArrayList<>();
    for (UUID publicId : publicIds) {
      Usuario usuario = Usuario.find("publicId", publicId).firstResult();
      if (usuario == null) {
        throw new RecursoNoEncontradoException("Usuario no encontrado: " + publicId);
      }
      if (usuario.estaActivo()) {
        auditService.logInvitationRejected(actorId(), usuario.getId(), "usuario_ya_activo");
        throw InvitacionException.conflicto("El usuario ya está activo: " + usuario.getEmail());
      }
      usuarios.add(usuario);
    }
    return usuarios;
  }

  /** Encola los emails una vez que la transaccion se confirma. */
  private void encolarDespuesDelCommit(List<EmailAEnviar> aEnviar) {
    synchronizationRegistry.registerInterposedSynchronization(
        new Synchronization() {
          @Override
          public void beforeCompletion() {
            // No hace falta nada aca: el email sale despues, no antes.
          }

          @Override
          public void afterCompletion(int status) {
            if (status == Status.STATUS_COMMITTED) {
              aEnviar.forEach(emailQueue::enqueue);
            }
          }
        });
  }

  /** Link de activacion que viaja en el email. */
  private String linkDeActivacion(String token) {
    // El token viaja envuelto en base64url sin relleno: el JWT pelado puede terminar
    // en "=" y el transporte del correo lo corrompe (ver InvitationTokenService).
    return frontendUrl + "/auth/activate?token=" + tokenService.envolverParaLink(token);
  }
}
