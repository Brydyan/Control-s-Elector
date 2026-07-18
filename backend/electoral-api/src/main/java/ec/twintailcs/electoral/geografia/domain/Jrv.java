package ec.twintailcs.electoral.geografia.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "jrv")
public class Jrv {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 20)
    private String numero;

    @Column(nullable = false, length = 200)
    private String recinto;

    @Column(nullable = false)
    private Integer totalElectores;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parroquia_id", nullable = false)
    private Parroquia parroquia;

    public Jrv() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNumero() { return numero; }
    public void setNumero(String numero) { this.numero = numero; }

    public String getRecinto() { return recinto; }
    public void setRecinto(String recinto) { this.recinto = recinto; }

    public Integer getTotalElectores() { return totalElectores; }
    public void setTotalElectores(Integer totalElectores) { this.totalElectores = totalElectores; }

    public Parroquia getParroquia() { return parroquia; }
    public void setParroquia(Parroquia parroquia) { this.parroquia = parroquia; }
}
