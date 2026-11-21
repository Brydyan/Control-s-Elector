package ec.twintailcs.electoral.shared.testing;

import java.util.Map;

/**
 * Headers que simulan un request que paso por el proxy de Cloudflare.
 *
 * <p>Es el mismo mecanismo que produccion: el secreto de origen ({@code
 * %test.app.client-ip.origin-secret}) prueba que el request vino del proxy, y {@code
 * CF-Connecting-IP} trae la IP real. Un {@code X-Forwarded-For} solo no alcanza: el cliente lo
 * puede escribir.
 */
public final class ClientIpHeaders {

  public static final String ORIGIN_SECRET = "test-origin-secret";

  private ClientIpHeaders() {}

  /** Headers de un request de {@code ip} que paso por el proxy confiable. */
  public static Map<String, String> from(String ip) {
    return Map.of("X-Origin-Auth", ORIGIN_SECRET, "CF-Connecting-IP", ip);
  }
}
