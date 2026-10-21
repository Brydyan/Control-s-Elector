package ec.twintailcs.electoral.auth.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;

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
import org.hibernate.annotations.UuidGenerator;
import java.time.Instant;
import java.util.UUID;

/** Usuario del sistema. Mismo schema que la entidad del backend Spring. */
@Entity
@Table(name = "usuario")
public class Usuario extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
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
    this.nombre = nombre;
  }

  public void setApellido(String apellido) {
    this.apellido = apellido;
  }

  public void setEmail(String email) {
    this.email = email;
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
}
