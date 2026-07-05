package ec.twintailcs.electoral.auth.web.dto;

import java.util.List;

/**
 * Response de GET /api/v1/menus/me.
 *
 * Estructura jerarquica: cada nodo puede tener hijos. El servidor hace
 * el trabajo de aplanar por permisos del usuario + walk-up de padres;
 * el frontend solo itera y renderiza.
 *
 * Wrap en objeto (no array directo) para futura extension: agregar
 * metadata sin breaking change (ej: timestamp, version del catalogo).
 */
public record MenuResponse(List<MenuNode> menus) {

    /**
     * Nodo del arbol. {@code children} siempre presente (vacio si es hoja).
     * Orden de campos pensado para legibilidad en JSON, no logica.
     */
    public record MenuNode(
            Long id,
            Long parentId,
            String codigo,
            String label,
            String icono,
            String ruta,
            Integer orden,
            Boolean activo,
            List<MenuNode> children
    ) {}
}