package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotEmpty;
import java.util.List;

/** Petición para eliminar múltiples JRVs en lote. */
@RegisterForReflection
public record EliminarJrvsBatchRequest(@NotEmpty List<Long> jrvIds) {}
