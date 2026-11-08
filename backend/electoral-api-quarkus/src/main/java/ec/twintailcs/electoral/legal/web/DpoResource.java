package ec.twintailcs.electoral.legal.web;

import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.legal.application.DpoRateLimiter;
import ec.twintailcs.electoral.legal.application.LegalService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Formulario de contacto con el Delegado de Proteccion de Datos.
 *
 * <p>Es publico: lo puede usar cualquiera, no solo un usuario del sistema. Eso obliga a dos cosas
 * que este resource hace:
 *
 * <ol>
 *   <li>Validar el contenido en el servidor (el cliente puede no validar nada: la validacion del
 *       navegador es una ayuda, no una garantia).
 *   <li>Limitar por IP: sin eso, el formulario es un canal de spam y la tabla crece sola.
 * </ol>
 *
 * <p>La solicitud se guarda y NO se manda por email (decision D7 del design): el DPO puede estar
 * unos dias sin connectivity y el mensaje se pierde. La respuesta la escribe el DPO a mano en la
 * misma fila.
 */
@Path("/api/v1/legal")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class DpoResource {

  @Inject LegalService legalService;

  @Inject DpoRateLimiter rateLimiter;

  @Inject ContextoSolicitud contexto;

  /**
   * Recibe una solicitud de contacto con el DPO.
   *
   * @param cuerpo datos del formulario
   * @param xForwardedFor IP del cliente, puesta por el proxy
   */
  @POST
  @Path("/contact-dpo")
  @Consumes(MediaType.APPLICATION_JSON)
  @Transactional
  public Response contacto(
      @Valid ContactoDpoRequest cuerpo, @HeaderParam("X-Forwarded-For") String xForwardedFor) {

    String ip = contexto.ip(xForwardedFor);
    rateLimiter.registrar(ip);

    legalService.registrarSolicitudDpo(
        cuerpo.name(), cuerpo.email(), cuerpo.subject(), cuerpo.message(), ip);

    Map<String, String> body = new LinkedHashMap<>();
    body.put("status", "received");
    body.put("message", "Mensaje recibido. DPO responderá en 5 días hábiles.");
    return Response.ok(body).build();
  }

  /**
   * Datos del formulario de contacto con el DPO.
   *
   * <p>Los nombres de los campos son los del contrato (spec de DPO): {@code name}, {@code email},
   * {@code subject} y {@code message}. Son los mismos de la tabla, asi que lo que llega por el
   * cable es lo que se guarda, sin traduccion intermedia.
   *
   * <p>Los limites de longitud no son cosmeticos: acotan lo que se puede guardar en la base y lo
   * que se devuelve en una respuesta. El mensaje es el campo mas libre y por eso tiene el techo mas
   * bajo — 5000 caracteres alcanzan para exponer un problema y no para abusar del canal.
   *
   * @param name nombre de quien escribe
   * @param email correo de contacto
   * @param subject motivo de la solicitud
   * @param message contenido de la solicitud
   */
  public record ContactoDpoRequest(
      @NotBlank(message = "El nombre es requerido")
          @Size(max = 255, message = "El nombre no puede pasar de 255 caracteres")
          String name,
      @NotBlank(message = "El email es requerido")
          @Email(message = "El email debe ser válido")
          @Size(max = 254, message = "El email no puede pasar de 254 caracteres")
          String email,
      @NotBlank(message = "El asunto es requerido")
          @Size(max = 255, message = "El asunto no puede pasar de 255 caracteres")
          String subject,
      @NotBlank(message = "El mensaje es requerido")
          @Size(max = 5000, message = "El mensaje no puede pasar de 5000 caracteres")
          String message) {}
}
