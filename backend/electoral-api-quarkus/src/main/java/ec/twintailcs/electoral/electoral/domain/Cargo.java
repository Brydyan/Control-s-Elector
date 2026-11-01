package ec.twintailcs.electoral.electoral.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Cargo electoral (Prefecto, Alcalde, Concejal, etc.). Mismo schema que la entidad del backend
 * Spring.
 *
 * <p>El nivel se guarda como texto ({@code EnumType.STRING}), igual que en Spring, para que el
 * valor de la columna sea legible y estable entre las dos implementaciones.
 */
@Entity
@Table(name = "cargo")
public class Cargo extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 100)
  String nombre;

  @Enumerated(EnumType.STRING)
  @Column(name = "nivel", nullable = false, length = 20)
  NivelCargo nivel;

  @Column(name = "puestos", nullable = false)
  Integer puestos;

  public Cargo() {}

  public Cargo(String nombre, NivelCargo nivel, Integer puestos) {
    this.nombre = nombre;
    this.nivel = nivel;
    this.puestos = puestos;
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
