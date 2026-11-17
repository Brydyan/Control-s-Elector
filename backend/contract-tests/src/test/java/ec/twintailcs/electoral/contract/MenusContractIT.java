package ec.twintailcs.electoral.contract;

import static org.assertj.core.api.Assertions.assertThat;

import io.restassured.response.Response;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Contrato del árbol de menús del usuario: {@code GET /api/v1/menus/me}.
 *
 * <p>El frontend arma el sidebar con esta respuesta, derivándolo de los permisos RBAC del usuario.
 * Es el endpoint que hoy no tenía cobertura de controller: la suite anterior solo probaba el DTO
 * {@code MenuResponse}, no el endpoint.
 *
 * <p>Contrato fijado:
 *
 * <ul>
 *   <li>{@code GET /api/v1/menus/me} → 200 con {@code {menus: [...]}} en forma de árbol, cada nodo
 *       con {@code id}, {@code codigo}, {@code label}, {@code icono}, {@code ruta}, {@code orden},
 *       {@code activo} y {@code children}.
 *   <li>Sin token → 401.
 *   <li>El árbol depende del rol: un OBSERVER no puede ver los nodos de MASTER.
 * </ul>
 */
class MenusContractIT extends ContractTestSupport {

  /** Prefijo del módulo de menús. */
  private static final String MENUS = "/api/v1/menus";

  @Test
  @DisplayName("GET /menus/me devuelve el árbol de menús del usuario autenticado")
  void menusMeDevuelveElArbolDelUsuario() {
    String token = tokenFor("COORDINATOR");

    Response r = as(token).get(MENUS + "/me");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    List<Map<String, Object>> menus = r.jsonPath().<Map<String, Object>>getList("menus");
    assertThat(menus).as("el usuario debe tener al menos un menu: %s", r.asString()).isNotEmpty();

    assertThat(menus)
        .allSatisfy(
            menu -> {
              assertThat(menu.get("id")).as("id del menu: %s", menu).isNotNull();
              assertThat((String) menu.get("codigo")).as("codigo del menu: %s", menu).isNotBlank();
              assertThat((String) menu.get("label")).as("label del menu: %s", menu).isNotBlank();
              assertThat(menu.get("icono")).as("icono del menu: %s", menu).isNotNull();
              assertThat(menu.get("ruta")).as("ruta del menu: %s", menu).isNotNull();
              assertThat(menu.get("orden")).as("orden del menu: %s", menu).isNotNull();
              assertThat(menu.get("activo")).as("activo del menu: %s", menu).isNotNull();
              assertThat(menu.get("children")).as("children del menu: %s", menu).isNotNull();
            });
  }

  @Test
  @DisplayName("el árbol de menús es un árbol: los hijos tienen la misma forma que la raíz")
  void losHijosDelMenuTienenLaMismaFormaQueLaRaiz() {
    String token = tokenFor("MASTER");

    Response r = as(token).get(MENUS + "/me");

    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    for (Map<String, Object> menu : r.jsonPath().<Map<String, Object>>getList("menus")) {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> hijos = (List<Map<String, Object>>) menu.get("children");
      assertThat(hijos).as("children del menu: %s", menu).isNotNull();
      for (Map<String, Object> hijo : hijos) {
        assertThat(hijo.get("id")).as("id del hijo: %s", hijo).isNotNull();
        assertThat((String) hijo.get("codigo")).as("codigo del hijo: %s", hijo).isNotBlank();
        assertThat((String) hijo.get("label")).as("label del hijo: %s", hijo).isNotBlank();
        assertThat(hijo.get("children")).as("children del hijo: %s", hijo).isNotNull();
      }
    }
  }

  @Test
  @DisplayName("el árbol de menús depende del rol: OBSERVER y MASTER no ven el mismo menú")
  void elArbolDeMenusDependeDelRol() {
    Response observer = as(tokenFor("OBSERVER")).get(MENUS + "/me");
    Response master = as(masterToken()).get(MENUS + "/me");

    assertThat(observer.statusCode()).isEqualTo(200);
    assertThat(master.statusCode()).isEqualTo(200);
    assertThat(observer.jsonPath().<Map<String, Object>>getList("menus")).isNotEmpty();
    assertThat(master.jsonPath().<Map<String, Object>>getList("menus")).isNotEmpty();

    // Los menús se derivan de los permisos: un OBSERVER (que solo tiene acta:read/upload y
    // evidencia:*) no puede ver los mismos nodos que un MASTER.
    List<String> codigosObserver = codigosDe(observer.jsonPath().<Map<String, Object>>getList("menus"));
    List<String> codigosMaster = codigosDe(master.jsonPath().<Map<String, Object>>getList("menus"));

    assertThat(codigosObserver).as("codigos del observer: %s", codigosObserver).isNotEmpty();
    assertThat(codigosMaster).as("codigos del master: %s", codigosMaster).isNotEmpty();
    assertThat(codigosMaster).isNotEqualTo(codigosObserver);
  }

  @Test
  @DisplayName("solo MASTER ve el menu de Configuración Geográfica")
  void soloMasterVeElMenuDeGeografia() {
    // El menú cuelga del permiso `configuracion:write`, que solo MASTER tiene: ADMIN puede
    // leer la geografía pero no administrarla.
    assertThat(codigosDe(menusDe(masterToken()))).contains("admin.geografia");

    for (String rol : new String[] {"ADMIN", "COORDINATOR", "OBSERVER"}) {
      assertThat(codigosDe(menusDe(tokenFor(rol))))
          .as("el menu de geografia no puede estar para %s", rol)
          .doesNotContain("admin.geografia");
    }
  }

  @Test
  @DisplayName("GET /menus/me sin token devuelve 401")
  void menusMeSinTokenDevuelve401() {
    Response r = anon().get(MENUS + "/me");

    assertErrorShape(r, 401);
  }

  /** Árbol de menús del usuario con el token dado. */
  private List<Map<String, Object>> menusDe(String token) {
    Response r = as(token).get(MENUS + "/me");
    assertThat(r.statusCode()).as("body: %s", r.asString()).isEqualTo(200);
    return r.jsonPath().<Map<String, Object>>getList("menus");
  }

  /** Códigos de todos los nodos del árbol, en profundidad. */
  private static List<String> codigosDe(List<Map<String, Object>> menus) {
    List<String> codigos = new java.util.ArrayList<>();
    for (Map<String, Object> menu : menus) {
      codigos.add(String.valueOf(menu.get("codigo")));
      Object hijos = menu.get("children");
      if (hijos instanceof List<?> lista) {
        for (Object hijo : lista) {
          if (hijo instanceof Map<?, ?> mapa) {
            @SuppressWarnings("unchecked")
            Map<String, Object> hijoComoMapa = (Map<String, Object>) mapa;
            codigos.add(String.valueOf(hijoComoMapa.get("codigo")));
          }
        }
      }
    }
    return codigos;
  }
}
