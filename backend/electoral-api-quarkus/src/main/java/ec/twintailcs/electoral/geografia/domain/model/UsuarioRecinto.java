package ec.twintailcs.electoral.geografia.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * Assignment of a user (COORDINATOR or OBSERVER) to a Recinto.
 *
 * <p>Represents the N-N relationship between usuarios and recintos with role context. A user can be
 * COORDINATOR or OBSERVER in a Recinto.
 *
 * <p>RLS: Electoral_app role can only INSERT/SELECT/UPDATE/DELETE rows where: - INSERT: blocked
 * (only MASTER can assign users to recintos) - SELECT: filtered by RLS policy (coordinators see
 * their own recinto assignments) - UPDATE/DELETE: coordin-only on themselves
 */
@Entity
@Table(
    name = "usuario_recinto",
    uniqueConstraints = {
      @UniqueConstraint(
          columnNames = {"usuario_id", "recinto_id"},
          name = "uk_usuario_recinto_unique")
    })
public class UsuarioRecinto extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "id")
  public Long id;

  @Column(name = "usuario_id", nullable = false)
  public Long usuarioId;

  @Column(name = "recinto_id", nullable = false)
  public Long recintoId;

  @Column(name = "rol_en_recinto", nullable = false, length = 20)
  @Enumerated(EnumType.STRING)
  public RolEnRecinto rolEnRecinto;

  @Column(name = "fecha_asignacion", nullable = false, columnDefinition = "TIMESTAMP")
  public LocalDateTime fechaAsignacion = LocalDateTime.now();

  @Column(name = "fecha_actualizacion", nullable = false, columnDefinition = "TIMESTAMP")
  public LocalDateTime fechaActualizacion = LocalDateTime.now();

  /** Enum: role of the user in the recinto context */
  public enum RolEnRecinto {
    COORDINATOR("COORDINATOR"),
    OBSERVER("OBSERVER");

    private final String value;

    RolEnRecinto(String value) {
      this.value = value;
    }

    public String getValue() {
      return value;
    }

    public static RolEnRecinto fromString(String value) {
      for (RolEnRecinto rol : RolEnRecinto.values()) {
        if (rol.value.equals(value)) {
          return rol;
        }
      }
      throw new IllegalArgumentException("Unknown rol: " + value);
    }
  }

  // Convenience constructors
  public UsuarioRecinto() {}

  public UsuarioRecinto(Long usuarioId, Long recintoId, RolEnRecinto rolEnRecinto) {
    this.usuarioId = usuarioId;
    this.recintoId = recintoId;
    this.rolEnRecinto = rolEnRecinto;
  }

  @Override
  public String toString() {
    return "UsuarioRecinto{"
        + "id="
        + id
        + ", usuarioId="
        + usuarioId
        + ", recintoId="
        + recintoId
        + ", rolEnRecinto="
        + rolEnRecinto
        + ", fechaAsignacion="
        + fechaAsignacion
        + '}';
  }
}
