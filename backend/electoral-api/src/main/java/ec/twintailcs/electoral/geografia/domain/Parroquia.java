package ec.twintailcs.electoral.geografia.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "parroquia")
public class Parroquia {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false)
    private boolean esUrbana;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "canton_id", nullable = false)
    private Canton canton;

    public Parroquia() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }

    public boolean isEsUrbana() { return esUrbana; }
    public void setEsUrbana(boolean esUrbana) { this.esUrbana = esUrbana; }

    public Canton getCanton() { return canton; }
    public void setCanton(Canton canton) { this.canton = canton; }
}
