package ec.twintailcs.electoral.acta.web.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;

@Schema(description = "Datos de anulacion de un acta APROBADA (solo MASTER)")
public class AnulacionDto {

  @Schema(description = "Motivo de la anulacion", example = "Datos incorrectos verificados")
  @NotBlank(message = "El motivo de anulacion es obligatorio")
  private String motivo;

  public AnulacionDto() {}

  public String getMotivo() {
    return motivo;
  }

  public void setMotivo(String motivo) {
    this.motivo = motivo;
  }
}
