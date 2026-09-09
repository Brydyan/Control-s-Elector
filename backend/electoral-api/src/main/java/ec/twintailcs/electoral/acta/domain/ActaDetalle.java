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

/**
 * Línea del acta: una fila por candidato dentro del acta.
 *
 * <p>Reglas:
 * <ul>
 *   <li>UNIQUE(acta_id, candidato_id): no se puede repetir candidato en la misma acta.</li>
 *   <li>{@code @Version} protege contra ediciones concurrentes durante
 *       disputa/corrección.</li>
 *   <li>FK a Acta con ON DELETE CASCADE: si se borra el acta (MASTER),
 *       se borran sus líneas.</li>
 * </ul>
 */
@Entity
@Table(name = "acta_detalle")
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

    public ActaDetalle() {
    }

    public ActaDetalle(Acta acta, Candidato candidato, Integer votos) {
        this.acta = acta;
        this.candidato = candidato;
        this.votos = votos;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getVersion() {
        return version;
    }

    public Acta getActa() {
        return acta;
    }

    public void setActa(Acta acta) {
        this.acta = acta;
    }

    public Candidato getCandidato() {
        return candidato;
    }

    public void setCandidato(Candidato candidato) {
        this.candidato = candidato;
    }

    public Integer getVotos() {
        return votos;
    }

    public void setVotos(Integer votos) {
        this.votos = votos;
    }
}