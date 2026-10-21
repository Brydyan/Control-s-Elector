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
 * Tabla intermedia N:M entre Menu y Permiso.
 *
 * <p>Un menu es visible para un usuario si al menos UNA de sus entradas en menu_permiso matchea con
 * un permiso efectivo del usuario (regla OR). Si un menu no tiene filas en menu_permiso, NO es
 * visible para nadie — a excepcion de los menus padres que el MenuService incluye via walk-up para
 * mantener la estructura visual del sidebar.
 *
 * <p>Port exacto de la entidad del backend Spring: mismas columnas y mismo generador de {@code
 * public_id}. Como en Spring, la relacion con Permiso no se modela como {@code @ManyToOne}: el
 * filtro por permisos se arma con un JOIN explicito.
 */
@Entity
@Table(
    name = "menu_permiso",
    uniqueConstraints =
        @UniqueConstraint(
            name = "uk_menu_permiso",
            columnNames = {"menu_id", "permiso_id"}))
public class MenuPermiso extends PanacheEntityBase {

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

  @Column(name = "menu_id", nullable = false)
  Long menuId;

  @Column(name = "permiso_id", nullable = false)
  Long permisoId;

  /** Soft delete. */
  @Column(name = "borrado_en")
  Instant borradoEn;

  public Long getMenuId() {
    return menuId;
  }

  public Long getPermisoId() {
    return permisoId;
  }
}
