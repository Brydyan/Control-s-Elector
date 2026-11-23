package ec.twintailcs.electoral.shared.web;

import ec.twintailcs.electoral.auth.application.AuthException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.WebApplicationException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import org.eclipse.microprofile.config.ConfigProvider;
import org.jboss.logging.Logger;

/**
 * Mapeo de excepciones al shape estandar de error.
 *
 * <p>La validacion de bean corre ANTES que la autorizacion (igual que en Spring): un body invalido
 * con un rol sin permiso devuelve 400, no 403.
 */
public final class ErrorMappers {

  private ErrorMappers() {}

  /** Error de validacion de bean → 400 con el detalle por campo. */
  @Provider
  public static class ConstraintViolationMapper
      implements ExceptionMapper<ConstraintViolationException> {

    private static final Logger log = Logger.getLogger(ConstraintViolationMapper.class);

    @Override
    public Response toResponse(ConstraintViolationException ex) {
      Map<String, String> fields = new LinkedHashMap<>();
      for (ConstraintViolation<?> v : ex.getConstraintViolations()) {
        String path = v.getPropertyPath() == null ? "" : v.getPropertyPath().toString();
        int ultimoPunto = path.lastIndexOf('.');
        String campo = ultimoPunto >= 0 ? path.substring(ultimoPunto + 1) : path;
        fields.put(campo, v.getMessage());
      }
      return Response.status(400)
          .type("application/json")
          .entity(ApiErrorResponse.body(400, "Bad Request", "validation failed", fields))
          .build();
    }
  }

  /** Credenciales invalidas → 401 UNAUTHORIZED. */
  @Provider
  public static class AuthExceptionMapper implements ExceptionMapper<AuthException> {

    @Override
    public Response toResponse(AuthException ex) {
      return Response.status(401)
          .type("application/json")
          .entity(ApiErrorResponse.body(401, "Unauthorized", ex.getMessage()))
          .build();
    }
  }

  /** Ruta inexistente → 404 NOT FOUND. Sin este mapper el catch-all la convertia en 500. */
  @Provider
  public static class NotFoundMapper implements ExceptionMapper<NotFoundException> {

    @Override
    public Response toResponse(NotFoundException ex) {
      return Response.status(404)
          .type("application/json")
          .entity(ApiErrorResponse.body(404, "Not Found", "Not found"))
          .build();
    }
  }

  /** Metodo HTTP no soportado en una ruta existente → 405 METHOD NOT ALLOWED. */
  @Provider
  public static class MethodNotAllowedMapper implements ExceptionMapper<NotAllowedException> {

    @Override
    public Response toResponse(NotAllowedException ex) {
      return Response.status(405)
          .type("application/json")
          .entity(ApiErrorResponse.body(405, "Method Not Allowed", ex.getMessage()))
          .build();
    }
  }

  /** Cualquier otra excepcion → 500 sin filtrar stack trace al cliente. */
  @Provider
  public static class GenericMapper implements ExceptionMapper<Throwable> {

    private static final Logger log = Logger.getLogger(GenericMapper.class);

    @Override
    public Response toResponse(Throwable ex) {
      if (ex instanceof WebApplicationException wae) {
        Response original = wae.getResponse();
        int status = original != null ? original.getStatus() : 500;
        String reason =
            original != null && original.getStatusInfo() != null
                ? original.getStatusInfo().getReasonPhrase()
                : "Error";
        String message = ex.getMessage() != null ? ex.getMessage() : reason;
        return Response.status(status)
            .type("application/json")
            .entity(ApiErrorResponse.body(status, reason, message))
            .build();
      }
      log.error("Unhandled exception", ex);
      return Response.status(500)
          .type("application/json")
          .entity(ApiErrorResponse.body(500, "Internal Server Error", mensaje(ex)))
          .build();
    }

    /**
     * Mensaje del 500.
     *
     * <p>Por defecto es {@code internal error}: el cliente no debe ver la excepcion interna. Cuando
     * {@code app.error.details.enabled} esta prendido (solo en tests), el mensaje lleva el tipo y
     * el texto de la excepcion. Sin eso, un 500 en el CI es un fallo sin causa: el log del job
     * tiene el stack trace, pero el resumen del fallo dice solo "expected 200 but was 500" y no
     * deja diagnosticar nada sin ir a buscar el log a mano.
     */
    private static String mensaje(Throwable ex) {
      if (!detallesHabilitados()) {
        return "internal error";
      }
      String texto = ex.getMessage();
      return ex.getClass().getName() + ": " + (texto == null ? "(sin mensaje)" : texto);
    }

    /** El detalle del error se enciende solo cuando la configuracion lo pide. */
    private static boolean detallesHabilitados() {
      try {
        return ConfigProvider.getConfig()
            .getOptionalValue("app.error.details.enabled", Boolean.class)
            .orElse(Boolean.FALSE);
      } catch (RuntimeException e) {
        // El mapper de errores no puede fallar por leer la configuracion: si falla, el 500 se
        // devuelve sin detalle, que es exactamente el comportamiento por defecto.
        return false;
      }
    }
  }
}
