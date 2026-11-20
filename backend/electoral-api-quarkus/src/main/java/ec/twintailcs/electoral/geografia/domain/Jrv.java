package ec.twintailcs.electoral.geografia.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Junta de Recepcion de Votos. Ultimo nivel de la jerarquia geografica.
 *
 * <p>El numero de JRV es unico en el sistema (lo valida la capa de aplicacion) y la parroquia se
 * mapea como la columna {@code parroquia_id}.
 */
@Entity
@Table(name = "jrv")
public class Jrv extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "numero", nullable = false, length = 20)
  String numero;

  @Column(name = "recinto", nullable = false, length = 200)
  String recinto;

  @Column(name = "total_electores", nullable = false)
  int totalElectores;

  @Column(name = "parroquia_id", nullable = false)
  Long parroquiaId;

  @Column(name = "recinto_id")
  Long recintoId;

  public Jrv() {}

  public Jrv(String numero, String recinto, int totalElectores, Long parroquiaId) {
    this.numero = numero;
    this.recinto = recinto;
    this.totalElectores = totalElectores;
    this.parroquiaId = parroquiaId;
  }

  public Long getId() {
    return id;
  }

  public String getNumero() {
    return numero;
  }

  public String getRecinto() {
    return recinto;
  }

  public int getTotalElectores() {
    return totalElectores;
  }

  public Long getParroquiaId() {
    return parroquiaId;
  }

  public void setNumero(String numero) {
    this.numero = numero;
  }

  public void setRecinto(String recinto) {
    this.recinto = recinto;
  }

  public void setTotalElectores(int totalElectores) {
    this.totalElectores = totalElectores;
  }

  public Long getRecintoId() {
    return recintoId;
  }

  public void setRecintoId(Long recintoId) {
    this.recintoId = recintoId;
  }
}
