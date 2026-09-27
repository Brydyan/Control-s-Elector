package ec.twintailcs.electoral.acta.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Votos obtenidos por un candidato en el acta")
public class ActaDetalleDto {

  @Schema(description = "ID del candidato", example = "7")
  @NotNull(message = "candidatoId es obligatorio")
  private Long candidatoId;

  @Schema(description = "Numero de votos", example = "42")
  @NotNull(message = "votos es obligatorio")
  @Min(value = 0, message = "votos no puede ser negativo")
  private Integer votos;

  public ActaDetalleDto() {}

  public ActaDetalleDto(Long candidatoId, Integer votos) {
    this.candidatoId = candidatoId;
    this.votos = votos;
  }

  public Long getCandidatoId() {
    return candidatoId;
  }

  public void setCandidatoId(Long candidatoId) {
    this.candidatoId = candidatoId;
  }

  public Integer getVotos() {
    return votos;
  }

  public void setVotos(Integer votos) {
    this.votos = votos;
  }
}
