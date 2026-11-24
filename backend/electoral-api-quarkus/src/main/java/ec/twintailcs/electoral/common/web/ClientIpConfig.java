package ec.twintailcs.electoral.common.web;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;
import java.util.Optional;

/**
 * Como se obtiene la IP real del cliente ({@code app.client-ip.*}).
 *
 * <p>En produccion la API esta detras de Cloudflare (proxy): la conexion llega desde una IP de
 * Cloudflare y la IP real viene en {@code CF-Connecting-IP}. Ese header solo se cree si el request
 * trae tambien el header secreto que agrega una Transform Rule de Cloudflare: sin esa prueba,
 * cualquiera que entre por otra puerta (por ejemplo el dominio *.up.railway.app) podria inventarse
 * la IP.
 */
@ConfigMapping(prefix = "app.client-ip")
public interface ClientIpConfig {

  /**
   * Secreto que Cloudflare agrega en cada request ({@code APP_ORIGIN_AUTH_SECRET}). Si no esta
   * configurado, la IP del cliente no es confiable y las politicas por IP no se aplican.
   */
  Optional<String> originSecret();

  /** Header donde Cloudflare manda el secreto. */
  @WithDefault("X-Origin-Auth")
  String originSecretHeader();

  /** Header donde el proxy confiable manda la IP real del cliente. */
  @WithDefault("CF-Connecting-IP")
  String clientIpHeader();

  /**
   * Confiar en la IP de la conexion TCP. Solo para desarrollo local, donde no hay proxy delante. En
   * produccion debe ser false: la conexion viene de Cloudflare o del edge de Railway.
   */
  @WithDefault("false")
  boolean trustRemoteAddress();
}
