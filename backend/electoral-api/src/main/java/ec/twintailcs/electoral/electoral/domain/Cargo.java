package ec.twintailcs.electoral.electoral.domain;

import jakarta.persistence.*;

@Entity
@Table(name = "cargo")
public class Cargo {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, length = 100)
  private String nombre;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 20)
  private NivelCargo nivel;

  @Column(nullable = false)
  private Integer puestos;

  public Cargo() {}

  public Cargo(String nombre, NivelCargo nivel, Integer puestos) {
    this.nombre = nombre;
    this.nivel = nivel;
    this.puestos = puestos;
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

  public NivelCargo getNivel() {
    return nivel;
  }

  public void setNivel(NivelCargo nivel) {
    this.nivel = nivel;
  }

  public Integer getPuestos() {
    return puestos;
  }

  public void setPuestos(Integer puestos) {
    this.puestos = puestos;
  }
}
