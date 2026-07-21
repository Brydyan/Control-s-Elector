package ec.twintailcs.electoral.acta.domain;

import jakarta.persistence.*;
import ec.twintailcs.electoral.electoral.domain.Candidato;

@Entity
@Table(name = "acta_detalle")
public class ActaDetalle {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "acta_id", nullable = false)
    private Acta acta;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "candidato_id", nullable = false)
    private Candidato candidato;

    @Column(nullable = false)
    private Integer votos;

    public ActaDetalle() {}

    public ActaDetalle(Acta acta, Candidato candidato, Integer votos) {
        this.acta = acta;
        this.candidato = candidato;
        this.votos = votos;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Acta getActa() { return acta; }
    public void setActa(Acta acta) { this.acta = acta; }

    public Candidato getCandidato() { return candidato; }
    public void setCandidato(Candidato candidato) { this.candidato = candidato; }

    public Integer getVotos() { return votos; }
    public void setVotos(Integer votos) { this.votos = votos; }
}
