package ec.twintailcs.electoral.acta.web.dto.mapper;

import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.HistorialActaDto;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Mapeo entre las filas de proyeccion del repositorio y los DTOs de la API.
 *
 * <p>En el backend Spring esto lo hace MapStruct (mapeo por nombre) con un parche explicito para el
 * subtotal de votos: el listado plano deja {@code totalVotosCandidatos} en {@code null} y solo las
 * respuestas del coordinator (y el listado paginado) lo rellenan. Aca el mapeo es a mano, como en el
 * modulo de auth y en el de electoral.
 */
@ApplicationScoped
public class ActaMapper {

  /**
   * DTO de un acta sin el subtotal de votos por candidato.
   *
   * <p>Es el shape que devuelven los endpoints del observer: paridad exacta con el mapper del
   * backend Spring, que deja ese campo en {@code null}.
   */
  public ActaResponseDto toDtoSinSubtotal(ActaRepository.ProyeccionActa fila) {
    if (fila == null) {
      return null;
    }
    return new ActaResponseDto(
        fila.id(),
        fila.jrvId(),
        fila.cargoId(),
        fila.registradaPorId(),
        fila.estado(),
        fila.totalSufragantes(),
        fila.votosBlancos(),
        fila.votosNulos(),
        fila.fechaRegistro(),
        fila.motivoAnulacion(),
        fila.nombreCargo(),
        fila.numeroJrv(),
        null);
  }

  /**
   * DTO de un acta con el subtotal de votos por candidato.
   *
   * <p>Lo usan el listado paginado del coordinator y las respuestas de sus transiciones.
   */
  public ActaResponseDto toDto(ActaRepository.ProyeccionActa fila) {
    if (fila == null) {
      return null;
    }
    return new ActaResponseDto(
        fila.id(),
        fila.jrvId(),
        fila.cargoId(),
        fila.registradaPorId(),
        fila.estado(),
        fila.totalSufragantes(),
        fila.votosBlancos(),
        fila.votosNulos(),
        fila.fechaRegistro(),
        fila.motivoAnulacion(),
        fila.nombreCargo(),
        fila.numeroJrv(),
        fila.totalVotosCandidatos());
  }

  /** DTO de una linea del acta (votos de un candidato). */
  public ActaDetalleDto toDetalleDto(ActaRepository.FilaDetalle fila) {
    return new ActaDetalleDto(fila.candidatoId(), fila.votos());
  }

  /** DTO de una entrada del historial del acta. */
  public HistorialActaDto toHistorialDto(ActaRepository.FilaHistorial fila) {
    String nombre = null;
    if (fila.nombreUsuario() != null || fila.apellidoUsuario() != null) {
      nombre = (fila.nombreUsuario() == null ? "" : fila.nombreUsuario())
          + " "
          + (fila.apellidoUsuario() == null ? "" : fila.apellidoUsuario());
    }
    return new HistorialActaDto(
        fila.id(),
        fila.accion(),
        fila.timestamp(),
        nombre,
        fila.rolUsuario(),
        fila.ipAddress(),
        fila.detalle());
  }
}
