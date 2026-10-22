package ec.twintailcs.electoral.auth.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Item de menu del sistema. Estructura jerarquica (self-reference via {@code parent_id}). Los
 * permisos que requiere cada menu para ser visible viven en {@link MenuPermiso}.
 *
 * <p>Port exacto de la entidad del backend Spring: mismas columnas, mismo generador de {@code
 * public_id} y mismo esquema.
 *
 * <p>Decisiones:
 *
 * <ul>
 *   <li>{@code codigo} es la clave de UI estable (ej: "actas.aprobar"). La usa el frontend como
 *       i18n key, NO el {@code id} que es autonumerico y puede variar entre ambientes.
 *   <li>Permisos NO viven en Menu: van en MenuPermiso (muchos-a-muchos). Esto permite que un mismo
 *       menu sea visible para varios permisos (OR logico) y mantiene Menu chico.
 *   <li>{@code borradoEn} = soft delete. Preserva historial cuando se elimina una entrada del
 *       catalogo.
 * </ul>
 */
@Entity
@Table(
    name = "menu",
    uniqueConstraints = @UniqueConstraint(name = "uk_menu_codigo", columnNames = "codigo"))
public class Menu extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, updatable = false, columnDefinition = "UUID")
  UUID publicId;

  /** FK self-reference. NULL = item raiz. */
  @Column(name = "parent_id")
  Long parentId;

  /** Codigo estable para i18n y routing en FE. ej: "actas.aprobar". */
  @Column(name = "codigo", nullable = false, length = 64, unique = true)
  String codigo;

  /**
   * Etiqueta visible. La fuente de verdad actual es este campo; la internacionalizacion futura
   * mapeara codigo -> bundle.
   */
  @Column(name = "label", nullable = false, length = 128)
  String label;

  /** Nombre de icono Ionic / Material. ej: "document", "home". NULL si no aplica. */
  @Column(name = "icono", length = 64)
  String icono;

  /** Ruta Angular/Ionic. ej: "/actas/nueva". */
  @Column(name = "ruta", nullable = false, length = 256)
  String ruta;

  /** Orden de presentacion dentro del mismo nivel (menor = primero). */
  @Column(name = "orden", nullable = false)
  Integer orden = 0;

  /** Si false, no se incluye en el response aunque tenga permisos. */
  @Column(name = "activo", nullable = false)
  Boolean activo = true;

  /** Soft delete. Items con borradoEn != null se ignoran en queries. */
  @Column(name = "borrado_en")
  Instant borradoEn;

  public Long getId() {
    return id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public Long getParentId() {
    return parentId;
  }

  public String getCodigo() {
    return codigo;
  }

  public String getLabel() {
    return label;
  }

  public String getIcono() {
    return icono;
  }

  public String getRuta() {
    return ruta;
  }

  public Integer getOrden() {
    return orden;
  }

  public Boolean getActivo() {
    return activo;
  }
}
