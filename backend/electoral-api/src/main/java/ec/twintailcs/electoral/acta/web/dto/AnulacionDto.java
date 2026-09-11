package ec.twintailcs.electoral.acta.web.dto;

import jakarta.validation.constraints.NotBlank;

public class AnulacionDto {

  @NotBlank(message = "El motivo de anulación es obligatorio")
  private String motivo;

  public AnulacionDto() {}

  public String getMotivo() {
    return motivo;
  }

  public void setMotivo(String motivo) {
    this.motivo = motivo;
  }
}
