package ec.twintailcs.electoral.auth.domain;

import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;

/**
 * Item de menu del sistema. Estructura jerarquica (self-reference via {@code parent_id}). Los
 * permisos que requiere cada menu para ser visible viven en {@link MenuPermiso}.
 *
 * <p>Decisiones: - {@code codigo} es la clave de UI estable (ej: "actas.aprobar"). La usa el
 * frontend como i18n key, NO el {@code id} que es autonumerico y puede variar entre ambientes. -
 * Permisos NO viven en Menu: van en MenuPermiso (muchos-a-muchos). Esto permite que un mismo menu
 * sea visible para varios permisos (OR logico) y mantiene Menu chico. - {@code borradoEn} = soft
 * delete. Preserva historial cuando se elimina una entrada del catalogo.
 */
@Entity
@Table(
    name = "menu",
    uniqueConstraints = {@UniqueConstraint(name = "uk_menu_codigo", columnNames = "codigo")},
    indexes = {
      @Index(name = "ix_menu_parent", columnList = "parent_id"),
      @Index(name = "ix_menu_activo", columnList = "activo")
    })
public class Menu {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @UuidGenerator(algorithm = UuidV7Generator.class)
  @Column(name = "public_id", nullable = false, unique = true, updatable = false)
  private UUID publicId;

  /** FK self-reference. NULL = item raiz. */
  @Column(name = "parent_id")
  private Long parentId;

  /** Codigo estable para i18n y routing en FE. ej: "actas.aprobar". */
  @Column(nullable = false, length = 64, unique = true)
  private String codigo;

  /**
   * Etiqueta visible. La fuente de verdad actual es este campo; la internacionalizacion futura
   * mapeara codigo -> bundle.
   */
  @Column(nullable = false, length = 128)
  private String label;

  /** Nombre de icono Ionic / Material. ej: "document", "home". NULL si no aplica. */
  @Column(length = 64)
  private String icono;

  /** Ruta Angular/Ionic. ej: "/actas/nueva". */
  @Column(nullable = false, length = 256)
  private String ruta;

  /** Orden de presentacion dentro del mismo nivel (menor = primero). */
  @Column(nullable = false)
  private Integer orden = 0;

  /** Si false, no se incluye en el response aunque tenga permisos. */
  @Column(nullable = false)
  private Boolean activo = true;

  /** Soft delete. Items con borradoEn != null se ignoran en queries. */
  @Column(name = "borrado_en")
  private Instant borradoEn;

  public Menu() {}

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public UUID getPublicId() {
    return publicId;
  }

  public void setPublicId(UUID publicId) {
    this.publicId = publicId;
  }

  public Long getParentId() {
    return parentId;
  }

  public void setParentId(Long parentId) {
    this.parentId = parentId;
  }

  public String getCodigo() {
    return codigo;
  }

  public void setCodigo(String codigo) {
    this.codigo = codigo;
  }

  public String getLabel() {
    return label;
  }

  public void setLabel(String label) {
    this.label = label;
  }

  public String getIcono() {
    return icono;
  }

  public void setIcono(String icono) {
    this.icono = icono;
  }

  public String getRuta() {
    return ruta;
  }

  public void setRuta(String ruta) {
    this.ruta = ruta;
  }

  public Integer getOrden() {
    return orden;
  }

  public void setOrden(Integer orden) {
    this.orden = orden;
  }

  public Boolean getActivo() {
    return activo;
  }

  public void setActivo(Boolean activo) {
    this.activo = activo;
  }

  public Instant getBorradoEn() {
    return borradoEn;
  }

  public void setBorradoEn(Instant borradoEn) {
    this.borradoEn = borradoEn;
  }
}
