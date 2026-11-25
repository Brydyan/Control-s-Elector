package ec.twintailcs.electoral.geografia.web.dto;

import io.quarkus.runtime.annotations.RegisterForReflection;
import jakarta.validation.constraints.NotNull;
import java.util.List;

@RegisterForReflection
public record AsignarJrvsRequest(@NotNull List<Long> jrvIds) {}
