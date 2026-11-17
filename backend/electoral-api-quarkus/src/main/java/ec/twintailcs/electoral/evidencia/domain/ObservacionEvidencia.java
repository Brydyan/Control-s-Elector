package ec.twintailcs.electoral.evidencia.domain;

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
 * Observacion marcada por el OBSERVER sobre su evidencia. Tabla junction propia entre la evidencia
 * y el {@link TipoObservacion}.
 *
 * <p>Diseno (port del backend Spring): tabla junction propia en vez de columna jsonb en evidencia.
 * La restriccion de «maximo 2» se aplica en el caso de uso (RF-OBS-02) y se respalda con un trigger
 * a nivel de base.
 *
 * <p>La evidencia es una FK ({@code evidencia_id}) y se mapea como columna simple: el modulo no
 * recorre la relacion, solo la consulta por id.
 */
@Entity
@Table(name = "evidencia_observacion")
public class ObservacionEvidencia extends PanacheEntityBase {

  /** Maximo de observaciones por evidencia (RF-OBS-02). Validado en el caso de uso. */
  public static final int MAX_POR_EVIDENCIA = 2;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  /** Evidencia sobre la que se marca la observacion. */
  @Column(name = "evidencia_id", nullable = false)
  Long evidenciaId;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo", nullable = false, length = 32)
  TipoObservacion tipo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  Instant creadoEn = Instant.now();

  public Long getId() {
    return id;
  }

  public Long getEvidenciaId() {
    return evidenciaId;
  }

  public void setEvidenciaId(Long evidenciaId) {
    this.evidenciaId = evidenciaId;
  }

  public TipoObservacion getTipo() {
    return tipo;
  }

  public void setTipo(TipoObservacion tipo) {
    this.tipo = tipo;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public void setCreadoEn(Instant creadoEn) {
    this.creadoEn = creadoEn;
  }
}
