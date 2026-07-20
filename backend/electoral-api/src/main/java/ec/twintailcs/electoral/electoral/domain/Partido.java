package ec.twintailcs.electoral.electoral.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "partido")
public class Partido {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 150)
    private String nombre;

    @Column(length = 20)
    private String siglas;

    @Column(unique = true)
    private Integer numeroLista;

    public Partido() {}

    public Partido(String nombre, String siglas, Integer numeroLista) {
        this.nombre = nombre;
        this.siglas = siglas;
        this.numeroLista = numeroLista;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public String getSiglas() { return siglas; }
    public void setSiglas(String siglas) { this.siglas = siglas; }

    public Integer getNumeroLista() { return numeroLista; }
    public void setNumeroLista(Integer numeroLista) { this.numeroLista = numeroLista; }
}
