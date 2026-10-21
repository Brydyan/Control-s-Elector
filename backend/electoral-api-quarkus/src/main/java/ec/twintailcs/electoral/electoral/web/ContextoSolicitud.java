package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Datos del request en curso que la configuracion necesita: quien lo hace y desde donde.
 *
 * <p>En el backend Spring cada controlador resolvia el usuario y la IP por su cuenta. Aca viven en
 * un solo lugar para que los tres recursos de configuracion repitan la misma logica.
 */
@ApplicationScoped
public class ContextoSolicitud {

  @Inject CurrentUser currentUser;

  /**
   * Id interno del usuario autenticado. Es el que va en la auditoria ({@code registrado_por}).
   *
   * @throws IllegalArgumentException si el usuario del token no existe en la base
   */
  public Long usuarioId() {
    Usuario usuario = Usuario.find("email", currentUser.email()).firstResult();
    if (usuario == null) {
      throw new IllegalArgumentException("Usuario no existe");
    }
    return usuario.getId();
  }

  /**
   * IP del cliente. Toma el primer valor de {@code X-Forwarded-For} (el que deja el proxy) y, si no
   * viene, deja {@code sistema}.
   */
  public String ip(String xForwardedFor) {
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return "sistema";
  }
}
