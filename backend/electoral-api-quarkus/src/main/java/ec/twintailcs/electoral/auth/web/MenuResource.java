package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.MenuResponse;
import ec.twintailcs.electoral.auth.application.MenuService;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/**
 * Endpoints publicos para el usuario autenticado relacionados a menus.
 *
 * <p>Port exacto del {@code MenuController} del backend Spring: mismo path, mismo status y mismo
 * cuerpo. El frontend llama {@code /api/v1/menus/me} al login para obtener su sidebar.
 *
 * <p>Protegido por JWT: cualquier usuario autenticado puede pedir su propio menu. El filtro de
 * autenticacion ya exige token en toda ruta que no sea publica.
 */
@Path("/api/v1/menus")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class MenuResource {

  @Inject MenuService menuService;

  @Inject CurrentUser currentUser;

  /**
   * Devuelve el arbol de menus visibles para el usuario autenticado. Si el usuario no tiene
   * permisos que matcheen ningun menu, la lista viene vacia (el FE deberia mostrar el sidebar vacio
   * o un fallback).
   */
  @GET
  @Path("/me")
  public MenuResponse myMenus() {
    return menuService.getMyMenus(currentUser.email());
  }
}
