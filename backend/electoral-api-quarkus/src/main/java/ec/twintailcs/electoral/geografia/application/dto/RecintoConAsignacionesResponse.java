package ec.twintailcs.electoral.geografia.application.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/** Response DTO for a Recinto with its user assignments (observers + coordinators). */
public class RecintoConAsignacionesResponse {

  @JsonProperty("id")
  public UUID id;

  @JsonProperty("nombre")
  public String nombre;

  @JsonProperty("parroquiaId")
  public UUID parroquiaId;

  @JsonProperty("parroquiaNombre")
  public String parroquiaNombre;

  @JsonProperty("cantidadJrvs")
  public Integer cantidadJrvs;

  @JsonProperty("cantidadJrvsActuales")
  public Long cantidadJrvsActuales;

  @JsonProperty("estado")
  public String estado;

  @JsonProperty("observadorIds")
  public List<UUID> observadorIds;

  @JsonProperty("coordinadorIds")
  public List<UUID> coordinadorIds;

  @JsonProperty("fechaCreacion")
  public LocalDateTime fechaCreacion;

  @JsonProperty("fechaActualizacion")
  public LocalDateTime fechaActualizacion;

  // Constructors
  public RecintoConAsignacionesResponse() {}

  public RecintoConAsignacionesResponse(
      UUID id,
      String nombre,
      UUID parroquiaId,
      String parroquiaNombre,
      Integer cantidadJrvs,
      Long cantidadJrvsActuales,
      String estado,
      List<UUID> observadorIds,
      List<UUID> coordinadorIds,
      LocalDateTime fechaCreacion,
      LocalDateTime fechaActualizacion) {
    this.id = id;
    this.nombre = nombre;
    this.parroquiaId = parroquiaId;
    this.parroquiaNombre = parroquiaNombre;
    this.cantidadJrvs = cantidadJrvs;
    this.cantidadJrvsActuales = cantidadJrvsActuales;
    this.estado = estado;
    this.observadorIds = observadorIds;
    this.coordinadorIds = coordinadorIds;
    this.fechaCreacion = fechaCreacion;
    this.fechaActualizacion = fechaActualizacion;
  }

  @Override
  public String toString() {
    return "RecintoConAsignacionesResponse{"
        + "id="
        + id
        + ", nombre='"
        + nombre
        + '\''
        + ", parroquia="
        + parroquiaNombre
        + ", observadores="
        + (observadorIds != null ? observadorIds.size() : 0)
        + ", coordinadores="
        + (coordinadorIds != null ? coordinadorIds.size() : 0)
        + '}';
  }
}
