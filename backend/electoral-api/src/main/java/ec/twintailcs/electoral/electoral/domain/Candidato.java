package ec.twintailcs.electoral.electoral.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "candidato")
public class Candidato {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 200)
  private String nombre;

  @Column(nullable = false)
  private Integer orden;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cargo_id", nullable = false)
  private Cargo cargo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "partido_id", nullable = false)
  private Partido partido;

  public Candidato() {}

  public Candidato(String nombre, Integer orden, Cargo cargo, Partido partido) {
    this.nombre = nombre;
    this.orden = orden;
    this.cargo = cargo;
    this.partido = partido;
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public Integer getOrden() {
    return orden;
  }

  public void setOrden(Integer orden) {
    this.orden = orden;
  }

  public Cargo getCargo() {
    return cargo;
  }

  public void setCargo(Cargo cargo) {
    this.cargo = cargo;
  }

  public Partido getPartido() {
    return partido;
  }

  public void setPartido(Partido partido) {
    this.partido = partido;
  }
}
