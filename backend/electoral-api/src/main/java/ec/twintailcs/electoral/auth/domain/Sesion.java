package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Sesion activa de un usuario.
 *
 * <p>Sesiones NO son JWT stateless. Cada login crea una fila aqui y un par (access token, refresh
 * token). El refresh token se hashea y se guarda solo el hash (nunca el token plano).
 *
 * <p>Beneficios: - Logout invalida la sesion (revocado=true) y rompe la renovacion. - "Cerrar todas
 * mis sesiones" pone revocado=true en todas las filas del usuario. - Auditoria basica: queda
 * registro de que IPs/dispositivos accedieron.
 *
 * <p>El access token (JWT) no vive aca: valida contra la sesion por su "sesionId" embebido en el
 * claim jti.
 */
@Entity
@Table(
    name = "sesion",
    indexes = {
      @Index(name = "ix_sesion_usuario", columnList = "usuario_id"),
      @Index(name = "ix_sesion_refresh", columnList = "refresh_hash", unique = true),
      @Index(name = "ix_sesion_revocado", columnList = "revocado")
    })
public class Sesion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "usuario_id", nullable = false)
  private Long usuarioId;

  /**
   * Identificador unico embebido en el JWT (claim jti). Actua como session id publico; este campo
   * es ese mismo ID.
   */
  @Column(name = "sesion_id", nullable = false, unique = true, length = 36)
  private String sesionId;

  /** BCrypt hash del refresh token. Nunca se guarda el token plano. */
  @Column(name = "refresh_hash", nullable = false, length = 100, unique = true)
  private String refreshHash;

  @Column(nullable = false)
  private boolean revocado = false;

  @Column(name = "ip_cliente", length = 64)
  private String ipCliente;

  @Column(name = "user_agent", length = 256)
  private String userAgent;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn = Instant.now();

  @Column(name = "ultimo_uso_en", nullable = false)
  private Instant ultimoUsoEn = Instant.now();

  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public Long getUsuarioId() {
    return usuarioId;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public String getSesionId() {
    return sesionId;
  }

  public void setSesionId(String sesionId) {
    this.sesionId = sesionId;
  }

  public String getRefreshHash() {
    return refreshHash;
  }

  public void setRefreshHash(String refreshHash) {
    this.refreshHash = refreshHash;
  }

  public boolean isRevocado() {
    return revocado;
  }

  public void setRevocado(boolean revocado) {
    this.revocado = revocado;
  }

  public String getIpCliente() {
    return ipCliente;
  }

  public void setIpCliente(String ipCliente) {
    this.ipCliente = ipCliente;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public void setCreadoEn(Instant creadoEn) {
    this.creadoEn = creadoEn;
  }

  public Instant getUltimoUsoEn() {
    return ultimoUsoEn;
  }

  public void setUltimoUsoEn(Instant ultimoUsoEn) {
    this.ultimoUsoEn = ultimoUsoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public void setExpiraEn(Instant expiraEn) {
    this.expiraEn = expiraEn;
  }

  public boolean activa() {
    return !revocado && expiraEn.isAfter(Instant.now());
  }
}
