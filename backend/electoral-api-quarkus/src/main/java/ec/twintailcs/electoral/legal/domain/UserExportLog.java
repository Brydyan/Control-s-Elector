package ec.twintailcs.electoral.legal.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Registro de una exportacion de datos personales (GDPR Art. 15).
 *
 * <p>La tabla cumple dos papeles a la vez:
 *
 * <ul>
 *   <li><b>Auditoria</b>: queda constancia de quien pidio sus datos y cuando. Es informacion
 *       personal que el propio usuario puede ver en su exportacion.
 *   <li><b>Rate limit</b>: la ventana de 24 horas sale de la ultima fila del usuario, no de un
 *       contador en memoria. asi el limite se respeta aunque el backend se reinicie o haya mas de
 *       una replica, que es justo cuando un limite en memoria deja de valer.
 * </ul>
 */
@Entity
@Table(name = "user_export_log")
public class UserExportLog extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "usuario_id", nullable = false)
  Long usuarioId;

  /** UUID de la exportacion: es el identificador que el usuario puede citar si reclama. */
  @Column(name = "export_id", nullable = false, unique = true, length = 36)
  String exportId;

  @Column(name = "exported_at", nullable = false)
  Instant exportedAt = Instant.now();

  @Column(name = "ip_address", length = 45)
  String ipAddress;

  /** Cantidad de registros que contiene el export. */
  @Column(name = "record_count", nullable = false)
  int recordCount;

  public Long getId() {
    return id;
  }

  public Long getUsuarioId() {
    return usuarioId;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public String getExportId() {
    return exportId;
  }

  public void setExportId(String exportId) {
    this.exportId = exportId;
  }

  public Instant getExportedAt() {
    return exportedAt;
  }

  public void setExportedAt(Instant exportedAt) {
    this.exportedAt = exportedAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public int getRecordCount() {
    return recordCount;
  }

  public void setRecordCount(int recordCount) {
    this.recordCount = recordCount;
  }
}
