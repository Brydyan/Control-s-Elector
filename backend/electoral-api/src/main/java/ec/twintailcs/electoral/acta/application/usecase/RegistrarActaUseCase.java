package ec.twintailcs.electoral.acta.application.usecase;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaInvalidaException;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.domain.JrvCargoYaRegistradoException;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.JrvCargoRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class RegistrarActaUseCase {

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final JrvRepository jrvRepository;
  private final UsuarioRepository usuarioRepository;
  private final CandidatoRepository candidatoRepository;
  private final CargoRepository cargoRepository;
  private final JrvCargoRepository jrvCargoRepository;
  private final AuditLogService auditLogService;

  public RegistrarActaUseCase(
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      JrvRepository jrvRepository,
      UsuarioRepository usuarioRepository,
      CandidatoRepository candidatoRepository,
      CargoRepository cargoRepository,
      JrvCargoRepository jrvCargoRepository,
      AuditLogService auditLogService) {
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.jrvRepository = jrvRepository;
    this.usuarioRepository = usuarioRepository;
    this.candidatoRepository = candidatoRepository;
    this.cargoRepository = cargoRepository;
    this.jrvCargoRepository = jrvCargoRepository;
    this.auditLogService = auditLogService;
  }

  @Transactional
  public Acta execute(ActaRequestDto dto, Long observerId, String ipAddress) {
    Jrv jrv =
        jrvRepository
            .findById(dto.getJrvId())
            .orElseThrow(() -> new ActaInvalidaException("JRV no encontrada: " + dto.getJrvId()));
    Cargo cargo =
        cargoRepository
            .findById(dto.getCargoId())
            .orElseThrow(
                () -> new ActaInvalidaException("Cargo no encontrado: " + dto.getCargoId()));
    Usuario observer =
        usuarioRepository
            .findById(observerId)
            .orElseThrow(() -> new ActaInvalidaException("Observer no encontrado"));

    validarCargoHabilitadoEnJrv(jrv.getId(), cargo.getId());

    actaRepository
        .findActaActivaConLock(jrv, cargo)
        .ifPresent(
            existente -> {
              throw new JrvCargoYaRegistradoException(dto.getJrvId(), dto.getCargoId());
            });

    validarCandidatosParaCargoDto(dto, cargo);
    validarSumaVotosDto(dto);

    Acta acta = new Acta();
    acta.setJrv(jrv);
    acta.setCargo(cargo);
    acta.setRegistradaPor(observer);
    acta.setEstado(EstadoActa.ENVIADA);
    acta.setTotalSufragantes(dto.getTotalSufragantes());
    acta.setVotosBlancos(dto.getVotosBlancos());
    acta.setVotosNulos(dto.getVotosNulos());
    acta.validarDatosObligatorios();

    Acta actaGuardada;
    try {
      actaGuardada = actaRepository.saveAndFlush(acta);
    } catch (OptimisticLockingFailureException e) {
      throw new JrvCargoYaRegistradoException(dto.getJrvId(), dto.getCargoId());
    }
    persistirDetalles(actaGuardada, dto.getDetalles());

    auditLogService.registrarConIp(
        observerId,
        "REGISTRO_ACTA",
        "acta",
        actaGuardada.getId(),
        "Acta registrada: JRV="
            + dto.getJrvId()
            + ", cargo="
            + dto.getCargoId()
            + ", sufragantes="
            + dto.getTotalSufragantes(),
        ipAddress);
    return actaGuardada;
  }

  private void validarCargoHabilitadoEnJrv(Long jrvId, Long cargoId) {
    boolean habilitado =
        !jrvCargoRepository.findByJrvIdAndCargoId(jrvId, cargoId).isEmpty()
            && jrvCargoRepository.findByJrvIdAndActivo(jrvId, true).stream()
                .anyMatch(jc -> jc.getCargo().getId().equals(cargoId));
    if (!habilitado) {
      throw new ActaInvalidaException(
          "El cargo " + cargoId + " no está habilitado en la JRV " + jrvId);
    }
  }

  private void validarCandidatosParaCargoDto(ActaRequestDto dto, Cargo cargo) {
    Set<Long> ids = new HashSet<>();
    for (ActaDetalleDto detalle : dto.getDetalles()) {
      if (!ids.add(detalle.getCandidatoId())) {
        throw new ActaInvalidaException(
            "Candidato repetido en el detalle: " + detalle.getCandidatoId());
      }
      Candidato candidato =
          candidatoRepository
              .findById(detalle.getCandidatoId())
              .orElseThrow(
                  () ->
                      new ActaInvalidaException(
                          "Candidato no encontrado: " + detalle.getCandidatoId()));
      if (!candidato.getCargo().getId().equals(cargo.getId())) {
        throw new ActaInvalidaException(
            "Candidato "
                + candidato.getId()
                + " pertenece al cargo "
                + candidato.getCargo().getId()
                + " pero el acta es del cargo "
                + cargo.getId());
      }
    }
  }

  private void validarSumaVotosDto(ActaRequestDto dto) {
    int sumaCandidatos = dto.getDetalles().stream().mapToInt(ActaDetalleDto::getVotos).sum();
    int total = sumaCandidatos + dto.getVotosBlancos() + dto.getVotosNulos();
    if (total != dto.getTotalSufragantes()) {
      throw new ActaInvalidaException(
          "Los votos no cuadran. Candidatos="
              + sumaCandidatos
              + " + Blancos="
              + dto.getVotosBlancos()
              + " + Nulos="
              + dto.getVotosNulos()
              + " = "
              + total
              + " ≠ "
              + dto.getTotalSufragantes()
              + " (total sufragantes)");
    }
  }

  private void persistirDetalles(Acta acta, List<ActaDetalleDto> detalles) {
    for (ActaDetalleDto detalleDto : detalles) {
      Candidato candidato =
          candidatoRepository
              .findById(detalleDto.getCandidatoId())
              .orElseThrow(
                  () ->
                      new ActaInvalidaException(
                          "Candidato no encontrado: " + detalleDto.getCandidatoId()));
      ActaDetalle detalle = new ActaDetalle(acta, candidato, detalleDto.getVotos());
      actaDetalleRepository.save(detalle);
    }
  }
}
