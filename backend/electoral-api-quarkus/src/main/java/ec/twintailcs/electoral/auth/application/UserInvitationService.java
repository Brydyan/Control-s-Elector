package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.application.port.InvitationEmailQueue;
import ec.twintailcs.electoral.auth.domain.EstadoInvitacion;
import ec.twintailcs.electoral.auth.domain.EstadoUsuario;
import ec.twintailcs.electoral.auth.domain.InvitacionException;
import ec.twintailcs.electoral.auth.domain.RecursoNoEncontradoException;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.UserInvitation;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.InviteUserRequest;
import ec.twintailcs.electoral.auth.web.dto.ResendInvitationResponse;
import ec.twintailcs.electoral.auth.web.dto.UserInvitationResponse;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Status;
import jakarta.transaction.Synchronization;
import jakarta.transaction.TransactionSynchronizationRegistry;
import jakarta.transaction.Transactional;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Invitacion de una persona: crea su cuenta y le manda el link de activacion.
 *
 * <p>Es la version de una sola persona del flujo de invitacion. La de lote ({@code
 * InvitacionService}) reenvia invitaciones a cuentas que ya existen; esta crea la cuenta desde
 * cero, con un correo de contacto propio, y deja registrada de quien fue la invitacion.
 *
 * <p><b>Lo que el usuario recibe es un link, no una contrasena.</b> La cuenta nace en estado {@code
 * PENDIENTE_INVITACION}: el login esta bloqueado hasta que la persona abra el link y elija su
 * propia contrasena. Es lo que ya hace la activacion del sistema, y es mejor que mandar una
 * contrasena temporal por email: una contrasena que viaja por correo queda en el buzon, en los
 * servidores de mail y en la pantalla de quien invite; una que la persona elige no.
 *
 * <p>El email sale al <b>correo de contacto</b> de la persona, no a su login: el login se genera
 * solo y casi siempre es una direccion institucional a la que la persona invitada todavia no tiene
 * acceso. Por eso el email lleva tambien el login: sin el, la persona no sabria con que credencial
 * entrar.
 */
@ApplicationScoped
public class UserInvitationService {

  /**
   * Comprobacion basica de RFC 5322.
   *
   * <p>El mismo criterio que usa el validador de bean del endpoint, repetido aqui para que la regla
   * no dependa de donde se llame al caso de uso.
   */
  private static final Pattern EMAIL_BASICO =
      Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");

  /** Caracteres del hash provisional de la cuenta, que nadie llega a conocer. */
  private static final String CARACTERES_PROVISIONALES =
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";

  /** Largo del hash provisional. */
  private static final int LARGO_PROVISIONAL = 32;

  private final PasswordHasher hasher;
  private final EmailGenerator emailGenerator;
  private final InvitationTokenService tokenService;
  private final InvitationAuditService auditService;
  private final InvitationEmailQueue emailQueue;
  private final TransactionSynchronizationRegistry synchronizationRegistry;
  private final CurrentUser currentUser;
  private final RlsContexto rls;
  private final String frontendUrl;
  private final SecureRandom random = new SecureRandom();

  public UserInvitationService(
      PasswordHasher hasher,
      EmailGenerator emailGenerator,
      InvitationTokenService tokenService,
      InvitationAuditService auditService,
      InvitationEmailQueue emailQueue,
      TransactionSynchronizationRegistry synchronizationRegistry,
      CurrentUser currentUser,
      RlsContexto rls,
      @ConfigProperty(name = "app.public.frontend-url") String frontendUrl) {
    this.hasher = hasher;
    this.emailGenerator = emailGenerator;
    this.tokenService = tokenService;
    this.auditService = auditService;
    this.emailQueue = emailQueue;
    this.synchronizationRegistry = synchronizationRegistry;
    this.currentUser = currentUser;
    this.rls = rls;
    this.frontendUrl = frontendUrl;
  }

  /**
   * Invita a una persona: crea su cuenta y encola el email de activacion.
   *
   * @param pedido datos de la persona
   * @param ip direccion del cliente
   * @param userAgent agente de usuario del cliente
   * @return los datos de la invitacion, sin ninguna credencial
   * @throws InvitacionException 400 si el email o el rol no valen
   */
  @Transactional
  public UserInvitationResponse invitar(InviteUserRequest pedido, String ip, String userAgent) {
    String emailPersonal = normalizarEmail(pedido.emailPersonal());
    if (!EMAIL_BASICO.matcher(emailPersonal).matches()) {
      throw InvitacionException.peticionInvalida("Email inválido: " + emailPersonal);
    }

    if (Usuario.count("emailPersonal", emailPersonal) > 0) {
      throw InvitacionException.conflicto("Ya existe usuario con email personal: " + emailPersonal);
    }

    Rol rol = Rol.find("nombre", pedido.rolNombre()).firstResult();
    if (rol == null) {
      throw InvitacionException.peticionInvalida("Rol inválido: " + pedido.rolNombre());
    }

    Long actorId = actorId();
    if (actorId == null) {
      throw new InvitacionException(401, "No autenticado");
    }

    // El contexto de RLS es el del MASTER que invita: la politica de escritura de
    // `user_invitations` exige que `invited_by` sea el usuario del contexto.
    rls.activar(actorId);

    // El login se genera solo a partir de nombre, apellido y rol. Si ya existe, el
    // generador agrega un sufijo numerico: dos Juan Garcia observadores no pueden
    // compartir login.
    String username = emailGenerator.generar(pedido.nombre(), pedido.apellido(), rol.getNombre());

    Usuario usuario = new Usuario();
    usuario.setNombre(pedido.nombre().trim());
    usuario.setApellido(pedido.apellido().trim());
    usuario.setEmail(username);
    usuario.setEmailPersonal(emailPersonal);
    // Hash provisional de una cadena que no se le muestra a nadie: la contrasena
    // real la elige la persona al activar la cuenta. Lo que importa es que la
    // columna NOT NULL tenga un bcrypt valido.
    usuario.setPasswordHash(hasher.hash(hashProvisional()));
    usuario.setRol(rol);
    usuario.setHabilitado(true);
    usuario.setEstadoUsuario(EstadoUsuario.PENDIENTE_INVITACION);
    usuario.validarDatosObligatorios();
    persistirUsuario(usuario, emailPersonal);

    Instant ahora = Instant.now();

    // ODD #504 (A): el toggle decide si la invitacion sale por email. Con el
    // toggle apagado la cuenta queda creada y pendiente: sin token y sin
    // correo, lista para que el MASTER le envie la invitacion cuando quiera.
    if (!pedido.enviarEmail()) {
      auditService.logUserCreated(actorId, usuario.getId(), emailPersonal, false);
      return new UserInvitationResponse(
          usuario.getPublicId(),
          username,
          emailPersonal,
          EstadoInvitacion.CREATED.valor(),
          null,
          true,
          "Usuario creado. Puedes enviar invitación después.");
    }

    String token = tokenService.generateToken(usuario.getPublicId(), username, rol.getNombre());
    Instant expira = tokenService.expiraEn();
    usuario.setActivationTokenHash(tokenService.hashOf(token));
    usuario.setInvitationSentAt(ahora);
    usuario.persist();

    UserInvitation invitacion = new UserInvitation();
    invitacion.setUsuarioId(usuario.getId());
    invitacion.setEmailPersonal(emailPersonal);
    invitacion.setInvitedAt(ahora);
    invitacion.setInvitedBy(actorId);
    invitacion.setStatus(UserInvitation.STATUS_INVITED);
    invitacion.setIpAddress(ip);
    invitacion.setUserAgent(userAgent);
    // La vigencia de la invitacion es la misma que la del token: 24 horas
    // (ODD #504, B). Una invitacion que vive 30 dias es una credencial que
    // vive 30 dias en un buzon de correo.
    invitacion.setExpiryAt(ahora.plus(Duration.ofHours(UserInvitation.HORAS_VIGENCIA)));
    invitacion.persist();

    auditService.logUserCreated(actorId, usuario.getId(), emailPersonal, true);

    EmailAEnviar email =
        new EmailAEnviar(
            usuario.getId(),
            emailPersonal,
            username,
            usuario.getNombre(),
            usuario.getApellido(),
            rol.getNombre(),
            linkDeActivacion(token),
            expira.toString(),
            1,
            EmailAEnviar.TipoEmail.INVITACION);
    encolarDespuesDelCommit(email);

    return new UserInvitationResponse(
        usuario.getPublicId(),
        username,
        emailPersonal,
        EstadoInvitacion.INVITED.valor(),
        invitacion.getExpiryAt(),
        true,
        "Invitación enviada a " + emailPersonal);
  }

  /**
   * Reenvia la invitacion de una cuenta que todavia no se activo.
   *
   * <p>Es el caso de soporte: la persona no recibio el correo, se le cambio la direccion o la
   * cuenta quedo a la espera. El reenvio genera un token NUEVO y deja el anterior sin efecto: como
   * el hash guardado es el del token vigente, el link viejo deja de servir en el momento en que se
   * guarda el nuevo.
   *
   * <p>Solo se reenvia a una cuenta que este esperando: una cuenta ya activada no se re-invita (el
   * link nuevo no haria falta y le daria a quien lo recibiera el control de una cuenta que ya
   * existe), y una cuenta deshabilitada tampoco.
   *
   * @param publicId id publico del usuario a re-invitar
   * @param ip direccion del cliente
   * @param userAgent agente de usuario del cliente
   * @return los datos de la nueva invitacion
   * @throws InvitacionException 400 si la cuenta no admite invitacion; 404 si el usuario no existe
   */
  @Transactional
  public ResendInvitationResponse reenviar(UUID publicId, String ip, String userAgent) {
    Usuario usuario = Usuario.find("publicId", publicId).firstResult();
    if (usuario == null) {
      throw new RecursoNoEncontradoException("Usuario no encontrado: " + publicId);
    }

    if (usuario.estaActivo()) {
      auditService.logInvitationRejected(actorId(), usuario.getId(), "usuario_ya_activo");
      throw InvitacionException.peticionInvalida(
          "La cuenta ya está activa: no se puede reenviar la invitación.");
    }
    if (!usuario.isHabilitado()) {
      auditService.logInvitationRejected(actorId(), usuario.getId(), "usuario_deshabilitado");
      throw InvitacionException.peticionInvalida(
          "La cuenta está deshabilitada: habilítala antes de reenviar la invitación.");
    }

    Long actorId = actorId();
    if (actorId == null) {
      throw new InvitacionException(401, "No autenticado");
    }
    rls.activar(actorId);

    // El correo de contacto es el destino de la invitacion. Si la cuenta se creo
    // sin correo personal (ODD #504, A), se cae al login: es la unica direccion
    // que hay.
    String emailPersonal =
        usuario.getEmailPersonal() == null || usuario.getEmailPersonal().isBlank()
            ? usuario.getEmail()
            : usuario.getEmailPersonal();

    Instant ahora = Instant.now();
    String token =
        tokenService.generateToken(
            usuario.getPublicId(), usuario.getEmail(), usuario.getRolNombre());
    Instant expira = tokenService.expiraEn();

    // El token nuevo deja sin efecto al anterior: el hash guardado es el del
    // token vigente y el link viejo deja de coincidir.
    usuario.setActivationTokenHash(tokenService.hashOf(token));
    usuario.setInvitationSentAt(ahora);
    usuario.persist();

    UserInvitation invitacion = ultimaInvitacion(usuario.getId());
    if (invitacion == null) {
      // La cuenta se creo sin email (ODD #504, A): todavia no hay invitacion que
      // reenviar, se crea la primera.
      invitacion = new UserInvitation();
      invitacion.setUsuarioId(usuario.getId());
      invitacion.setEmailPersonal(emailPersonal);
      invitacion.setInvitedBy(actorId);
      invitacion.setStatus(UserInvitation.STATUS_INVITED);
    }
    invitacion.setInvitedAt(ahora);
    invitacion.setIpAddress(ip);
    invitacion.setUserAgent(userAgent);
    invitacion.setExpiryAt(ahora.plus(Duration.ofHours(UserInvitation.HORAS_VIGENCIA)));
    if (UserInvitation.STATUS_ACTIVATED.equals(invitacion.getStatus())) {
      invitacion.setStatus(UserInvitation.STATUS_INVITED);
    }
    invitacion.persist();

    auditService.logInvitationResent(actorId, usuario.getId(), emailPersonal, expira.toString());

    EmailAEnviar email =
        new EmailAEnviar(
            usuario.getId(),
            emailPersonal,
            usuario.getEmail(),
            usuario.getNombre(),
            usuario.getApellido(),
            usuario.getRolNombre(),
            linkDeActivacion(token),
            expira.toString(),
            2,
            EmailAEnviar.TipoEmail.REENVIO);
    encolarDespuesDelCommit(email);

    return new ResendInvitationResponse(
        true,
        "Invitación re-enviada a " + emailPersonal,
        ahora,
        invitacion.getExpiryAt(),
        emailPersonal);
  }

  /** Ultima invitacion registrada de un usuario (la vigente). */
  private UserInvitation ultimaInvitacion(Long usuarioId) {
    return UserInvitation.find("usuarioId = ?1 order by id desc", usuarioId).firstResult();
  }

  /**
   * Marca como activada la invitacion vigente de un usuario.
   *
   * <p>La llama el caso de uso de activacion, en la misma transaccion que activa la cuenta: si la
   * cuenta queda activa, la invitacion que la trajo tiene que quedar registrada como tal.
   *
   * @param usuarioId id interno del usuario que se activo
   */
  public void marcarInvitacionActivada(Long usuarioId) {
    if (usuarioId == null) {
      return;
    }
    // El contexto de RLS es el de la persona que se esta activando: la activacion
    // es publica (no hay sesion todavia), pero la fila que se actualiza es la
    // suya, y la politica de UPDATE exige que `usuario_id` sea el del contexto.
    rls.activar(usuarioId);

    UserInvitation invitacion = UserInvitation.find("usuarioId", usuarioId).firstResult();
    if (invitacion == null) {
      return;
    }
    invitacion.marcarActivada(Instant.now());
    invitacion.persist();
  }

  private void persistirUsuario(Usuario usuario, String emailPersonal) {
    try {
      usuario.persist();
    } catch (jakarta.persistence.PersistenceException e) {
      if (esViolacionUnicidadEmailPersonal(e)) {
        throw InvitacionException.conflicto(
            "Ya existe usuario con email personal: " + emailPersonal);
      }
      throw e;
    }
  }

  private boolean esViolacionUnicidadEmailPersonal(Throwable error) {
    Throwable actual = error;
    while (actual != null) {
      if (actual instanceof org.hibernate.exception.ConstraintViolationException cve) {
        String constraint = cve.getConstraintName();
        String sqlState = cve.getSQLState();
        if ("23505".equals(sqlState)
            || "uq_usuario_email_personal".equals(constraint)
            || "idx_usuario_email_personal".equals(constraint)) {
          return true;
        }
      }
      if (actual instanceof java.sql.SQLException sqle && "23505".equals(sqle.getSQLState())) {
        String mensaje = String.valueOf(sqle.getMessage());
        if (mensaje.contains("uq_usuario_email_personal")
            || mensaje.contains("idx_usuario_email_personal")) {
          return true;
        }
      }
      actual = actual.getCause();
    }
    return false;
  }

  /** Id interno del MASTER que esta ejecutando la operacion. */
  private Long actorId() {
    String email = currentUser == null ? null : currentUser.email();
    if (email == null || email.isBlank()) {
      return null;
    }
    Usuario actor = Usuario.find("email", email.trim().toLowerCase()).firstResult();
    return actor == null ? null : actor.getId();
  }

  /** Normaliza el correo de contacto: minusculas y sin espacios. */
  private static String normalizarEmail(String email) {
    return email == null ? "" : email.trim().toLowerCase();
  }

  /** Cadena aleatoria que se hashea como contrasena provisional. */
  private String hashProvisional() {
    StringBuilder sb = new StringBuilder(LARGO_PROVISIONAL);
    for (int i = 0; i < LARGO_PROVISIONAL; i++) {
      sb.append(CARACTERES_PROVISIONALES.charAt(random.nextInt(CARACTERES_PROVISIONALES.length())));
    }
    return sb.toString();
  }

  /** Link de activacion que viaja en el email. */
  private String linkDeActivacion(String token) {
    return frontendUrl + "/auth/activate?token=" + tokenService.envolverParaLink(token);
  }

  /**
   * Encola el email una vez confirmada la transaccion.
   *
   * <p>Si el email saliera durante la transaccion y esta fallara despues, la persona recibiria un
   * link con un token que nunca llego a la base. Encolar despues del commit elimina esa ventana.
   */
  private void encolarDespuesDelCommit(EmailAEnviar email) {
    synchronizationRegistry.registerInterposedSynchronization(
        new Synchronization() {
          @Override
          public void beforeCompletion() {
            // Nada: el email sale despues, no antes.
          }

          @Override
          public void afterCompletion(int status) {
            if (status == Status.STATUS_COMMITTED) {
              emailQueue.enqueue(email);
            }
          }
        });
  }
}
