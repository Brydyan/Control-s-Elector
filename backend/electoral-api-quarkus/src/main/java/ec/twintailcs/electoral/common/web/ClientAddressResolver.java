package ec.twintailcs.electoral.common.web;

import io.quarkus.vertx.http.runtime.CurrentVertxRequest;
import io.vertx.core.http.HttpServerRequest;
import io.vertx.core.net.SocketAddress;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;
import org.jboss.logging.Logger;

/**
 * Unico lugar donde se resuelve la IP del cliente.
 *
 * <p>Reemplaza la lectura directa de {@code X-Forwarded-For}: su primer valor lo escribe el
 * cliente, asi que tomarlo permitia inventar una IP distinta en cada request.
 *
 * <ul>
 *   <li>{@link #trustedIp()}: IP en la que se puede confiar para rate limiting. Vacia si no hay
 *       forma de probarla; en ese caso las politicas por IP no se aplican (si se aplicaran sobre la
 *       IP de Cloudflare, todos los usuarios compartirian contador y se bloquearian entre si).
 *   <li>{@link #auditIp()}: IP para auditoria y sesiones. Usa la confiable si existe y, si no, la
 *       de la conexion. Nunca un header que el cliente pueda escribir.
 * </ul>
 */
@ApplicationScoped
public class ClientAddressResolver {

  private static final Logger LOG = Logger.getLogger(ClientAddressResolver.class);
  private static final long WARN_INTERVAL_MILLIS = 60_000;

  /** IPv4 o IPv6 literal. No valida rangos: solo descarta basura e inyecciones en el header. */
  private static final Pattern IP_LITERAL = Pattern.compile("^[0-9A-Fa-f:.]{2,45}$");

  static final String UNKNOWN = "desconocida";

  @Inject ClientIpConfig config;
  @Inject CurrentVertxRequest currentRequest;

  private final AtomicLong lastWarnAt = new AtomicLong(Long.MIN_VALUE);

  /** IP del cliente apta para rate limiting, o vacia si no se puede probar. */
  public Optional<String> trustedIp() {
    HttpServerRequest request = request();
    if (request == null) {
      return Optional.empty();
    }
    Optional<String> secret = config.originSecret().filter(s -> !s.isBlank());
    if (secret.isPresent()) {
      String presented = request.getHeader(config.originSecretHeader());
      if (presented != null && constantTimeEquals(secret.get(), presented)) {
        return validIp(request.getHeader(config.clientIpHeader()));
      }
      warnUntrustedOrigin();
      return Optional.empty();
    }
    if (config.trustRemoteAddress()) {
      return validIp(remoteHost(request));
    }
    return Optional.empty();
  }

  /** IP para auditoria y sesiones: la confiable si existe, si no la de la conexion. */
  public String auditIp() {
    Optional<String> trusted = trustedIp();
    if (trusted.isPresent()) {
      return trusted.get();
    }
    HttpServerRequest request = request();
    String remote = request == null ? null : remoteHost(request);
    return remote == null || remote.isBlank() ? UNKNOWN : remote;
  }

  private HttpServerRequest request() {
    try {
      RoutingContext ctx = currentRequest.getCurrent();
      return ctx == null ? null : ctx.request();
    } catch (RuntimeException e) {
      // Fuera de un request HTTP (jobs, tests de servicio): no hay IP que resolver.
      return null;
    }
  }

  private static String remoteHost(HttpServerRequest request) {
    SocketAddress remote = request.remoteAddress();
    return remote == null ? null : remote.hostAddress();
  }

  private static Optional<String> validIp(String value) {
    if (value == null) {
      return Optional.empty();
    }
    String ip = value.trim();
    return IP_LITERAL.matcher(ip).matches() ? Optional.of(ip) : Optional.empty();
  }

  private static boolean constantTimeEquals(String expected, String presented) {
    return MessageDigest.isEqual(
        expected.getBytes(StandardCharsets.UTF_8), presented.getBytes(StandardCharsets.UTF_8));
  }

  /**
   * Un request sin el secreto teniendo el secreto configurado no paso por Cloudflare: alguien esta
   * entrando por otra puerta. Se avisa como mucho una vez por minuto y sin datos del request.
   */
  private void warnUntrustedOrigin() {
    long now = System.currentTimeMillis();
    long last = lastWarnAt.get();
    if ((last == Long.MIN_VALUE || now - last >= WARN_INTERVAL_MILLIS)
        && lastWarnAt.compareAndSet(last, now)) {
      LOG.warn(
          "Request sin el header de origen de Cloudflare: no paso por el proxy. Las politicas por"
              + " IP no se aplican a estos requests. Revisar que el dominio *.up.railway.app este"
              + " deshabilitado. Este aviso se repite como mucho una vez por minuto.");
    }
  }
}
