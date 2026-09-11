package ec.twintailcs.electoral.acta.web;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

public class ActaDetalleDto {

  @NotNull(message = "candidatoId es obligatorio")
  private Long candidatoId;

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
