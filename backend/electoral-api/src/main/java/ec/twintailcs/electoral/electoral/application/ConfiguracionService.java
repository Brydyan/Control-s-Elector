package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.application.usecase.ActualizarCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.CrearCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.EliminarCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ListarCandidatosUseCase;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.domain.PartidoRepository;
import ec.twintailcs.electoral.electoral.web.ConfiguracionConflictException;
import ec.twintailcs.electoral.electoral.web.ConfiguracionNotFoundException;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Logica de negocio para la configuracion preelectoral (MASTER): registro de Cargos, Partidos y
 * Candidatos.
 *
 * <p>Cada create/update/delete registra una entrada en AUDIT_LOG (patron identico a ActaService).
 * Los listados son de solo lectura y no generan auditoria.
 */
@Service
public class ConfiguracionService {

  private final CargoRepository cargoRepository;
  private final PartidoRepository partidoRepository;
  private final AuditLogService auditLogService;
  private final CrearCandidatoUseCase crearCandidatoUseCase;
  private final ActualizarCandidatoUseCase actualizarCandidatoUseCase;
  private final EliminarCandidatoUseCase eliminarCandidatoUseCase;
  private final ListarCandidatosUseCase listarCandidatosUseCase;

  public ConfiguracionService(
      CargoRepository cargoRepository,
      PartidoRepository partidoRepository,
      AuditLogService auditLogService,
      CrearCandidatoUseCase crearCandidatoUseCase,
      ActualizarCandidatoUseCase actualizarCandidatoUseCase,
      EliminarCandidatoUseCase eliminarCandidatoUseCase,
      ListarCandidatosUseCase listarCandidatosUseCase) {
    this.cargoRepository = cargoRepository;
    this.partidoRepository = partidoRepository;
    this.auditLogService = auditLogService;
    this.crearCandidatoUseCase = crearCandidatoUseCase;
    this.actualizarCandidatoUseCase = actualizarCandidatoUseCase;
    this.eliminarCandidatoUseCase = eliminarCandidatoUseCase;
    this.listarCandidatosUseCase = listarCandidatosUseCase;
  }

  // ─── Cargo ──────────────────────────────────────────────────────────────

  @Transactional
  public Cargo createCargo(CargoRequest dto, Long masterId, HttpServletRequest request) {
    Cargo cargo = new Cargo(dto.nombre(), dto.nivel(), dto.puestos());
    Cargo guardado = cargoRepository.save(cargo);

    auditLogService.registrar(
        masterId,
        "CREAR_CARGO",
        "cargo",
        guardado.getId(),
        "Cargo creado: " + dto.nombre(),
        request);
    return guardado;
  }

  @Transactional
  public Cargo updateCargo(Long id, CargoRequest dto, Long masterId, HttpServletRequest request) {
    Cargo cargo =
        cargoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + id));

    cargo.setNombre(dto.nombre());
    cargo.setNivel(dto.nivel());
    cargo.setPuestos(dto.puestos());
    Cargo actualizado = cargoRepository.save(cargo);

    auditLogService.registrar(
        masterId, "ACTUALIZAR_CARGO", "cargo", id, "Cargo actualizado: " + dto.nombre(), request);
    return actualizado;
  }

  @Transactional
  public void deleteCargo(Long id, Long masterId, HttpServletRequest request) {
    Cargo cargo =
        cargoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + id));

    cargoRepository.delete(cargo);

    auditLogService.registrar(
        masterId, "ELIMINAR_CARGO", "cargo", id, "Cargo eliminado: " + cargo.getNombre(), request);
  }

  public List<Cargo> listCargos() {
    return cargoRepository.findAll();
  }

  // ─── Partido ────────────────────────────────────────────────────────────

  @Transactional
  public Partido createPartido(PartidoRequest dto, Long masterId, HttpServletRequest request) {
    partidoRepository
        .findByNumeroLista(dto.numeroLista())
        .ifPresent(
            p -> {
              throw new ConfiguracionConflictException(
                  "Ya existe un partido con numeroLista " + dto.numeroLista());
            });

    Partido partido = new Partido(dto.nombre(), dto.siglas(), dto.numeroLista());
    Partido guardado = partidoRepository.save(partido);

    auditLogService.registrar(
        masterId,
        "CREAR_PARTIDO",
        "partido",
        guardado.getId(),
        "Partido creado: " + dto.siglas(),
        request);
    return guardado;
  }

  @Transactional
  public Partido updatePartido(
      Long id, PartidoRequest dto, Long masterId, HttpServletRequest request) {
    Partido partido =
        partidoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + id));

    partidoRepository
        .findByNumeroLista(dto.numeroLista())
        .filter(p -> !p.getId().equals(id))
        .ifPresent(
            p -> {
              throw new ConfiguracionConflictException(
                  "Ya existe un partido con numeroLista " + dto.numeroLista());
            });

    partido.setNombre(dto.nombre());
    partido.setSiglas(dto.siglas());
    partido.setNumeroLista(dto.numeroLista());
    Partido actualizado = partidoRepository.save(partido);

    auditLogService.registrar(
        masterId,
        "ACTUALIZAR_PARTIDO",
        "partido",
        id,
        "Partido actualizado: " + dto.siglas(),
        request);
    return actualizado;
  }

  @Transactional
  public void deletePartido(Long id, Long masterId, HttpServletRequest request) {
    Partido partido =
        partidoRepository
            .findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + id));

    partidoRepository.delete(partido);

    auditLogService.registrar(
        masterId,
        "ELIMINAR_PARTIDO",
        "partido",
        id,
        "Partido eliminado: " + partido.getSiglas(),
        request);
  }

  public List<Partido> listPartidos() {
    return partidoRepository.findAll();
  }

  // ─── Candidato ──────────────────────────────────────────────────────────

  public Candidato createCandidato(
      CandidatoRequest dto, Long masterId, HttpServletRequest request) {
    return crearCandidatoUseCase.execute(dto, masterId, request);
  }

  public Candidato updateCandidato(
      Long id, CandidatoRequest dto, Long masterId, HttpServletRequest request) {
    return actualizarCandidatoUseCase.execute(id, dto, masterId, request);
  }

  public void deleteCandidato(Long id, Long masterId, HttpServletRequest request) {
    eliminarCandidatoUseCase.execute(id, masterId, request);
  }

  public List<Candidato> listCandidatos(Long cargoId, Long partidoId) {
    return listarCandidatosUseCase.execute(cargoId, partidoId);
  }
}
