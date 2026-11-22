package ec.twintailcs.electoral.geografia.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.UUID;

/**
 * Request DTO for creating a Recinto with user assignments.
 *
 * <p>Includes: - Recinto data: nombre, ubicación (parroquiaId), cantidad de JRVs - Observadores: 5+
 * observers assigned to the recinto - Coordinadores: 0-5 coordinators assigned to the recinto
 */
public class CrearRecintoConAsignacionesRequest {

  @NotBlank(message = "Nombre del recinto es requerido")
  @Size(min = 3, max = 255, message = "Nombre debe tener entre 3 y 255 caracteres")
  @JsonProperty("nombre")
  public String nombre;

  @NotNull(message = "Parroquia es requerida")
  @JsonProperty("parroquiaId")
  public UUID parroquiaId;

  @NotNull(message = "Cantidad de JRVs es requerida")
  @Min(value = 1, message = "Cantidad de JRVs debe ser mayor a 0")
  @JsonProperty("cantidadJrvs")
  public Integer cantidadJrvs;

  @NotNull(message = "Lista de observadores es requerida")
  @Size(min = 5, message = "Mínimo 5 observadores requeridos")
  @JsonProperty("observadorIds")
  public List<UUID> observadorIds;

  @NotNull(message = "Lista de coordinadores es requerida")
  @Size(max = 5, message = "Máximo 5 coordinadores permitidos")
  @JsonProperty("coordinadorIds")
  public List<UUID> coordinadorIds;

  // Constructors
  public CrearRecintoConAsignacionesRequest() {}

  public CrearRecintoConAsignacionesRequest(
      String nombre,
      UUID parroquiaId,
      Integer cantidadJrvs,
      List<UUID> observadorIds,
      List<UUID> coordinadorIds) {
    this.nombre = nombre;
    this.parroquiaId = parroquiaId;
    this.cantidadJrvs = cantidadJrvs;
    this.observadorIds = observadorIds;
    this.coordinadorIds = coordinadorIds;
  }

  /** Validate no duplicates in observador list. */
  public boolean hasNoDuplicateObservadores() {
    return observadorIds.size() == observadorIds.stream().distinct().count();
  }

  /** Validate no duplicates in coordinador list. */
  public boolean hasNoDuplicateCoordinadores() {
    return coordinadorIds.size() == coordinadorIds.stream().distinct().count();
  }

  /** Check if no overlap between observadores and coordinadores (optional validation). */
  public boolean hasNoOverlapObserverCoordinator() {
    var coordSet = coordinadorIds.stream().collect(java.util.stream.Collectors.toSet());
    return observadorIds.stream().noneMatch(coordSet::contains);
  }

  @Override
  public String toString() {
    return "CrearRecintoConAsignacionesRequest{"
        + "nombre='"
        + nombre
        + '\''
        + ", parroquiaId="
        + parroquiaId
        + ", cantidadJrvs="
        + cantidadJrvs
        + ", observadores="
        + observadorIds.size()
        + ", coordinadores="
        + coordinadorIds.size()
        + '}';
  }
}
