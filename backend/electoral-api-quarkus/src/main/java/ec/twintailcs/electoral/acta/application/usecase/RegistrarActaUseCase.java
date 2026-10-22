package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.application.ActaAuditService;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.domain.JrvCargoYaRegistradoException;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.PersistenceException;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Alta directa de un acta: se crea y se envia en un solo paso (estado ENVIADA).
 *
 * <p>Port exacto del {@code RegistrarActaUseCase} del backend Spring: mismas validaciones, mismo
 * bloqueo del par (JRV, cargo) y misma traduccion de la colision del indice unico a 409.
 */
@ApplicationScoped
public class RegistrarActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaAuditService audit;
  @Inject ValidacionActa validacion;

  /**
   * Registra el acta.
   *
   * @param dto datos del acta
   * @param observerId id interno del observer que registra
   * @param ipAddress IP del cliente
   */
  @Transactional
  public Acta execute(ActaRequestDto dto, Long observerId, String ipAddress) {
    if (!repository.existeJrv(dto.jrvId())) {
      throw new ActaInvalidaException("JRV no encontrada: " + dto.jrvId());
    }
    if (!repository.existeCargo(dto.cargoId())) {
      throw new ActaInvalidaException("Cargo no encontrado: " + dto.cargoId());
    }

    validarCargoHabilitadoEnJrv(dto.jrvId(), dto.cargoId());

    // El par (JRV, cargo) admite una sola acta activa: se bloquea la fila viva para que dos
    // observers que registran el mismo par a la vez no se pisen.
    repository
        .buscarActivaPorJrvYCargoConLock(dto.jrvId(), dto.cargoId())
        .ifPresent(
            existente -> {
              throw new JrvCargoYaRegistradoException(dto.jrvId(), dto.cargoId());
            });

    validarCandidatosParaCargo(dto);
    validarSumaVotos(dto);
    Acta acta = new Acta();
    acta.setJrvId(dto.jrvId());
    acta.setCargoId(dto.cargoId());
    acta.setRegistradaPorId(observerId);
    acta.setEstado(EstadoActa.ENVIADA);
    acta.setTotalSufragantes(dto.totalSufragantes());
    acta.setVotosBlancos(dto.votosBlancos());
    acta.setVotosNulos(dto.votosNulos());
    acta.validarDatosObligatorios();

    try {
      acta.persist();
      Acta.getEntityManager().flush();
    } catch (PersistenceException e) {
      // Indica que otro hilo gano la carrera: el indice unico uk_acta_activa detecto el duplicado.
      // Se traduce al error de dominio, no se propaga la excepcion de persistencia.
      throw new JrvCargoYaRegistradoException(dto.jrvId(), dto.cargoId());
    }

    persistirDetalles(acta.getId(), dto.detalles());

    audit.registrarConIp(
        observerId,
        "REGISTRO_ACTA",
        "acta",
        acta.getId(),
        "Acta registrada: JRV="
            + dto.jrvId()
            + ", cargo="
            + dto.cargoId()
            + ", sufragantes="
            + dto.totalSufragantes(),
        ipAddress);
    return acta;
  }

  /** El cargo tiene que estar habilitado (activo) en la JRV. */
  private void validarCargoHabilitadoEnJrv(Long jrvId, Long cargoId) {
    if (!repository.cargoHabilitadoEnJrv(jrvId, cargoId)) {
      throw new ActaInvalidaException("El cargo " + cargoId + " no está habilitado en la JRV " + jrvId);
    }
  }

  /** Los candidatos del detalle tienen que existir y pertenecer al cargo del acta. */
  private void validarCandidatosParaCargo(ActaRequestDto dto) {
    validacion.validarCandidatosParaCargo(dto.detalles(), dto.cargoId());
  }

  /** La suma de votos tiene que cerrar con el total de sufragantes. */
  private void validarSumaVotos(ActaRequestDto dto) {
    validacion.validarSumaVotos(
        dto.detalles(), dto.votosBlancos(), dto.votosNulos(), dto.totalSufragantes());
  }

  /** Persiste las lineas del acta. */
  private void persistirDetalles(Long actaId, List<ActaDetalleDto> detalles) {
    for (ActaDetalleDto detalleDto : detalles) {
      repository.persistirDetalle(actaId, detalleDto.candidatoId(), detalleDto.votos());
    }
  }
}
