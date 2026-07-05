package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.MenuService;
import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.web.dto.MenuResponse;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints publicos para el usuario autenticado relacionados a menus.
 *
 * Ruta: /api/v1/menus (el frontend llama /api/v1/menus/me al login para
 * obtener su sidebar). El usuario se obtiene del SecurityContext
 * (poblado por JwtAuthFilter) via {@link CurrentUser}.
 *
 * Protegido por JWT: cualquier usuario autenticado puede pedir su propio
 * menu. SecurityConfig lo cubre con .anyRequest().authenticated().
 */
@RestController
@RequestMapping("/api/v1/menus")
public class MenuController {

    private final MenuService menuService;

    public MenuController(MenuService menuService) {
        this.menuService = menuService;
    }

    /**
     * Devuelve el arbol de menus visibles para el usuario autenticado.
     * Si el usuario no tiene permisos que matcheen ningun menu, la lista
     * viene vacia (el FE deberia mostrar el sidebar vacio o un fallback).
     */
    @GetMapping("/me")
    public MenuResponse myMenus() {
        UUID userId = CurrentUser.staticId();
        return menuService.getMyMenus(userId);
    }
}