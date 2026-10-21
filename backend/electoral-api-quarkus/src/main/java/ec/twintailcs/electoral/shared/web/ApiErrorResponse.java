package ec.twintailcs.electoral.shared.web;

import jakarta.ws.rs.core.Response;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Shape estandar del error del backend. Es el mismo que produce el
 * {@code GlobalExceptionHandler} del backend Spring: {@code {timestamp, status, error, message}}
 * y, en los errores de validacion de bean, {@code fields}.
 *
 * <p>La suite de regresion por contrato afirma estas claves, asi que cualquier cambio
 * de forma es un cambio de contrato.
 */
public final class ApiErrorResponse {

  private ApiErrorResponse() {}

  public static Map<String, Object> body(int status, String reasonPhrase, String message) {
    return body(status, reasonPhrase, message, null);
  }

  public static Map<String, Object> body(
      int status, String reasonPhrase, String message, Map<String, String> fields) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status);
    body.put("error", reasonPhrase);
    body.put("message", message);
    if (fields != null && !fields.isEmpty()) {
      body.put("fields", fields);
    }
    return body;
  }

  public static Response response(int status, String reasonPhrase, String message) {
    return Response.status(status)
        .type("application/json")
        .entity(body(status, reasonPhrase, message))
        .build();
  }
}
