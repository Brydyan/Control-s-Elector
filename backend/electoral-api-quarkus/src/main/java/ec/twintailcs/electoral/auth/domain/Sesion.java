package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/** Sesion activa de un usuario. Guarda el hash del refresh token, nunca el token plano. */
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
}
