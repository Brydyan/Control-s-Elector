package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Validaciones de integridad electoral que comparten el alta y la correccion de un acta.
 *
 * <p>El backend Spring repite estas comprobaciones en {@code RegistrarActaUseCase} y en {@code
 * CorregirActaUseCase}; aca viven en un solo lugar para que las dos rutas no puedan divergir. Los
 * mensajes son los mismos que los de alla: el frontend los muestra al observer.
 */
@ApplicationScoped
public class ValidacionActa {

  @Inject ActaRepository repository;

  /**
   * Los candidatos del detalle tienen que existir y pertenecer al cargo del acta.
   *
   * @param detalles lineas del acta
   * @param cargoId cargo del acta
   */
  public void validarCandidatosParaCargo(List<ActaDetalleDto> detalles, Long cargoId) {
    Set<Long> ids = new HashSet<>();
    List<Long> idsCandidatos = new ArrayList<>();
    for (ActaDetalleDto detalle : detalles) {
      idsCandidatos.add(detalle.candidatoId());
    }
    Map<Long, Long> cargoPorCandidato = repository.cargosDeCandidatos(idsCandidatos);
    for (ActaDetalleDto detalle : detalles) {
      if (!ids.add(detalle.candidatoId())) {
        throw new ActaInvalidaException("Candidato repetido en el detalle: " + detalle.candidatoId());
      }
      Long cargoDelCandidato = cargoPorCandidato.get(detalle.candidatoId());
      if (cargoDelCandidato == null) {
        throw new ActaInvalidaException("Candidato no encontrado: " + detalle.candidatoId());
      }
      if (!cargoDelCandidato.equals(cargoId)) {
        throw new ActaInvalidaException(
            "Candidato "
                + detalle.candidatoId()
                + " pertenece al cargo "
                + cargoDelCandidato
                + " pero el acta es del cargo "
                + cargoId);
      }
    }
  }

  /**
   * La suma de votos tiene que cerrar con el total de sufragantes.
   *
   * @param detalles lineas del acta
   * @param votosBlancos votos en blanco
   * @param votosNulos votos nulos
   * @param totalSufragantes total de sufragantes de la JRV
   */
  public void validarSumaVotos(
      List<ActaDetalleDto> detalles,
      Integer votosBlancos,
      Integer votosNulos,
      Integer totalSufragantes) {
    int sumaCandidatos = detalles.stream().mapToInt(ActaDetalleDto::votos).sum();
    int total = sumaCandidatos + votosBlancos + votosNulos;
    if (total != totalSufragantes) {
      throw new ActaInvalidaException(
          "Los votos no cuadran. Candidatos="
              + sumaCandidatos
              + " + Blancos="
              + votosBlancos
              + " + Nulos="
              + votosNulos
              + " = "
              + total
              + " ≠ "
              + totalSufragantes
              + " (total sufragantes)");
    }
  }
}
