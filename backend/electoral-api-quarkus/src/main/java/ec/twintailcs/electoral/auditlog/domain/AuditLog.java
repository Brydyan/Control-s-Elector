package ec.twintailcs.electoral.auditlog.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Registro de auditoria. Mismo schema que el backend Spring. */
@Entity
@Table(name = "audit_log")
public class AuditLog extends PanacheEntityBase {

  /**
   * Id por SECUENCIA y no por IDENTITY, a proposito.
   *
   * <p>Con IDENTITY, Hibernate escribe {@code insert ... returning id}. El {@code RETURNING} lo
   * evalua la politica de LECTURA de la tabla, y la de {@code audit_log} solo deja ver las filas a
   * quien tiene rol COORDINATOR/MASTER/ADMIN: el auditoria de un login fallido (que no tiene actor)
   * se filtraba y la escritura moria con "new row violates row-level security policy".
   *
   * <p>Con la secuencia, el id lo pide la base antes del INSERT y la escritura no depende de la
   * politica de lectura: poder escribir en la auditoria no implica poder leerla.
   */
  @Id
  @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "audit_log_id_seq")
  @SequenceGenerator(
      name = "audit_log_id_seq",
      sequenceName = "audit_log_id_seq",
      allocationSize = 1)
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
