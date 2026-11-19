package ec.twintailcs.electoral.geografia.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;

/**
 * Recinto: edificio/unidad física que agrupa múltiples JRVs bajo un COORDINATOR responsable.
 *
 * <p>Un Recinto pertenece a una Parroquia y es gestionado por un Coordinador que valida/aprueba
 * actas enviadas por Observers de las JRVs del Recinto.
 */
@Entity
@Table(name = "recinto")
public class Recinto extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", nullable = false, length = 255)
  @NotBlank(message = "nombre is required")
  @Size(min = 1, max = 255, message = "nombre must be between 1 and 255 characters")
  String nombre;

  @Column(name = "parroquia_id", nullable = false)
  @NotNull(message = "parroquiaId is required")
  Long parroquiaId;

  @Column(name = "coordinator_id")
  Long coordinatorId;

  @Column(name = "estado", nullable = false, length = 20)
  @NotBlank(message = "estado is required")
  String estado = "ACTIVO";

  @Column(name = "fecha_creacion", nullable = false)
  Instant fechaCreacion;

  @Column(name = "fecha_actualizacion", nullable = false)
  Instant fechaActualizacion;

  public Recinto() {
    this.fechaCreacion = Instant.now();
    this.fechaActualizacion = Instant.now();
  }

  public Recinto(String nombre, Long parroquiaId, Long coordinatorId, String estado) {
    this.nombre = nombre;
    this.parroquiaId = parroquiaId;
    this.coordinatorId = coordinatorId;
    this.estado = estado != null ? estado : "ACTIVO";
    this.fechaCreacion = Instant.now();
    this.fechaActualizacion = Instant.now();
  }

  // --- Getters & Setters ---

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public void setNombre(String nombre) {
    this.nombre = nombre;
    this.fechaActualizacion = Instant.now();
  }

  public Long getParroquiaId() {
    return parroquiaId;
  }

  public void setParroquiaId(Long parroquiaId) {
    this.parroquiaId = parroquiaId;
    this.fechaActualizacion = Instant.now();
  }

  public Long getCoordinatorId() {
    return coordinatorId;
  }

  public void setCoordinatorId(Long coordinatorId) {
    this.coordinatorId = coordinatorId;
    this.fechaActualizacion = Instant.now();
  }

  public String getEstado() {
    return estado;
  }

  public void setEstado(String estado) {
    this.estado = estado;
    this.fechaActualizacion = Instant.now();
  }

  public Instant getFechaCreacion() {
    return fechaCreacion;
  }

  public Instant getFechaActualizacion() {
    return fechaActualizacion;
  }
}
