package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Entidad de rol. Reemplaza el modelo anterior donde {@code rol} era un String acoplado en {@link
 * Usuario} y {@link RolPermiso}.
 *
 * <p>Los nombres se siembran en Liquibase (005-fks.sql) y son inmutables despues de creados:
 * MASTER, COORDINATOR, ADMIN, OBSERVER.
 */
@Entity
@Table(name = "rol")
public class Rol {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(length = 32, nullable = false, unique = true)
  private String nombre;

  @Column(nullable = false, unique = true, columnDefinition = "UUID")
  private UUID publicId;

  @Column(nullable = false)
  private Instant creadoEn;

  /**
   * @deprecated solo JPA.
   */
  @Deprecated
  protected Rol() {}

  public Rol(String nombre) {
    this.nombre = nombre;
    this.publicId = UUID.randomUUID();
    this.creadoEn = Instant.now();
  }

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
