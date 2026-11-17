package ec.twintailcs.electoral.geografia.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Canton de la Provincia de Santa Elena. Mismo schema que la entidad del backend Spring.
 *
 * <p>La provincia se mapea como la columna {@code provincia_id} y no como entidad: el modulo de
 * configuracion solo lee cantones y nunca recorre hacia la provincia, asi que no hace falta
 * mapearla ni exponerla.
 */
@Entity
@Table(name = "canton")
public class Canton extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 100)
  String nombre;

  @Column(name = "codigo", unique = true, length = 10)
  String codigo;

  @Column(name = "provincia_id", nullable = false)
  Long provinciaId;

  public Canton() {}

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public String getCodigo() {
    return codigo;
  }

  public Long getProvinciaId() {
    return provinciaId;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
  }

  public void setCodigo(String codigo) {
    this.codigo = codigo;
  }

  public void setProvinciaId(Long provinciaId) {
    this.provinciaId = provinciaId;
  }
}
