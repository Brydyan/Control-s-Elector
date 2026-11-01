package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Sesion activa de un usuario. Guarda el hash del refresh token, nunca el token plano.
 *
 * <p>Port exacto de la entidad Spring: mismos campos, mismo esquema y la misma definicion de
 * "sesion activa" ({@link #activa()}).
 */
@Entity
@Table(name = "sesion")
public class Sesion extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "usuario_id", nullable = false)
  Long usuarioId;

  @Column(name = "sesion_id", nullable = false, unique = true, length = 36)
  String sesionId;

  @Column(name = "refresh_hash", nullable = false, length = 100, unique = true)
  String refreshHash;

  @Column(name = "revocado", nullable = false)
  boolean revocado = false;

  @Column(name = "ip_cliente", length = 64)
  String ipCliente;

  @Column(name = "user_agent", length = 256)
  String userAgent;

  @Column(name = "creado_en", nullable = false, updatable = false)
  Instant creadoEn = Instant.now();

  @Column(name = "ultimo_uso_en", nullable = false)
  Instant ultimoUsoEn = Instant.now();

  @Column(name = "expira_en", nullable = false)
  Instant expiraEn;

  public Long getId() {
    return id;
  }

  public Long getUsuarioId() {
    return usuarioId;
  }

  public String getSesionId() {
    return sesionId;
  }

  public String getRefreshHash() {
    return refreshHash;
  }

  public boolean isRevocado() {
    return revocado;
  }

  public String getIpCliente() {
    return ipCliente;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public Instant getUltimoUsoEn() {
    return ultimoUsoEn;
  }

  public Instant getExpiraEn() {
    return expiraEn;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public void setSesionId(String sesionId) {
    this.sesionId = sesionId;
  }

  public void setRefreshHash(String refreshHash) {
    this.refreshHash = refreshHash;
  }

  public void setRevocado(boolean revocado) {
    this.revocado = revocado;
  }

  public void setIpCliente(String ipCliente) {
    this.ipCliente = ipCliente;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public void setCreadoEn(Instant creadoEn) {
    this.creadoEn = creadoEn;
  }

  public void setUltimoUsoEn(Instant ultimoUsoEn) {
    this.ultimoUsoEn = ultimoUsoEn;
  }

  public void setExpiraEn(Instant expiraEn) {
    this.expiraEn = expiraEn;
  }

  /** Invariantes obligatorias de la sesion. Mismas comprobaciones que el backend Spring. */
  public void validarDatosObligatorios() {
    if (this.usuarioId == null) {
      throw new IllegalArgumentException("El ID de usuario es obligatorio para la sesión");
    }
    if (this.sesionId == null || this.sesionId.trim().isEmpty()) {
      throw new IllegalArgumentException("El sesionId es obligatorio");
    }
    if (this.refreshHash == null || this.refreshHash.trim().isEmpty()) {
      throw new IllegalArgumentException("El refreshHash es obligatorio");
    }
    if (this.expiraEn == null) {
      throw new IllegalArgumentException("La fecha de expiración es obligatoria");
    }
  }

  /**
   * Una sesion esta activa si no fue revocada y su refresh token todavia no expiro.
   *
   * <p>Es la misma definicion que usa el backend Spring: es lo que decide si un refresh token puede
   * canjearse por un par nuevo.
   */
  public boolean activa() {
    return !revocado && expiraEn != null && expiraEn.isAfter(Instant.now());
  }
}
