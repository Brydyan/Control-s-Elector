package ec.twintailcs.electoral.acta.web.dto;

import jakarta.validation.constraints.NotBlank;

public class DisputaDto {

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
