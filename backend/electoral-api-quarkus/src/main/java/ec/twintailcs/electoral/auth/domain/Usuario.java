package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Usuario del sistema. Mismo schema que la entidad del backend Spring.
 *
 * <p>La identidad sigue siendo email + password (BCrypt). {@code rol} es una FK a {@link Rol}.
 *
 * <p>Port exacto de la entidad Spring: los setters recortan y normalizan igual que alla (el email
 * se guarda siempre en minusculas) y {@link #validarDatosObligatorios()} hace las mismas
 * comprobaciones, para que un usuario creado por cualquiera de las dos implementaciones tenga
 * exactamente los mismos valores.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @Column(
      name = "public_id",
      nullable = false,
      unique = true,
      updatable = false,
      columnDefinition = "UUID")
  UUID publicId;

  @Column(name = "email", nullable = false, length = 254, unique = true)
  String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  String passwordHash;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "rol_id", nullable = false)
  Rol rol;

  @Column(name = "habilitado", nullable = false)
  boolean habilitado = true;

  @Column(name = "creado_en", nullable = false, updatable = false)
  Instant creadoEn = Instant.now();

  @Column(name = "nombre", nullable = false, length = 100)
  String nombre;

  @Column(name = "apellido", nullable = false, length = 100)
  String apellido;

  /**
   * Estado de activacion de la cuenta.
   *
   * <p>Se persiste el nombre del enum ({@link EstadoUsuario}). El default de la columna es
   * 'ACTIVO': las cuentas preexistentes no se tocan y una cuenta nueva nace activa salvo que se
   * pida explicitamente que nazca esperando la activacion.
   */
  @Column(name = "estado_usuario", nullable = false, length = 32)
  String estadoUsuario = EstadoUsuario.ACTIVO.valor();

  /**
   * HMAC del token de activacion vigente.
   *
   * <p>NULL cuando no hay invitacion pendiente o cuando el token ya se consumio. Nunca es el token
   * en claro: la base no debe contener ningun bearer secret utilizable.
   */
  @Column(name = "activation_token_hash", length = 64)
  String activationTokenHash;

  /** Momento de la activacion de la cuenta. NULL mientras sigue pendiente. */
  @Column(name = "activated_at")
  Instant activatedAt;

  /** Momento del ultimo envio de invitacion. NULL si nunca se invito. */
  @Column(name = "invitation_sent_at")
  Instant invitationSentAt;

  /** Versión de Términos de Uso aceptada. NULL si nunca aceptó. */
  @Column(name = "accepted_terms_version", length = 10)
  String acceptedTermsVersion;

  /** Momento de la aceptación de Términos de Uso. NULL si nunca aceptó. */
  @Column(name = "accepted_terms_at")
  Instant acceptedTermsAt;

  /** Versión de Política de Privacidad aceptada. NULL si nunca aceptó. */
  @Column(name = "accepted_privacy_version", length = 10)
  String acceptedPrivacyVersion;

  /** Momento de la aceptación de Política de Privacidad. NULL si nunca aceptó. */
  @Column(name = "accepted_privacy_at")
  Instant acceptedPrivacyAt;

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Rol getRol() {
    return rol;
  }

  public boolean isHabilitado() {
    return habilitado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public String getNombre() {
    return nombre;
  }

  public String getApellido() {
    return apellido;
  }

  public String getRolNombre() {
    return rol != null ? rol.getNombre() : null;
  }

  public void setNombre(String nombre) {
    if (nombre == null || nombre.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El nombre no puede estar vacío");
    }
    this.nombre = nombre.trim();
  }

  public void setApellido(String apellido) {
    if (apellido == null || apellido.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El apellido no puede estar vacío");
    }
    this.apellido = apellido.trim();
  }

  public void setEmail(String email) {
    if (email == null || email.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El email no puede estar vacío");
    }
    this.email = email.trim().toLowerCase();
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void setRol(Rol rol) {
    this.rol = rol;
  }

  public void setHabilitado(boolean habilitado) {
    this.habilitado = habilitado;
  }

  public String getEstadoUsuario() {
    return estadoUsuario;
  }

  /**
   * Fija el estado de activacion.
   *
   * @throws IllegalArgumentException si el valor no es un estado conocido
   */
  public void setEstadoUsuario(String estadoUsuario) {
    this.estadoUsuario = EstadoUsuario.desde(estadoUsuario).valor();
  }

  /** Fija el estado desde el enum, sin pasar por la cadena. */
  public void setEstadoUsuario(EstadoUsuario estado) {
    this.estadoUsuario = estado.valor();
  }

  /** Estado de activacion tipado. Lanza si el valor persistido es desconocido. */
  public EstadoUsuario estado() {
    return EstadoUsuario.desde(estadoUsuario);
  }

  /** {@code true} si la cuenta ya puede iniciar sesion. */
  public boolean estaActivo() {
    return EstadoUsuario.ACTIVO.valor().equals(estadoUsuario);
  }

  public String getActivationTokenHash() {
    return activationTokenHash;
  }

  public void setActivationTokenHash(String activationTokenHash) {
    this.activationTokenHash = activationTokenHash;
  }

  public Instant getActivatedAt() {
    return activatedAt;
  }

  public void setActivatedAt(Instant activatedAt) {
    this.activatedAt = activatedAt;
  }

  public Instant getInvitationSentAt() {
    return invitationSentAt;
  }

  public void setInvitationSentAt(Instant invitationSentAt) {
    this.invitationSentAt = invitationSentAt;
  }

  public String getAcceptedTermsVersion() {
    return acceptedTermsVersion;
  }

  public void setAcceptedTermsVersion(String acceptedTermsVersion) {
    this.acceptedTermsVersion = acceptedTermsVersion;
  }

  public Instant getAcceptedTermsAt() {
    return acceptedTermsAt;
  }

  public void setAcceptedTermsAt(Instant acceptedTermsAt) {
    this.acceptedTermsAt = acceptedTermsAt;
  }

  public String getAcceptedPrivacyVersion() {
    return acceptedPrivacyVersion;
  }

  public void setAcceptedPrivacyVersion(String acceptedPrivacyVersion) {
    this.acceptedPrivacyVersion = acceptedPrivacyVersion;
  }

  public Instant getAcceptedPrivacyAt() {
    return acceptedPrivacyAt;
  }

  public void setAcceptedPrivacyAt(Instant acceptedPrivacyAt) {
    this.acceptedPrivacyAt = acceptedPrivacyAt;
  }

  /** Invariantes obligatorias del usuario. Mismas comprobaciones que el backend Spring. */
  public void validarDatosObligatorios() {
    if (this.email == null || this.email.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El email es obligatorio");
    }
    if (this.nombre == null || this.nombre.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El nombre es obligatorio");
    }
    if (this.apellido == null || this.apellido.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El apellido es obligatorio");
    }
    if (this.passwordHash == null || this.passwordHash.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El hash del password es obligatorio");
    }
    if (this.rol == null) {
      throw new UsuarioInvalidoException("El rol es obligatorio");
    }
  }
}
