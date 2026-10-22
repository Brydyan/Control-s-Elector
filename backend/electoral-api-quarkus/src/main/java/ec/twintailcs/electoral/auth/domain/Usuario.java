package ec.twintailcs.electoral.auth.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Usuario del sistema. Mismo schema que la entidad del backend Spring.
 *
 * <p>La identidad sigue siendo email + password (BCrypt). {@code rol} es una FK a {@link Rol}.
 *
 * <p>Port exacto de la entidad Spring: los setters recortan y normalizan igual que alla (el email
 * se guarda siempre en minusculas) y {@link #validarDatosObligatorios()} hace las mismas
 * comprobaciones, para que un usuario creado por cualquiera de las dos implementaciones tenga
 * exactamente los mismos valores.
 */
@Entity
@Table(name = "usuario")
public class Usuario extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @UuidGenerator(style = UuidGenerator.Style.VERSION_7)
  @Column(
      name = "public_id",
      nullable = false,
      unique = true,
      updatable = false,
      columnDefinition = "UUID")
  UUID publicId;

  @Column(name = "email", nullable = false, length = 254, unique = true)
  String email;

  @Column(name = "password_hash", nullable = false, length = 100)
  String passwordHash;

  @ManyToOne(fetch = FetchType.EAGER)
  @JoinColumn(name = "rol_id", nullable = false)
  Rol rol;

  @Column(name = "habilitado", nullable = false)
  boolean habilitado = true;

  @Column(name = "creado_en", nullable = false, updatable = false)
  Instant creadoEn = Instant.now();

  @Column(name = "nombre", nullable = false, length = 100)
  String nombre;

  @Column(name = "apellido", nullable = false, length = 100)
  String apellido;

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public String getEmail() {
    return email;
  }

  public String getPasswordHash() {
    return passwordHash;
  }

  public Rol getRol() {
    return rol;
  }

  public boolean isHabilitado() {
    return habilitado;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }

  public String getNombre() {
    return nombre;
  }

  public String getApellido() {
    return apellido;
  }

  public String getRolNombre() {
    return rol != null ? rol.getNombre() : null;
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

  public void setEmail(String email) {
    if (email == null || email.trim().isEmpty()) {
      throw new UsuarioInvalidoException("El email no puede estar vacío");
    }
    this.email = email.trim().toLowerCase();
  }

  public void setPasswordHash(String passwordHash) {
    this.passwordHash = passwordHash;
  }

  public void setRol(Rol rol) {
    this.rol = rol;
  }

  public void setHabilitado(boolean habilitado) {
    this.habilitado = habilitado;
  }

  /** Invariantes obligatorias del usuario. Mismas comprobaciones que el backend Spring. */
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
