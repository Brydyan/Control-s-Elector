package ec.twintailcs.electoral.acta.domain;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Línea del acta: una fila por candidato dentro del acta.
 *
 * <p>Reglas:
 *
 * <ul>
 *   <li>UNIQUE(acta_id, candidato_id): no se puede repetir candidato en la misma acta.
 *   <li>{@code @Version} protege contra ediciones concurrentes durante disputa/corrección.
 *   <li>FK a Acta con ON DELETE CASCADE: si se borra el acta (MASTER), se borran sus líneas.
 * </ul>
 */
@Entity
@Table(name = "acta_detalle")
@Getter
@Setter
@NoArgsConstructor
public class ActaDetalle {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Version
  @Column(nullable = false)
  private Long version;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "acta_id", nullable = false)
  private Acta acta;

  @ManyToOne(optional = false, fetch = FetchType.LAZY)
  @JoinColumn(name = "candidato_id", nullable = false)
  private Candidato candidato;

  @Column(nullable = false)
  private Integer votos;

  /**
   * Notas libres del coordinator/MASTER sobre esta línea del acta (T-BE-16). Por ejemplo: "voto
   * observado, esperar recuento".
   */
  @Column(columnDefinition = "text")
  private String observaciones;

  public ActaDetalle(Acta acta, Candidato candidato, Integer votos) {
    if (acta == null) {
      throw new ActaInvalidaException("El acta es obligatoria para el detalle");
    }
    if (candidato == null) {
      throw new ActaInvalidaException("El candidato es obligatorio para el detalle");
    }
    setVotos(votos);
    this.acta = acta;
    this.candidato = candidato;
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
}
