package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.MenuService;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.MenuResponse;
import ec.twintailcs.electoral.shared.web.dto.ApiErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos para el usuario autenticado relacionados a menus.
 *
 * <p>Ruta: /api/v1/menus (el frontend llama /api/v1/menus/me al login para obtener su sidebar). El
 * usuario se obtiene del SecurityContext (poblado por JwtAuthFilter) via {@link CurrentUser}.
 *
 * <p>Protegido por JWT: cualquier usuario autenticado puede pedir su propio menu. SecurityConfig lo
 * cubre con .anyRequest().authenticated().
 */
@Tag(name = "Auth", description = "Endpoints de autenticacion, sesion y registro de usuarios.")
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

  private final MenuService menuService;

  public MenuController(MenuService menuService) {
    this.menuService = menuService;
  }

  /**
   * Devuelve el arbol de menus visibles para el usuario autenticado. Si el usuario no tiene
   * permisos que matcheen ningun menu, la lista viene vacia (el FE deberia mostrar el sidebar vacio
   * o un fallback).
   */
  @Operation(
      summary = "Arbol de menus del usuario",
      description =
          "Retorna el arbol de menus visibles para el usuario autenticado segun sus permisos RBAC.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Arbol de menus",
        content = @Content(schema = @Schema(implementation = MenuResponse.class))),
    @ApiResponse(
        responseCode = "401",
        description = "No autenticado",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @GetMapping("/me")
  public MenuResponse myMenus() {
    UUID userId = CurrentUser.staticId();
    return menuService.getMyMenus(userId);
  }
}
