package ec.twintailcs.electoral.shared.web;

import ec.twintailcs.electoral.acta.domain.AccesoNoPermitidoException;
import ec.twintailcs.electoral.acta.domain.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.JrvCargoYaRegistradoException;
import ec.twintailcs.electoral.auth.domain.AuthException;
import ec.twintailcs.electoral.auth.domain.UsuarioInvalidoException;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.domain.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaNotFoundException;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Traduce excepciones a respuestas JSON consistentes.
 *
 * <p>PR γ: maneja {@link EvidenciaNotFoundException} (404), {@link AccessDeniedException} (403),
 * {@link ActaInvalidaException} (400), {@link ActaEstadoInvalidoException} (409) y {@link
 * JrvCargoYaRegistradoException} (409).
 *
 * <p>Se agrega para evitar que las excepciones de negocio caigan en el catch-all (500) y filtren
 * stack traces al cliente, y para que los tests puedan asertar el contrato JSON estándar.
 *
 * <p>Mapeo de exceptions a HTTP status: ver doc/adr/0002-exception-mapping.md.
 *
 * <p>Las excepciones de negocio no llevan (y no deben llevar) {@code @ResponseStatus} acopladas a
 * HTTP: tiran RuntimeException y este handler las clasifica.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

  private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

  @ExceptionHandler(EvidenciaNotFoundException.class)
  public ResponseEntity<Map<String, Object>> notFound(EvidenciaNotFoundException ex) {
    return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
  }

  /** EntityNotFoundException de JPA (e.g. JRV inexistente en IncidenciaService) → 404. */
  @ExceptionHandler(jakarta.persistence.EntityNotFoundException.class)
  public ResponseEntity<Map<String, Object>> entityNotFound(
      jakarta.persistence.EntityNotFoundException ex) {
    return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
  }

  @ExceptionHandler(EvidenciaInvalidaException.class)
  public ResponseEntity<Map<String, Object>> evidenciaInvalida(EvidenciaInvalidaException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  @ExceptionHandler(AuthException.class)
  public ResponseEntity<Map<String, Object>> authException(AuthException ex) {
    return body(HttpStatus.UNAUTHORIZED, ex.getMessage(), null);
  }

  @ExceptionHandler(UsuarioInvalidoException.class)
  public ResponseEntity<Map<String, Object>> usuarioInvalido(UsuarioInvalidoException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  @ExceptionHandler(ConfiguracionNotFoundException.class)
  public ResponseEntity<Map<String, Object>> configuracionNotFound(
      ConfiguracionNotFoundException ex) {
    return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
  }

  @ExceptionHandler(ConfiguracionConflictException.class)
  public ResponseEntity<Map<String, Object>> configuracionConflict(
      ConfiguracionConflictException ex) {
    return body(HttpStatus.CONFLICT, ex.getMessage(), null);
  }

  /** AccessDeniedException de @PreAuthorize -> 403 FORBIDDEN. */
  @ExceptionHandler(AccessDeniedException.class)
  public ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException ex) {
    return body(HttpStatus.FORBIDDEN, ex.getMessage(), null);
  }

  /** Parte multipart faltante o parámetro requerido ausente → 400 BAD REQUEST. */
  @ExceptionHandler({MissingServletRequestPartException.class, MissingServletRequestParameterException.class})
  public ResponseEntity<Map<String, Object>> missingPart(MissingServletRequestPartException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  @ExceptionHandler(AccesoNoPermitidoException.class)
  public ResponseEntity<Map<String, Object>> accesoNoPermitido(AccesoNoPermitidoException ex) {
    return body(HttpStatus.FORBIDDEN, ex.getMessage(), null);
  }

  /**
   * ActaInvalidaException -> 400 BAD_REQUEST. Contrato explícito: errores de validación de dominio
   * del módulo de actas (acta inexistente, votos que no cuadran, JRV/cargo/observer no encontrados,
   * candidato no encontrado al corregir) deben llegar al cliente como 400. El catch-all genérico
   * devolvería 500, ocultando el contrato detrás de un error de servidor.
   */
  @ExceptionHandler(ActaInvalidaException.class)
  public ResponseEntity<Map<String, Object>> actaInvalida(ActaInvalidaException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  @ExceptionHandler(MethodArgumentNotValidException.class)
  public ResponseEntity<Map<String, Object>> invalidArg(MethodArgumentNotValidException ex) {
    Map<String, String> fields = new HashMap<>();
    ex.getBindingResult()
        .getFieldErrors()
        .forEach(e -> fields.put(e.getField(), e.getDefaultMessage()));
    return body(HttpStatus.BAD_REQUEST, "validation failed", fields);
  }

  @ExceptionHandler(ConstraintViolationException.class)
  public ResponseEntity<Map<String, Object>> constraint(ConstraintViolationException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> tooLarge(MaxUploadSizeExceededException ex) {
    return body(HttpStatus.PAYLOAD_TOO_LARGE, "file exceeds max upload size", null);
  }

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> illegalArg(IllegalArgumentException ex) {
    return body(HttpStatus.BAD_REQUEST, ex.getMessage(), null);
  }

  /**
   * ActaEstadoInvalidoException -> 409 CONFLICT. Explicito: la anotacion @ResponseStatus de la
   * excepcion es ocultada por el catch-all generico de abajo. Mapeo explicito para preservar el
   * contrato HTTP.
   */
  @ExceptionHandler(ActaEstadoInvalidoException.class)
  public ResponseEntity<Map<String, Object>> estadoInvalido(ActaEstadoInvalidoException ex) {
    return body(HttpStatus.CONFLICT, ex.getMessage(), null);
  }

  /**
   * JrvCargoYaRegistradoException -> 409 CONFLICT. Misma razon que arriba: el catch-all
   * enmascara @ResponseStatus, asi que se mapea explicitamente.
   */
  @ExceptionHandler(JrvCargoYaRegistradoException.class)
  public ResponseEntity<Map<String, Object>> jrvCargoDuplicado(JrvCargoYaRegistradoException ex) {
    return body(HttpStatus.CONFLICT, ex.getMessage(), null);
  }

  @ExceptionHandler(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException.class)
  public ResponseEntity<Map<String, Object>> typeMismatch(
      org.springframework.web.method.annotation.MethodArgumentTypeMismatchException ex) {
    return body(HttpStatus.BAD_REQUEST, "invalid parameter format: " + ex.getName(), null);
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> generic(Exception ex) {
    log.error("Unhandled exception", ex);
    return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", null);
  }

  /**
   * Respeta el status code de las ResponseStatusException explícitas (ej. 404 de "Sesion no
   * encontrada"). Sin este handler, el genérico las convierte a 500.
   */
  @ExceptionHandler(ResponseStatusException.class)
  public ResponseEntity<Map<String, Object>> responseStatus(ResponseStatusException ex) {
    HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
    if (status == null) {
      status = HttpStatus.INTERNAL_SERVER_ERROR;
    }
    String message = ex.getReason() != null ? ex.getReason() : status.getReasonPhrase();
    return body(status, message, null);
  }

  private ResponseEntity<Map<String, Object>> body(
      HttpStatus status, String message, Map<String, String> fields) {
    Map<String, Object> body = new LinkedHashMap<>();
    body.put("timestamp", Instant.now().toString());
    body.put("status", status.value());
    body.put("error", status.getReasonPhrase());
    body.put("message", message);
    if (fields != null) body.put("fields", fields);
    return ResponseEntity.status(status).body(body);
  }
}
