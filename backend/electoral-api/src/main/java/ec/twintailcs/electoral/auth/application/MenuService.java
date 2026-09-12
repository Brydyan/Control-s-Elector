package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Menu;
import ec.twintailcs.electoral.auth.domain.MenuPermisoRepository;
import ec.twintailcs.electoral.auth.domain.MenuRepository;
import ec.twintailcs.electoral.auth.domain.RolPermisoRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.auth.web.dto.MenuResponse;
import ec.twintailcs.electoral.auth.web.dto.MenuResponse.MenuNode;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Construye el sub-arbol de menus visibles para un usuario dado.
 *
 * <p>Reglas: 1. Un menu es DIRECTAMENTE visible si: a) Es "publico" (codigo en {@link
 * #PUBLIC_MENU_CODES}) — ej: Dashboard. b) Tiene al menos un menu_permiso y alguno matchea con un
 * permiso efectivo del usuario (regla OR entre permisos del menu). c) Es un padre que aparece via
 * walk-up (regla 2). 2. Walk-up: si un menu es visible, todos sus ancestros se incluyen para
 * mantener la estructura visual del sidebar (leccion de jasrapo-backend, migration 20260517013500).
 * 3. Los menus sin menu_permiso y no publicos NO aparecen aunque sean hijos de un padre visible.
 * Esto evita ruido en el catalogo.
 *
 * <p>Cardinalidad esperada: ~20-50 menus totales. Se carga el catalogo completo y se filtra en
 * memoria — mas simple que N queries parametrizadas.
 */
@Service
public class MenuService {

  /**
   * Codigos de menus visibles para cualquier usuario autenticado, sin requerir permisos explicitos.
   * Hoy solo "dashboard". Si se agregan mas (help, logout, about), extender esta lista.
   *
   * <p>Alternativa evaluada: columna {@code publico BOOLEAN} en menu. Descartada por YAGNI: una
   * sola entrada no justifica una columna nueva + su migracion + su inclusion en queries de filtro.
   */
  private static final List<String> PUBLIC_MENU_CODES = List.of("dashboard");

  private final MenuRepository menus;
  private final MenuPermisoRepository menuPermisos;
  private final RolPermisoRepository rolPermisos;
  private final UsuarioRepository usuarios;

  public MenuService(
      MenuRepository menus,
      MenuPermisoRepository menuPermisos,
      RolPermisoRepository rolPermisos,
      UsuarioRepository usuarios) {
    this.menus = menus;
    this.menuPermisos = menuPermisos;
    this.rolPermisos = rolPermisos;
    this.usuarios = usuarios;
  }

  @Transactional(readOnly = true)
  public MenuResponse getMyMenus(UUID userPublicId) {
    Usuario u =
        usuarios
            .findByPublicId(userPublicId)
            .orElseThrow(
                () -> new ec.twintailcs.electoral.auth.domain.AuthException("Usuario no existe"));
    Long usuarioId = u.getId();

    // 1. Permisos efectivos del usuario como authority strings (recurso:accion).
    Set<String> userAuths = new HashSet<>(rolPermisos.findAuthorityStringsByUsuarioId(usuarioId));

    // 2. Catalogo completo de menus activos.
    List<Menu> allActive = menus.findAllActive();

    // 3. Map menuId -> set de permisos requeridos (authority strings).
    Map<Long, Set<String>> menuRequired = buildMenuRequiredMap();

    // 4. Menus directamente visibles (regla 1).
    Map<Long, Menu> visible = new LinkedHashMap<>();
    for (Menu m : allActive) {
      Set<String> required = menuRequired.getOrDefault(m.getId(), Set.of());
      if (isDirectlyVisible(m, required, userAuths)) {
        visible.put(m.getId(), m);
      }
    }

    // 5. Walk-up: incluir ancestros faltantes (regla 2).
    includeAncestors(visible, allActive);

    // 6. Construir arbol jerarquico ordenado.
    List<MenuNode> tree = buildTree(new ArrayList<>(visible.values()));
    return new MenuResponse(tree);
  }

  private Map<Long, Set<String>> buildMenuRequiredMap() {
    Map<Long, Set<String>> result = new HashMap<>();
    for (Object[] row : menuPermisos.findAllMenuAuthorityPairs()) {
      Long menuId = (Long) row[0];
      String auth = (String) row[1];
      result.computeIfAbsent(menuId, k -> new HashSet<>()).add(auth);
    }
    return result;
  }

  private boolean isDirectlyVisible(Menu m, Set<String> requiredAuths, Set<String> userAuths) {
    if (PUBLIC_MENU_CODES.contains(m.getCodigo())) {
      return true;
    }
    if (requiredAuths.isEmpty()) {
      // Sin permisos requeridos y no publico: solo aparece si algun
      // descendiente lo hace visible via walk-up. Lo decide la regla 2.
      return false;
    }
    return requiredAuths.stream().anyMatch(userAuths::contains);
  }

  private void includeAncestors(Map<Long, Menu> visible, List<Menu> allActive) {
    // Iterativo para evitar recursion infinita en caso de ciclos en parent_id.
    Set<Long> frontier = new HashSet<>(visible.keySet());
    while (!frontier.isEmpty()) {
      Set<Long> parentIds = new HashSet<>();
      for (Long menuId : frontier) {
        Menu m = visible.get(menuId);
        if (m != null && m.getParentId() != null && !visible.containsKey(m.getParentId())) {
          parentIds.add(m.getParentId());
        }
      }
      if (parentIds.isEmpty()) {
        break;
      }
      boolean added = false;
      for (Long parentId : parentIds) {
        Menu parent = findMenu(allActive, parentId);
        if (parent != null && visible.put(parent.getId(), parent) == null) {
          added = true;
        }
      }
      if (!added) {
        break; // No se agregaron nuevos -> terminamos.
      }
      frontier = parentIds;
    }
  }

  private Menu findMenu(List<Menu> all, Long id) {
    for (Menu m : all) {
      if (m.getId().equals(id)) {
        return m;
      }
    }
    return null;
  }

  private List<MenuNode> buildTree(List<Menu> flat) {
    // Orden estable: orden del modelo, luego label como tiebreaker.
    flat.sort(
        Comparator.comparing(Menu::getOrden, Comparator.nullsLast(Comparator.naturalOrder()))
            .thenComparing(Menu::getLabel, Comparator.nullsLast(Comparator.naturalOrder())));

    // Lookup map for Long ID -> UUID publicId of menus in the flat list
    Map<Long, UUID> idToPublicId = new HashMap<>();
    for (Menu m : flat) {
      idToPublicId.put(m.getId(), m.getPublicId());
    }

    Map<Long, MenuNode> nodes = new LinkedHashMap<>();
    for (Menu m : flat) {
      UUID publicId = m.getPublicId();
      UUID parentPublicId = m.getParentId() != null ? idToPublicId.get(m.getParentId()) : null;
      nodes.put(
          m.getId(),
          new MenuNode(
              publicId,
              parentPublicId,
              m.getCodigo(),
              m.getLabel(),
              m.getIcono(),
              m.getRuta(),
              m.getOrden(),
              m.getActivo(),
              new ArrayList<>()));
    }

    List<MenuNode> roots = new ArrayList<>();
    for (Menu m : flat) {
      MenuNode node = nodes.get(m.getId());
      if (m.getParentId() != null && nodes.containsKey(m.getParentId())) {
        nodes.get(m.getParentId()).children().add(node);
      } else {
        roots.add(node);
      }
    }
    return roots;
  }
}
