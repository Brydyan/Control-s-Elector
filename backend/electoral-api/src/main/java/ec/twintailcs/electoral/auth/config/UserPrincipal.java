package ec.twintailcs.electoral.auth.config;

import java.util.List;
import java.util.UUID;

/**
 * Principal enriquecido que se inyecta en el SecurityContext cuando
 * el JWT es valido.
 *
 * Reemplaza el String email que JwtAuthFilter usaba antes: ahora
 * transporta id, email, rol y permisos para que CurrentUser pueda
 * extraer valores reales (no defaults como 0L y null).
 */
public record UserPrincipal(UUID id, String email, String rol, List<String> permisos) {
}
