package ec.twintailcs.electoral.acta.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Datos para marcar un acta en disputa")
public class DisputaDto {

  @Schema(description = "Motivo de la disputa", example = "Inconsistencia en conteo de votos")
  @NotBlank(message = "El motivo es obligatorio")
  private String motivo;

  public DisputaDto() {}

  public String getMotivo() {
    return motivo;
  }

  public void setMotivo(String motivo) {
    this.motivo = motivo;
  }
}
