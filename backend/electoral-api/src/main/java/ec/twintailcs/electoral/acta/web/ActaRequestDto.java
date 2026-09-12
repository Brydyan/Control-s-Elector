package ec.twintailcs.electoral.acta.web;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.util.List;

public class ActaRequestDto {

  @NotNull(message = "jrvId es obligatorio")
  private Long jrvId;

  @NotNull(message = "cargoId es obligatorio")
  private Long cargoId;

  @NotNull(message = "totalSufragantes es obligatorio")
  @Min(value = 0, message = "totalSufragantes no puede ser negativo")
  private Integer totalSufragantes;

  @NotNull(message = "votosBlancos es obligatorio")
  @Min(value = 0, message = "votosBlancos no puede ser negativo")
  private Integer votosBlancos;

  @NotNull(message = "votosNulos es obligatorio")
  @Min(value = 0, message = "votosNulos no puede ser negativo")
  private Integer votosNulos;

  @NotEmpty(message = "Debe haber al menos un candidato")
  @Valid
  private List<ActaDetalleDto> detalles;

  public ActaRequestDto() {}

  public ActaRequestDto(
      Long jrvId,
      Long cargoId,
      Integer totalSufragantes,
      Integer votosBlancos,
      Integer votosNulos,
      List<ActaDetalleDto> detalles) {
    this.jrvId = jrvId;
    this.cargoId = cargoId;
    this.totalSufragantes = totalSufragantes;
    this.votosBlancos = votosBlancos;
    this.votosNulos = votosNulos;
    this.detalles = detalles;
  }

  public Long getJrvId() {
    return jrvId;
  }

  public void setJrvId(Long jrvId) {
    this.jrvId = jrvId;
  }

  public Long getCargoId() {
    return cargoId;
  }

  public void setCargoId(Long cargoId) {
    this.cargoId = cargoId;
  }

  public Integer getTotalSufragantes() {
    return totalSufragantes;
  }

  public void setTotalSufragantes(Integer totalSufragantes) {
    this.totalSufragantes = totalSufragantes;
  }

  public Integer getVotosBlancos() {
    return votosBlancos;
  }

  public void setVotosBlancos(Integer votosBlancos) {
    this.votosBlancos = votosBlancos;
  }

  public Integer getVotosNulos() {
    return votosNulos;
  }

  public void setVotosNulos(Integer votosNulos) {
    this.votosNulos = votosNulos;
  }

  public List<ActaDetalleDto> getDetalles() {
    return detalles;
  }

  public void setDetalles(List<ActaDetalleDto> detalles) {
    this.detalles = detalles;
  }
}
