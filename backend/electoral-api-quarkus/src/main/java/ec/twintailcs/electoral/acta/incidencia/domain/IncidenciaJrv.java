package ec.twintailcs.electoral.acta.incidencia.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Incidencia operativa reportada por un veedor sobre una JRV (T-BE-19).
 *
 * <p>Aislada del acta porque representa un evento, no votos. Mismo schema que la entidad del
 * backend Spring.
 */
@Entity
@Table(name = "incidencia_jrv")
public class IncidenciaJrv extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "jrv_id", nullable = false)
  Long jrvId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo", nullable = false, length = 50)
  TipoIncidenciaJrv tipo;

  @Column(name = "descripcion", nullable = false, columnDefinition = "TEXT")
  String descripcion;

  @Column(name = "evidencia_url", length = 500)
  String evidenciaUrl;

  @Column(name = "reportada_por_id", nullable = false)
  Long reportadaPorId;

  @Column(name = "fecha_registro", nullable = false, updatable = false)
  Instant fechaRegistro = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getJrvId() {
    return jrvId;
  }

  public void setJrvId(Long jrvId) {
    this.jrvId = jrvId;
  }

  public TipoIncidenciaJrv getTipo() {
    return tipo;
  }

  public void setTipo(TipoIncidenciaJrv tipo) {
    this.tipo = tipo;
  }

  public String getDescripcion() {
    return descripcion;
  }

  public void setDescripcion(String descripcion) {
    this.descripcion = descripcion;
  }

  public String getEvidenciaUrl() {
    return evidenciaUrl;
  }

  public void setEvidenciaUrl(String evidenciaUrl) {
    this.evidenciaUrl = evidenciaUrl;
  }

  public Long getReportadaPorId() {
    return reportadaPorId;
  }

  public void setReportadaPorId(Long reportadaPorId) {
    this.reportadaPorId = reportadaPorId;
  }

  public Instant getFechaRegistro() {
    return fechaRegistro;
  }
}
