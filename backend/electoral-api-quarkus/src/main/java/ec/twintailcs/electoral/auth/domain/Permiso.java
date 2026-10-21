package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.util.UUID;

/** Permiso atomico {@code recurso:accion}. */
@Entity
@Table(
    name = "permiso",
    uniqueConstraints = @UniqueConstraint(
        name = "uq_permiso_recurso_accion",
        columnNames = {"recurso", "accion"}))
public class Permiso extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "UUID")
  UUID publicId;

  @Column(name = "recurso", nullable = false, length = 64)
  String recurso;

  @Column(name = "accion", nullable = false, length = 64)
  String accion;

  @Column(name = "descripcion", length = 256)
  String descripcion;

  public Long getId() {
    return id;
  }

  public String getRecurso() {
    return recurso;
  }

  public String getAccion() {
    return accion;
  }
}
