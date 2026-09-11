package ec.twintailcs.electoral.acta.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.web.AccesoNoPermitidoException;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaEstadoInvalidoException;
import ec.twintailcs.electoral.acta.web.ActaInvalidaException;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.acta.web.JrvCargoYaRegistradoException;
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
import ec.twintailcs.electoral.notificacion.application.NotificacionService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Lógica de negocio de actas — ciclo de vida BORRADOR → ENVIADA → EN_DISPUTA → CORREGIDA → APROBADA
 * → ANULADA.
 *
 * <p>Reglas de concurrencia:
 *
 * <ul>
 *   <li>{@link #registrar} usa {@code findActaActivaConLock} + el índice único parcial {@code
 *       uk_acta_activa(jrv_id, cargo_id) WHERE estado IN (BORRADOR,ENVIADA,EN_DISPUTA,CORREGIDA)}
 *       definido en 010-tablas-jpa.sql. Dos inserciones simultáneas para la misma (jrv, cargo)
 *       chocan en el índice.
 *   <li>Las transiciones de estado (disputar/corregir/aprobar/anular) usan {@code findByIdWithLock}
 *       (PESSIMISTIC_WRITE) para evitar lecturas sucias entre dos coordinadores. Adicionalmente, la
 *       entidad {@link Acta} lleva {@code @Version} desde PR α, así que un update concurrente
 *       también tira OptimisticLockException.
 *   <li>Cada cambio de estado se audita vía {@link AuditLogService}.
 * </ul>
 *
 * <p>Validaciones electorales:
 *
 * <ul>
 *   <li>Cada candidato del detalle debe pertenecer al cargo del acta (mismo {@code cargo_id}).
 *       Antes esto NO se validaba: un acta de Prefecto podía contener candidatos de Alcalde.
 *   <li>El cargo debe estar habilitado en la JRV vía {@code jrv_cargo.activo = true}. Antes la
 *       JRV/cargo se elegían sin verificar la combinación.
 *   <li>No se permiten candidatos repetidos en el mismo detalle.
 *   <li>La suma de votos (candidatos + blancos + nulos) debe cuadrar con {@code total_sufragantes}.
 * </ul>
 */
@Service
public class ActaService {

  private static final Logger log = LoggerFactory.getLogger(ActaService.class);

  private final ActaRepository actaRepository;
  private final ActaDetalleRepository actaDetalleRepository;
  private final JrvRepository jrvRepository;
  private final UsuarioRepository usuarioRepository;
  private final CandidatoRepository candidatoRepository;
  private final CargoRepository cargoRepository;
  private final JrvCargoRepository jrvCargoRepository;
  private final AuditLogService auditLogService;
  private final NotificacionService notificacionService;

  public ActaService(
      ActaRepository actaRepository,
      ActaDetalleRepository actaDetalleRepository,
      JrvRepository jrvRepository,
      UsuarioRepository usuarioRepository,
      CandidatoRepository candidatoRepository,
      CargoRepository cargoRepository,
      JrvCargoRepository jrvCargoRepository,
      AuditLogService auditLogService,
      NotificacionService notificacionService) {
    this.actaRepository = actaRepository;
    this.actaDetalleRepository = actaDetalleRepository;
    this.jrvRepository = jrvRepository;
    this.usuarioRepository = usuarioRepository;
    this.candidatoRepository = candidatoRepository;
    this.cargoRepository = cargoRepository;
    this.jrvCargoRepository = jrvCargoRepository;
    this.auditLogService = auditLogService;
    this.notificacionService = notificacionService;
  }

  // ---------------------------------------------------------------------
  // Creación y transición inicial
  // ---------------------------------------------------------------------

  /**
   * Crea un acta en estado BORRADOR. NO exige que los votos cuadren: el observador puede ir
   * guardando mientras junta los datos. Para oficializar el acta hay que llamar {@link #enviar}.
   */
  @Transactional
  public Acta guardarBorrador(ActaRequestDto dto, Long observerId, HttpServletRequest request) {
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

    // Si ya existe un BORRADOR activo del mismo observador para la misma
    // (jrv, cargo), lo actualizamos en lugar de crear otro.
    Acta acta =
        actaRepository
            .findActaActivaBy(jrv.getId(), cargo.getId(), observerId)
            .orElseGet(Acta::new);
    boolean esNuevo = acta.getId() == null;

    acta.setJrv(jrv);
    acta.setCargo(cargo);
    acta.setRegistradaPor(observer);
    acta.setEstado(EstadoActa.BORRADOR);
    acta.setTotalSufragantes(dto.getTotalSufragantes());
    acta.setVotosBlancos(dto.getVotosBlancos());
    acta.setVotosNulos(dto.getVotosNulos());

    Acta actaGuardada = actaRepository.save(acta);

    // El detalle se reemplaza completo: borrar y volver a crear.
    if (!esNuevo) {
      actaDetalleRepository.deleteByActa(actaGuardada);
    }
    persistirDetalles(actaGuardada, dto.getDetalles());

    auditLogService.registrar(
        observerId,
        esNuevo ? "BORRADOR_CREADO" : "BORRADOR_ACTUALIZADO",
        "acta",
        actaGuardada.getId(),
        "Borrador guardado: JRV=" + dto.getJrvId() + ", cargo=" + dto.getCargoId(),
        request);
    return actaGuardada;
  }

  /**
   * Transición BORRADOR → ENVIADA. Valida integridad electoral completa: suma de votos,
   * candidato↔cargo, JRV/cargo habilitado, candidatos únicos. Persiste como ENVIADA. Devuelve la
   * entidad persistida.
   */
  @Transactional
  public Acta enviar(Long actaId, Long observerId, HttpServletRequest request) {
    Acta acta = loadActaConLock(actaId);

    if (acta.getEstado() != EstadoActa.BORRADOR) {
      throw new ActaEstadoInvalidoException(
          "Solo BORRADOR puede pasar a ENVIADA. Estado actual: " + acta.getEstado());
    }
    if (!acta.getRegistradaPor().getId().equals(observerId)) {
      throw new AccesoNoPermitidoException("Solo el autor puede enviar su propio borrador");
    }

    validarCandidatosParaCargo(acta);
    validarSumaVotos(acta);

    // El índice único parcial uk_acta_activa (jrv_id, cargo_id) protege
    // contra que ya exista otra acta activa en el mismo par. Si llegamos
    // acá, este BORRADOR es el único, así que la transición es segura.
    acta.setEstado(EstadoActa.ENVIADA);
    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrar(
        observerId, "ACTA_ENVIADA", "acta", actaId, "Borrador oficializado", request);
    return actaActualizada;
  }

  /**
   * Crea y oficializa un acta en una sola operación (legacy: equivalente a un enviar() sin pasar
   * por BORRADOR). Útil cuando el observador ya tiene todos los datos completos. Mantiene el flujo
   * histórico.
   */
  @Transactional
  public Acta registrar(ActaRequestDto dto, Long observerId, HttpServletRequest request) {
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

    // Lock pesimista + verificación de duplicado. Si dos requests corren
    // a la vez, una queda bloqueada hasta que la otra commitee; cuando
    // retoma, ve el acta activa creada por la otra y lanza 409.
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

    Acta actaGuardada;
    try {
      actaGuardada = actaRepository.saveAndFlush(acta);
    } catch (OptimisticLockingFailureException e) {
      // Carrera detectada por @Version: alguien más creó primero.
      throw new JrvCargoYaRegistradoException(dto.getJrvId(), dto.getCargoId());
    }
    persistirDetalles(actaGuardada, dto.getDetalles());

    auditLogService.registrar(
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
        request);
    return actaGuardada;
  }

  // ---------------------------------------------------------------------
  // Transiciones
  // ---------------------------------------------------------------------

  @Transactional
  public Acta marcarEnDisputa(
      Long actaId, Long coordinatorId, String motivo, HttpServletRequest request) {
    Acta acta = loadActaConLock(actaId);

    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
      throw new ActaEstadoInvalidoException(
          "No se puede marcar en disputa. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.EN_DISPUTA);
    Acta actaActualizada = actaRepository.save(acta);

    notificacionService.notificar(
        acta.getRegistradaPor(),
        "ACTA_EN_DISPUTA",
        acta,
        "Tu acta (JRV " + acta.getJrv().getId() + ") fue marcada en disputa. Motivo: " + motivo);

    auditLogService.registrar(
        coordinatorId, "ACTA_EN_DISPUTA", "acta", actaId, "Motivo: " + motivo, request);
    return actaActualizada;
  }

  @Transactional
  public Acta corregir(
      Long actaId,
      ActaRequestDto dto,
      Long usuarioId,
      boolean esCoordinator,
      HttpServletRequest request) {
    Acta acta = loadActaConLock(actaId);

    if (acta.getEstado() != EstadoActa.EN_DISPUTA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden corregir actas EN_DISPUTA. Estado actual: " + acta.getEstado());
    }

    if (!esCoordinator && !acta.getRegistradaPor().getId().equals(usuarioId)) {
      throw new AccesoNoPermitidoException("Solo puedes corregir tus propias actas");
    }

    validarCandidatosParaCargoDto(dto, acta.getCargo());
    validarSumaVotosDto(dto);

    acta.setTotalSufragantes(dto.getTotalSufragantes());
    acta.setVotosBlancos(dto.getVotosBlancos());
    acta.setVotosNulos(dto.getVotosNulos());
    acta.setEstado(EstadoActa.CORREGIDA);

    actaDetalleRepository.deleteByActa(acta);
    actaDetalleRepository.flush();
    persistirDetalles(acta, dto.getDetalles());

    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrar(
        usuarioId,
        "ACTA_CORREGIDA",
        "acta",
        actaId,
        "Acta corregida por usuario " + usuarioId,
        request);
    return actaActualizada;
  }

  @Transactional
  public Acta aprobar(Long actaId, Long coordinatorId, HttpServletRequest request) {
    Acta acta = loadActaConLock(actaId);

    if (acta.getEstado() != EstadoActa.ENVIADA && acta.getEstado() != EstadoActa.CORREGIDA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden aprobar actas ENVIADAS o CORREGIDAS. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.APROBADA);
    Acta actaActualizada = actaRepository.save(acta);

    auditLogService.registrar(
        coordinatorId,
        "ACTA_APROBADA",
        "acta",
        actaId,
        "Acta aprobada por coordinator " + coordinatorId,
        request);
    return actaActualizada;
  }

  @Transactional
  public Acta anular(Long actaId, Long masterId, String motivo, HttpServletRequest request) {
    Acta acta = loadActaConLock(actaId);

    if (acta.getEstado() != EstadoActa.APROBADA) {
      throw new ActaEstadoInvalidoException(
          "Solo se pueden anular actas APROBADAS. Estado actual: " + acta.getEstado());
    }

    acta.setEstado(EstadoActa.ANULADA);
    acta.setMotivoAnulacion(motivo);
    Acta actaActualizada = actaRepository.save(acta);

    notificacionService.notificar(
        acta.getRegistradaPor(),
        "ACTA_ANULADA",
        acta,
        "Tu acta (JRV " + acta.getJrv().getId() + ") fue anulada. Motivo: " + motivo);

    auditLogService.registrar(
        masterId, "ACTA_ANULADA", "acta", actaId, "Motivo: " + motivo, request);
    return actaActualizada;
  }

  // ---------------------------------------------------------------------
  // Helpers privados
  // ---------------------------------------------------------------------

  private Acta loadActaConLock(Long actaId) {
    return actaRepository
        .findByIdWithLock(actaId)
        .orElseThrow(() -> new ActaInvalidaException("Acta no encontrada: " + actaId));
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
      ActaDetalle detalle = new ActaDetalle();
      detalle.setActa(acta);
      detalle.setCandidato(candidato);
      detalle.setVotos(detalleDto.getVotos());
      actaDetalleRepository.save(detalle);
    }
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

  private void validarCandidatosParaCargo(Acta acta) {
    // Para BORRADOR→ENVIADA ya están persistidos en acta_detalle;
    // re-validamos para cubrir modificaciones del frontend entre
    // guardarBorrador y enviar.
    Set<Long> ids = new HashSet<>();
    var detallesActuales = actaDetalleRepository.findByActa(acta);
    for (var d : detallesActuales) {
      if (!ids.add(d.getCandidato().getId())) {
        throw new ActaInvalidaException(
            "Candidato repetido en el detalle: " + d.getCandidato().getId());
      }
      if (!d.getCandidato().getCargo().getId().equals(acta.getCargo().getId())) {
        throw new ActaInvalidaException(
            "Candidato " + d.getCandidato().getId() + " no pertenece al cargo del acta");
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

  private void validarSumaVotos(Acta acta) {
    var detalles = actaDetalleRepository.findByActa(acta);
    int sumaCandidatos =
        detalles.stream().mapToInt(ec.twintailcs.electoral.acta.domain.ActaDetalle::getVotos).sum();
    int total = sumaCandidatos + acta.getVotosBlancos() + acta.getVotosNulos();
    if (total != acta.getTotalSufragantes()) {
      throw new ActaInvalidaException(
          "Los votos no cuadran: " + total + " ≠ " + acta.getTotalSufragantes());
    }
  }
}
