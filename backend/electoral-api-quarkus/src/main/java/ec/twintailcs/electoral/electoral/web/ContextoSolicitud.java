package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.common.web.ClientAddressResolver;
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

  @Inject ClientAddressResolver clientAddress;

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
   * IP del cliente para auditoria. La resuelve {@link ClientAddressResolver#auditIp()}.
   *
   * <p>El parametro se ignora a proposito: el primer valor de {@code X-Forwarded-For} lo escribe el
   * cliente y tomarlo permitia falsificar la IP auditada. Se mantiene la firma para no tocar los
   * endpoints que la usan; quitarla es una limpieza pendiente.
   *
   * @param xForwardedFor ignorado
   */
  public String ip(String xForwardedFor) {
    return clientAddress.auditIp();
  }
}
