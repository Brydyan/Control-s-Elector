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
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Guarda o reanuda un borrador de acta (estado BORRADOR).
 *
 * <p>No valida la integridad electoral: el observer puede ir guardando mientras junta los datos. Solo
 * exige que el cargo este habilitado en la JRV y que el par (JRV, cargo) no tenga un acta activa de
 * otro observer.
 */
@ApplicationScoped
public class GuardarBorradorActaUseCase {

  @Inject ActaRepository repository;
  @Inject ActaAuditService audit;

  /**
   * Guarda el borrador.
   *
   * @param dto datos del acta
   * @param observerId id interno del observer que guarda
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

    // Si el observer ya tiene un borrador del par, se reanuda en vez de duplicar.
    Optional<Acta> borradorPropio =
        repository.buscarActivaPropia(dto.jrvId(), dto.cargoId(), observerId);

    Acta acta;
    boolean esNuevo;
    if (borradorPropio.isPresent()) {
      acta = borradorPropio.get();
      esNuevo = false;
    } else {
      // Hay un acta activa de otro observer en ese par: el indice unico uk_acta_activa
      // rechazaria el INSERT, asi que se responde 409 y no 500.
      repository
          .buscarActivaPorJrvYCargoConLock(dto.jrvId(), dto.cargoId())
          .ifPresent(
              existente -> {
                throw new JrvCargoYaRegistradoException(dto.jrvId(), dto.cargoId());
              });
      acta = new Acta();
      esNuevo = true;
    }

    acta.setJrvId(dto.jrvId());
    acta.setCargoId(dto.cargoId());
    acta.setRegistradaPorId(observerId);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setTotalSufragantes(dto.totalSufragantes());
    acta.setVotosBlancos(dto.votosBlancos());
    acta.setVotosNulos(dto.votosNulos());

    if (esNuevo) {
      acta.persist();
      Acta.getEntityManager().flush();
    } else {
      // El flush entre el delete y el insert es obligatorio: Hibernate ejecuta los INSERT de
      // ActaDetalle antes que los DELETE en el mismo flush y el UNIQUE (acta_id, candidato_id)
      // revienta con duplicate key.
      repository.borrarDetalles(acta.getId());
      Acta.getEntityManager().flush();
    }

    persistirDetalles(acta.getId(), dto.detalles());

    audit.registrarConIp(
        observerId,
        esNuevo ? "BORRADOR_CREADO" : "BORRADOR_ACTUALIZADO",
        "acta",
        acta.getId(),
        "Borrador guardado: JRV=" + dto.jrvId() + ", cargo=" + dto.cargoId(),
        ipAddress);
    return acta;
  }

  /** El cargo tiene que estar habilitado (activo) en la JRV. */
  private void validarCargoHabilitadoEnJrv(Long jrvId, Long cargoId) {
    if (!repository.cargoHabilitadoEnJrv(jrvId, cargoId)) {
      throw new ActaInvalidaException("El cargo " + cargoId + " no está habilitado en la JRV " + jrvId);
    }
  }

  /** Persiste las lineas del acta. */
  private void persistirDetalles(Long actaId, List<ActaDetalleDto> detalles) {
    for (ActaDetalleDto detalleDto : detalles) {
      repository.persistirDetalle(actaId, detalleDto.candidatoId(), detalleDto.votos());
    }
  }
}
