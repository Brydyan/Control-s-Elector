package ec.twintailcs.electoral.web.evidencia;

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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

/**
 * Traduce excepciones a respuestas JSON consistentes.
 *
 * Formato uniforme:
 *   { timestamp, status, error, message, fields? }
 *
 * Un solo lugar para revisar cuando algo falla. Los controllers no se
 * enteran de los detalles HTTP: tiran RuntimeException y este handler
 * las clasifica.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(EvidenciaNotFoundException.class)
    public ResponseEntity<Map<String, Object>> notFound(EvidenciaNotFoundException ex) {
        return body(HttpStatus.NOT_FOUND, ex.getMessage(), null);
    }

    @ExceptionHandler(EvidenciaAccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> denied(EvidenciaAccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> invalidArg(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new HashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(e ->
                fields.put(e.getField(), e.getDefaultMessage()));
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

    /** AccessDeniedException de @PreAuthorize -> 403 FORBIDDEN. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> accessDenied(AccessDeniedException ex) {
        return body(HttpStatus.FORBIDDEN, ex.getMessage(), null);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> generic(Exception ex) {
        log.error("Unhandled exception", ex);
        return body(HttpStatus.INTERNAL_SERVER_ERROR, "internal error", null);
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
