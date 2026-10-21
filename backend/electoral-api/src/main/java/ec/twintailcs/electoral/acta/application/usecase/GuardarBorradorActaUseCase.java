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
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GuardarBorradorActaUseCase {

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final JrvRepository jrvRepository;
  private final UsuarioRepository usuarioRepository;
  private final CandidatoRepository candidatoRepository;
  private final CargoRepository cargoRepository;
  private final JrvCargoRepository jrvCargoRepository;
  private final AuditLogService auditLogService;

  public GuardarBorradorActaUseCase(
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

    // El par (JRV, cargo) admite una sola acta activa. Si el observer no tiene borrador propio
    // pero hay un acta activa de OTRO observer en ese par, el índice parcial uk_acta_activa
    // revienta el INSERT con DataIntegrityViolationException y el endpoint devolvía 500 en vez
    // de 409. Mismo criterio que RegistrarActaUseCase.
    Optional<Acta> borradorPropio =
        actaRepository.findActaActivaBy(jrv.getId(), cargo.getId(), observerId);

    Acta acta;
    if (borradorPropio.isPresent()) {
      acta = borradorPropio.get();
    } else {
      actaRepository
          .findActaActivaConLock(jrv, cargo)
          .ifPresent(
              existente -> {
                throw new JrvCargoYaRegistradoException(dto.getJrvId(), dto.getCargoId());
              });
      acta = Acta.nuevoBorrador();
    }
    boolean esNuevo = acta.getId() == null;

    acta.setJrv(jrv);
    acta.setCargo(cargo);
    acta.setRegistradaPor(observer);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setTotalSufragantes(dto.getTotalSufragantes());
    acta.setVotosBlancos(dto.getVotosBlancos());
    acta.setVotosNulos(dto.getVotosNulos());

    Acta actaGuardada = actaRepository.save(acta);

    if (!esNuevo) {
      // El flush entre el delete y el insert es obligatorio: Hibernate ejecuta los INSERT de
      // ActaDetalle antes que los DELETE en el mismo flush (orden del ActionQueue) y el UNIQUE
      // (acta_id, candidato_id) revienta con duplicate key. Mismo patrón que CorregirActaUseCase.
      actaDetalleRepository.deleteByActa(actaGuardada);
      actaDetalleRepository.flush();
    }
    persistirDetalles(actaGuardada, dto.getDetalles());

    auditLogService.registrarConIp(
        observerId,
        esNuevo ? "BORRADOR_CREADO" : "BORRADOR_ACTUALIZADO",
        "acta",
        actaGuardada.getId(),
        "Borrador guardado: JRV=" + dto.getJrvId() + ", cargo=" + dto.getCargoId(),
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
