package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;

/**
 * Invitacion enviada a una persona que todavia no eligio su contrasena.
 *
 * <p>Es el HISTORIAL, no el estado vigente: el estado de la invitacion que sigue abierta vive en
 * {@code usuario.activation_token_hash} y {@code usuario.estado_usuario}. Esta fila responde a las
 * otras preguntas — quien invito, a que direccion personal, desde que IP, cuando expiro— y es lo
 * que permite demostrar el origen de una cuenta.
 *
 * <p>Los estados:
 *
 * <ul>
 *   <li>{@code INVITED} — enviada, pendiente de que la persona active la cuenta.
 *   <li>{@code ACTIVATED} — la persona activo la cuenta.
 *   <li>{@code EXPIRED} — paso la ventana de 24 horas sin activarse.
 * </ul>
 *
 * <p>{@code EXPIRED} no se escribe: se DERIVA. Una invitacion que nunca se activa deja de ser
 * "vigente" por el solo paso del tiempo, y un job que marque las vencidas seria trabajo de fondo
 * para algo que se puede consultar al vuelo. {@link #estadoActual()} hace esa cuenta.
 */
@Entity
@Table(name = "user_invitations")
public class UserInvitation extends PanacheEntityBase {

  /** Invitacion enviada, pendiente de activacion. */
  public static final String STATUS_INVITED = "INVITED";

  /** La persona activo la cuenta. */
  public static final String STATUS_ACTIVATED = "ACTIVATED";

  /** La invitacion vencio sin que nadie la activara. */
  public static final String STATUS_EXPIRED = "EXPIRED";

  /**
   * Ventana de vigencia de la invitacion, en horas.
   *
   * <p>Son 24 horas, no 30 dias (ODD #504, B): es lo mismo que dura el token de activacion, y una
   * invitacion que vive 30 dias es una credencial que vive 30 dias en un buzon de correo.
   */
  public static final int HORAS_VIGENCIA = 24;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  /** Usuario invitado. */
  @Column(name = "usuario_id", nullable = false)
  Long usuarioId;

  /** Correo de contacto al que se envio la invitacion. */
  @Column(name = "email_personal", nullable = false, length = 254)
  String emailPersonal;

  /** Momento del envio. */
  @Column(name = "invited_at", nullable = false)
  Instant invitedAt = Instant.now();

  /** Quien envio la invitacion (id interno del MASTER). */
  @Column(name = "invited_by")
  Long invitedBy;

  /** Estado guardado: INVITED o ACTIVATED. EXPIRED es derivado. */
  @Column(name = "status", nullable = false, length = 20)
  String status = STATUS_INVITED;

  /** Direccion IP desde la que se hizo la operacion. */
  @Column(name = "ip_address", length = 45)
  String ipAddress;

  /** Agente de usuario del cliente. */
  @Column(name = "user_agent", length = 500)
  String userAgent;

  /** Momento de la activacion de la cuenta. */
  @Column(name = "activated_at")
  Instant activatedAt;

  /** Momento en que la invitacion deja de valer. */
  @Column(name = "expiry_at", nullable = false)
  Instant expiryAt = Instant.now().plus(Duration.ofHours(HORAS_VIGENCIA));

  public Long getId() {
    return id;
  }

  public Long getUsuarioId() {
    return usuarioId;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public String getEmailPersonal() {
    return emailPersonal;
  }

  public void setEmailPersonal(String emailPersonal) {
    this.emailPersonal = emailPersonal == null ? null : emailPersonal.trim();
  }

  public Instant getInvitedAt() {
    return invitedAt;
  }

  public void setInvitedAt(Instant invitedAt) {
    this.invitedAt = invitedAt;
  }

  public Long getInvitedBy() {
    return invitedBy;
  }

  public void setInvitedBy(Long invitedBy) {
    this.invitedBy = invitedBy;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent == null ? null : userAgent.trim();
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public void setActivatedAt(Instant activatedAt) {
    this.activatedAt = activatedAt;
  }

  public Instant getExpiryAt() {
    return expiryAt;
  }

  public void setExpiryAt(Instant expiryAt) {
    this.expiryAt = expiryAt;
  }

  /**
   * Estado de la invitacion en este momento.
   *
   * <p>Si ya se activo, es ACTIVATED. Si no y la ventana ya paso, es EXPIRED. Si no, INVITED.
   */
  public String estadoActual() {
    if (STATUS_ACTIVATED.equals(status)) {
      return STATUS_ACTIVATED;
    }
    if (expiryAt != null && Instant.now().isAfter(expiryAt)) {
      return STATUS_EXPIRED;
    }
    return STATUS_INVITED;
  }

  /** {@code true} si la invitacion ya no puede aceptarse. */
  public boolean estaExpirada() {
    return STATUS_EXPIRED.equals(estadoActual());
  }

  /** Marca la invitacion como activada. */
  public void marcarActivada(Instant momento) {
    this.status = STATUS_ACTIVATED;
    this.activatedAt = momento == null ? Instant.now() : momento;
  }
}
