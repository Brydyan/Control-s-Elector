package ec.twintailcs.electoral.electoral.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Partido politico o movimiento electoral. Mismo schema que la entidad del backend Spring.
 *
 * <p>Los nombres de columna van EXPLICITOS: Quarkus no aplica la conversion CamelCaseToUnderscores
 * que aplica Spring, asi que declararlos es lo que hace que las dos implementaciones mapeen el
 * mismo schema.
 */
@Entity
@Table(name = "partido")
public class Partido extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 150)
  String nombre;

  @Column(name = "siglas", length = 20)
  String siglas;

  @Column(name = "numero_lista", unique = true)
  Integer numeroLista;

  public Partido() {}

  public Partido(String nombre, String siglas, Integer numeroLista) {
    this.nombre = nombre;
    this.siglas = siglas;
    this.numeroLista = numeroLista;
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

  public String getSiglas() {
    return siglas;
  }

  public void setSiglas(String siglas) {
    this.siglas = siglas;
  }

  public Integer getNumeroLista() {
    return numeroLista;
  }

  public void setNumeroLista(Integer numeroLista) {
    this.numeroLista = numeroLista;
  }
}
