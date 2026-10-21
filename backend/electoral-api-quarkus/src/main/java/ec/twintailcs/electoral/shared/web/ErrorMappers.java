package ec.twintailcs.electoral.shared.web;

import ec.twintailcs.electoral.auth.application.AuthException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.ws.rs.NotAllowedException;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import jakarta.ws.rs.ext.Provider;
import java.util.LinkedHashMap;
import java.util.Map;
import org.jboss.logging.Logger;

/**
 * Mapeo de excepciones al shape estandar de error.
 *
 * <p>La validacion de bean corre ANTES que la autorizacion (igual que en Spring): un body
 * invalido con un rol sin permiso devuelve 400, no 403.
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
  public static class MethodNotAllowedMapper
      implements ExceptionMapper<NotAllowedException> {

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
      log.error("Unhandled exception", ex);
      return Response.status(500)
          .type("application/json")
          .entity(ApiErrorResponse.body(500, "Internal Server Error", "internal error"))
          .build();
    }
  }
}
