package ec.twintailcs.electoral.electoral.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/**
 * Candidato inscrito a un cargo electoral. Mismo schema que la entidad del backend Spring.
 *
 * <p>El cargo y el partido son FKs obligatorias ({@code cargo_id} y {@code partido_id} son NOT NULL
 * en el schema) y se mapean como asociaciones, igual que en Spring.
 */
@Entity
@Table(name = "candidato")
public class Candidato extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 200)
  String nombre;

  @Column(name = "orden", nullable = false)
  Integer orden;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "cargo_id", nullable = false)
  Cargo cargo;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "partido_id", nullable = false)
  Partido partido;

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
