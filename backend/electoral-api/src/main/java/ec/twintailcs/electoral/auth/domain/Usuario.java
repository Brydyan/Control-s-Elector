package ec.twintailcs.electoral.auth.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UuidGenerator;

/**
 * Usuario del sistema. La identidad sigue siendo email + password (BCrypt).
 *
 * <p>{@code rol} es ahora una FK a la entidad {@link Rol}.
 */
@Entity
@Table(
    name = "usuario",
    indexes = {@Index(name = "ix_usuario_email", columnList = "email", unique = true)})
@Getter
@Setter
@NoArgsConstructor
public class Usuario {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, updatable = false)
  private UUID publicId;

  @Column(nullable = false, length = 254, unique = true)
  private String email;

  /** BCrypt hash. Nunca plain text. */
  @com.fasterxml.jackson.annotation.JsonIgnore
  @Column(nullable = false, length = 100)
  private String passwordHash;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "rol_id", nullable = false)
  private Rol rol;

  @Column(nullable = false)
  private boolean habilitado = true;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn = Instant.now();

  @Column(nullable = false, length = 100)
  private String nombre;

  @Column(nullable = false, length = 100)
  private String apellido;

  /** Devuelve el nombre del rol para uso en DTOs y JWT. */
  public String getRolNombre() {
    return rol != null ? rol.getNombre() : null;
  }

  public void setEmail(String email) {
    if (email == null || email.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El email no puede estar vacío");
    }
    this.email = email.trim().toLowerCase();
  }

  public void setNombre(String nombre) {
    if (nombre == null || nombre.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El nombre no puede estar vacío");
    }
    this.nombre = nombre.trim();
  }

  public void setApellido(String apellido) {
    if (apellido == null || apellido.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El apellido no puede estar vacío");
    }
    this.apellido = apellido.trim();
  }

  public void validarDatosObligatorios() {
    if (this.email == null || this.email.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El email es obligatorio");
    }
    if (this.nombre == null || this.nombre.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El nombre es obligatorio");
    }
    if (this.apellido == null || this.apellido.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El apellido es obligatorio");
    }
    if (this.passwordHash == null || this.passwordHash.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El hash del password es obligatorio");
    }
    if (this.rol == null) {
      throw new UsuarioInvalidoException("El rol es obligatorio");
    }
  }
}
