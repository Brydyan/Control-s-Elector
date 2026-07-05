package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.Instant;

/**
 * Tabla intermedia N:M entre Menu y Permiso.
 *
 * Un menu es visible para un usuario si al menos UNA de sus entradas en
 * menu_permiso matchea con un permiso efectivo del usuario (regla OR).
 * Si un menu no tiene filas en menu_permiso, NO es visible para nadie —
 * a excepcion de los menus padres que el MenuService incluye via walk-up
 * para mantener la estructura visual del sidebar.
 *
 * Modelo:
 *   menu (1) --- (N) menu_permiso (N) --- (1) permiso
 *
 * No se modela con @ManyToOne hacia Permiso para mantener consistencia
 * con {@link RolPermiso} (que tampoco lo hace) y porque las queries de
 * filtrado por permisos se arman en JPQL con JOIN explicito.
 */
@Entity
@Table(name = "menu_permiso", uniqueConstraints = {
        @UniqueConstraint(name = "uk_menu_permiso", columnNames = {"menu_id", "permiso_id"})
}, indexes = {
        @Index(name = "ix_menu_permiso_menu", columnList = "menu_id"),
        @Index(name = "ix_menu_permiso_permiso", columnList = "permiso_id")
})
public class MenuPermiso {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "menu_id", nullable = false)
    private Long menuId;

    @Column(name = "permiso_id", nullable = false)
    private Long permisoId;

    /** Soft delete. */
    @Column(name = "borrado_en")
    private Instant borradoEn;

    public MenuPermiso() {}

    public MenuPermiso(Long menuId, Long permisoId) {
        this.menuId = menuId;
        this.permisoId = permisoId;
    }

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getMenuId() { return menuId; }
    public void setMenuId(Long menuId) { this.menuId = menuId; }
    public Long getPermisoId() { return permisoId; }
    public void setPermisoId(Long permisoId) { this.permisoId = permisoId; }
    public Instant getBorradoEn() { return borradoEn; }
    public void setBorradoEn(Instant borradoEn) { this.borradoEn = borradoEn; }
}