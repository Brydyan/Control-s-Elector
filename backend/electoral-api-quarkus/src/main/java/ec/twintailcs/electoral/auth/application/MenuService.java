package ec.twintailcs.electoral.auth.application;

import ec.twintailcs.electoral.auth.domain.Menu;
import ec.twintailcs.electoral.auth.domain.MenuPermiso;
import ec.twintailcs.electoral.auth.domain.Permiso;
import ec.twintailcs.electoral.auth.domain.RolPermiso;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.application.MenuResponse.MenuNode;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.transaction.Transactional;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Construye el sub-arbol de menus visibles para un usuario dado.
 *
 * <p>Reglas:
 *
 * <ol>
 *   <li>Un menu es DIRECTAMENTE visible si: a) Es "publico" (codigo en {@link
 *       #PUBLIC_MENU_CODES}) — ej: Dashboard. b) Tiene al menos un menu_permiso y alguno matchea con
 *       un permiso efectivo del usuario (regla OR entre permisos del menu). c) Es un padre que
 *       aparece via walk-up (regla 2).
 *   <li>Walk-up: si un menu es visible, todos sus ancestros se incluyen para mantener la
 *       estructura visual del sidebar.
 *   <li>Los menus sin menu_permiso y no publicos NO aparecen aunque sean hijos de un padre
 *       visible. Esto evita ruido en el catalogo.
 * </ol>
 *
 * <p>Cardinalidad esperada: ~20-50 menus totales. Se carga el catalogo completo y se filtra en
 * memoria — mas simple que N queries parametrizadas.
 *
 * <p>Port exacto del {@code MenuService} del backend Spring: mismas reglas, mismo arbol y mismo
 * shape de respuesta.
 */
@ApplicationScoped
public class MenuService {

  /**
   * Codigos de menus visibles para cualquier usuario autenticado, sin requerir permisos explicitos.
   * Hoy solo "dashboard". Si se agregan mas (help, logout, about), extender esta lista.
   */
  private static final List<String> PUBLIC_MENU_CODES = List.of("dashboard");

  /**
   * Arbol de menus visibles para el usuario.
   *
   * @param email email del usuario autenticado
   * @throws AuthException si el usuario del token no existe en la base
   */
  @Transactional
  public MenuResponse getMyMenus(String email) {
    Usuario u = Usuario.find("email", email).firstResult();
    if (u == null) {
      throw new AuthException("Usuario no existe");
    }
    Long usuarioId = u.getId();

    // 1. Permisos efectivos del usuario como authority strings (recurso:accion).
    Set<String> userAuths = new HashSet<>(RolPermiso.authorityStringsByUsuarioId(usuarioId));

    // 2. Catalogo completo de menus activos.
    List<Menu> allActive = menusActivos();

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

  /** Catalogo completo de menus activos, ordenados por (orden, label). */
  private List<Menu> menusActivos() {
    return Menu.getEntityManager()
        .createQuery(
            "select m from Menu m"
                + " where m.borradoEn is null and m.activo = true"
                + " order by m.orden asc, m.label asc",
            Menu.class)
        .getResultList();
  }

  /** MenuId -> permisos que habilitan el menu, en forma de authority strings. */
  private Map<Long, Set<String>> buildMenuRequiredMap() {
    Map<Long, String> authorityPorPermisoId = new HashMap<>();
    List<Permiso> permisos = Permiso.listAll();
    for (Permiso permiso : permisos) {
      authorityPorPermisoId.put(permiso.getId(), permiso.getRecurso() + ":" + permiso.getAccion());
    }

    List<MenuPermiso> vinculos =
        MenuPermiso.getEntityManager()
            .createQuery(
                "select mp from MenuPermiso mp where mp.borradoEn is null", MenuPermiso.class)
            .getResultList();

    Map<Long, Set<String>> result = new HashMap<>();
    for (MenuPermiso vinculo : vinculos) {
      String authority = authorityPorPermisoId.get(vinculo.getPermisoId());
      if (authority != null) {
        result.computeIfAbsent(vinculo.getMenuId(), k -> new HashSet<>()).add(authority);
      }
    }
    return result;
  }

  /** Regla 1: visibilidad directa del menu. */
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

  /** Regla 2: agrega los ancestros de los menus visibles para mantener la estructura del sidebar. */
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

  /** Busca un menu del catalogo por su id interno. */
  private Menu findMenu(List<Menu> all, Long id) {
    for (Menu m : all) {
      if (m.getId().equals(id)) {
        return m;
      }
    }
    return null;
  }

  /** Arma el arbol a partir de la lista plana de menus visibles. */
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
