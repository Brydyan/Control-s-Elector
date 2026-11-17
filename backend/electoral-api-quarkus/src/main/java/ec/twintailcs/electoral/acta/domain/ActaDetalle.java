package ec.twintailcs.electoral.acta.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

/**
 * Linea del acta: una fila por candidato dentro del acta.
 *
 * <p>Mismo schema que la entidad del backend Spring. Reglas:
 *
 * <ul>
 *   <li>UNIQUE(acta_id, candidato_id): no se puede repetir candidato en la misma acta.
 *   <li>El {@code @Version} protege contra ediciones concurrentes durante disputa/correccion.
 *   <li>FK a acta con ON DELETE CASCADE: si se borra el acta (MASTER), se borran sus lineas.
 * </ul>
 *
 * <p>La FK al candidato se mapea como columna simple (el modulo de actas no recorre la
 * associacion): el nombre del candidato y su partido se leen con la proyeccion del expediente.
 */
@Entity
@Table(name = "acta_detalle")
public class ActaDetalle extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Version
  @Column(name = "version", nullable = false)
  Long version;

  @Column(name = "acta_id", nullable = false)
  Long actaId;

  @Column(name = "candidato_id", nullable = false)
  Long candidatoId;

  @Column(name = "votos", nullable = false)
  Integer votos;

  /**
   * Notas libres del coordinator/MASTER sobre esta linea del acta (T-BE-16). Por ejemplo: "voto
   * observado, esperar recuento".
   */
  @Column(name = "observaciones", columnDefinition = "text")
  String observaciones;

  public ActaDetalle() {}

  public ActaDetalle(Long actaId, Long candidatoId, Integer votos) {
    setVotos(votos);
    this.actaId = actaId;
    this.candidatoId = candidatoId;
  }

  public Long getId() {
    return id;
  }

  public Long getActaId() {
    return actaId;
  }

  public Long getCandidatoId() {
    return candidatoId;
  }

  public Integer getVotos() {
    return votos;
  }

  public void setVotos(Integer votos) {
    if (votos == null) {
      throw new ActaInvalidaException("Los votos del candidato son obligatorios");
    }
    if (votos < 0) {
      throw new ActaInvalidaException("Los votos del candidato no pueden ser negativos");
    }
    this.votos = votos;
  }

  public String getObservaciones() {
    return observaciones;
  }

  public void setObservaciones(String observaciones) {
    this.observaciones = observaciones;
  }
}
