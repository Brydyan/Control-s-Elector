package ec.twintailcs.electoral.web.evidencia;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Metadata del upload. El archivo viaja como {@code multipart/form-data}
 * en el mismo request, no aca.
 *
 * Bean Validation valida esto antes de tocar el service. Si falla,
 * MethodArgumentNotValidException se mapea a 400 via @ControllerAdvice.
 */
public record EvidenciaUploadRequest(
        @NotNull @Positive Long jrvId,
        @NotNull @Positive Long cargoId
) {}
