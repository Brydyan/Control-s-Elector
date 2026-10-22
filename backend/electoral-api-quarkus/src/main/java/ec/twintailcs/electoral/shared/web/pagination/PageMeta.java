package ec.twintailcs.electoral.shared.web.pagination;

import io.quarkus.runtime.annotations.RegisterForReflection;

/** Metadatos de una pagina. Mismo shape que el backend Spring. */
@RegisterForReflection
public record PageMeta(int page, int size, long totalElements, int totalPages) {}
