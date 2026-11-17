package ec.twintailcs.electoral.geografia.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Provincia electoral. Primer nivel de la jerarquia geografica.
 *
 * <p>Se mapea de forma explicita columna por columna: {@code codigo} es unico (indice {@code
 * ix_provincia_codigo}) y puede ser null, en cuyo caso la provincia no tiene codigo.
 */
@Entity
@Table(name = "provincia")
public class Provincia extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 100)
  String nombre;

  @Column(name = "codigo", unique = true, length = 10)
  String codigo;

  public Provincia() {}

  public Provincia(String nombre, String codigo) {
    this.nombre = nombre;
    this.codigo = codigo;
  }

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getCodigo() {
    return codigo;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public void setCodigo(String codigo) {
    this.codigo = codigo;
  }
}
