package ec.twintailcs.electoral.geografia.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Parroquia electoral. Tercer nivel de la jerarquia geografica.
 *
 * <p>El canton se mapea como la columna {@code canton_id} y no como entidad: la jerarquia se arma
 * en el arbol y no hace falta navegar la relacion.
 */
@Entity
@Table(name = "parroquia")
public class Parroquia extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 100)
  String nombre;

  @Column(name = "es_urbana", nullable = false)
  boolean esUrbana;

  @Column(name = "canton_id", nullable = false)
  Long cantonId;

  public Parroquia() {}

  public Parroquia(String nombre, boolean esUrbana, Long cantonId) {
    this.nombre = nombre;
    this.esUrbana = esUrbana;
    this.cantonId = cantonId;
  }

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public boolean isEsUrbana() {
    return esUrbana;
  }

  public Long getCantonId() {
    return cantonId;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public void setEsUrbana(boolean esUrbana) {
    this.esUrbana = esUrbana;
  }
}
