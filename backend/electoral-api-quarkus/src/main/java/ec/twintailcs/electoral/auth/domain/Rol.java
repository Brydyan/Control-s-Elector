package ec.twintailcs.electoral.auth.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/** Rol de usuario. Mismo schema que la entidad del backend Spring. */
@Entity
@Table(name = "rol")
public class Rol extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "nombre", length = 32, nullable = false, unique = true)
  String nombre;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, columnDefinition = "UUID")
  UUID publicId;

  @Column(name = "creado_en", nullable = false)
  Instant creadoEn;

  public Long getId() {
    return id;
  }

  public String getNombre() {
    return nombre;
  }

  public UUID getPublicId() {
    return publicId;
  }
}
