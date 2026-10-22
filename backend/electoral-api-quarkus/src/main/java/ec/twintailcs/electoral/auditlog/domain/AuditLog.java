package ec.twintailcs.electoral.auditlog.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import java.time.Instant;

/** Registro de auditoria. Mismo schema que el backend Spring. */
@Entity
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "entidad", nullable = false, length = 64)
  String entidad;

  @Column(name = "entidad_id", nullable = false)
  Long entidadId;

  @Column(name = "accion", nullable = false, length = 32)
  String accion;

  // La columna es JSONB: sin el mapeo de tipo de Hibernate, Postgres rechaza el INSERT
  // con "column detalle is of type jsonb but expression is of type character varying".
  @JdbcTypeCode(SqlTypes.JSON)
  @Column(name = "detalle", columnDefinition = "JSONB")
  String detalle;

  @Column(name = "ip_address", length = 64)
  String ipAddress;

  @Column(name = "registrado_por")
  Long registradoPor;

  @Column(name = "timestamp", nullable = false)
  Instant timestamp = Instant.now();

  public void setEntidad(String entidad) {
    this.entidad = entidad;
  }

  public void setEntidadId(Long entidadId) {
    this.entidadId = entidadId;
  }

  public void setAccion(String accion) {
    this.accion = accion;
  }

  public void setDetalle(String detalle) {
    this.detalle = detalle;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public void setRegistradoPor(Long registradoPor) {
    this.registradoPor = registradoPor;
  }

  public void setTimestamp(Instant timestamp) {
    this.timestamp = timestamp;
  }
}
