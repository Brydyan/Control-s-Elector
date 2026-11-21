package ec.twintailcs.electoral.geografia.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.*;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Request DTO for editing a Recinto and its user assignments.
 *
 * <p>All fields are optional. Only provided fields will be updated. - If observadorIds is provided,
 * all old observers are replaced with the new list - If coordinadorIds is provided, all old
 * coordinators are replaced with the new list
 */
public class EditarRecintoConAsignacionesRequest {

  @Size(min = 3, max = 255, message = "Nombre debe tener entre 3 y 255 caracteres")
  @JsonProperty("nombre")
  public String nombre;

  @JsonProperty("parroquiaId")
  public UUID parroquiaId;

  @Min(value = 1, message = "Cantidad de JRVs debe ser mayor a 0")
  @JsonProperty("cantidadJrvs")
  public Integer cantidadJrvs;

  @Size(min = 5, message = "Mínimo 5 observadores requeridos si se actualiza la lista")
  @JsonProperty("observadorIds")
  public List<UUID> observadorIds;

  @Size(max = 5, message = "Máximo 5 coordinadores permitidos")
  @JsonProperty("coordinadorIds")
  public List<UUID> coordinadorIds;

  // Constructors
  public EditarRecintoConAsignacionesRequest() {}

  public EditarRecintoConAsignacionesRequest(
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

  /** Check if any field is provided (at least one update). */
  public boolean hasAnyChange() {
    return nombre != null
        || parroquiaId != null
        || cantidadJrvs != null
        || observadorIds != null
        || coordinadorIds != null;
  }

  /** Validate no duplicates in observador list (if provided). */
  public boolean hasNoDuplicateObservadores() {
    if (observadorIds == null) return true;
    return observadorIds.size() == observadorIds.stream().distinct().count();
  }

  /** Validate no duplicates in coordinador list (if provided). */
  public boolean hasNoDuplicateCoordinadores() {
    if (coordinadorIds == null) return true;
    return coordinadorIds.size() == coordinadorIds.stream().distinct().count();
  }

  /** Get observador list as optional. */
  public Optional<List<UUID>> getObservadorIds() {
    return Optional.ofNullable(observadorIds);
  }

  /** Get coordinador list as optional. */
  public Optional<List<UUID>> getCoordinadorIds() {
    return Optional.ofNullable(coordinadorIds);
  }

  @Override
  public String toString() {
    return "EditarRecintoConAsignacionesRequest{"
        + "nombre='"
        + nombre
        + '\''
        + ", parroquiaId="
        + parroquiaId
        + ", cantidadJrvs="
        + cantidadJrvs
        + ", observadores="
        + (observadorIds != null ? observadorIds.size() : "no-change")
        + ", coordinadores="
        + (coordinadorIds != null ? coordinadorIds.size() : "no-change")
        + '}';
  }
}
