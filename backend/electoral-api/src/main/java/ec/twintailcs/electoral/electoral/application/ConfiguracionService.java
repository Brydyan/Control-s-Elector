package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
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
 * Logica de negocio para la configuracion preelectoral (MASTER):
 * registro de Cargos, Partidos y Candidatos.
 *
 * Cada create/update/delete registra una entrada en AUDIT_LOG
 * (patron identico a ActaService). Los listados son de solo lectura
 * y no generan auditoria.
 */
@Service
public class ConfiguracionService {

    private final CargoRepository cargoRepository;
    private final PartidoRepository partidoRepository;
    private final CandidatoRepository candidatoRepository;
    private final AuditLogService auditLogService;

    public ConfiguracionService(CargoRepository cargoRepository,
                                PartidoRepository partidoRepository,
                                CandidatoRepository candidatoRepository,
                                AuditLogService auditLogService) {
        this.cargoRepository = cargoRepository;
        this.partidoRepository = partidoRepository;
        this.candidatoRepository = candidatoRepository;
        this.auditLogService = auditLogService;
    }

    // ─── Cargo ──────────────────────────────────────────────────────────────

    @Transactional
    public Cargo createCargo(CargoRequest dto, Long masterId, HttpServletRequest request) {
        Cargo cargo = new Cargo(dto.nombre(), dto.nivel(), dto.puestos());
        Cargo guardado = cargoRepository.save(cargo);

        auditLogService.registrar(masterId, "CREAR_CARGO", "cargo", guardado.getId(),
            "Cargo creado: " + dto.nombre(), request);
        return guardado;
    }

    @Transactional
    public Cargo updateCargo(Long id, CargoRequest dto, Long masterId, HttpServletRequest request) {
        Cargo cargo = cargoRepository.findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + id));

        cargo.setNombre(dto.nombre());
        cargo.setNivel(dto.nivel());
        cargo.setPuestos(dto.puestos());
        Cargo actualizado = cargoRepository.save(cargo);

        auditLogService.registrar(masterId, "ACTUALIZAR_CARGO", "cargo", id,
            "Cargo actualizado: " + dto.nombre(), request);
        return actualizado;
    }

    @Transactional
    public void deleteCargo(Long id, Long masterId, HttpServletRequest request) {
        Cargo cargo = cargoRepository.findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + id));

        cargoRepository.delete(cargo);

        auditLogService.registrar(masterId, "ELIMINAR_CARGO", "cargo", id,
            "Cargo eliminado: " + cargo.getNombre(), request);
    }

    public List<Cargo> listCargos() {
        return cargoRepository.findAll();
    }

    // ─── Partido ────────────────────────────────────────────────────────────

    @Transactional
    public Partido createPartido(PartidoRequest dto, Long masterId, HttpServletRequest request) {
        partidoRepository.findByNumeroLista(dto.numeroLista()).ifPresent(p -> {
            throw new ConfiguracionConflictException(
                "Ya existe un partido con numeroLista " + dto.numeroLista());
        });

        Partido partido = new Partido(dto.nombre(), dto.siglas(), dto.numeroLista());
        Partido guardado = partidoRepository.save(partido);

        auditLogService.registrar(masterId, "CREAR_PARTIDO", "partido", guardado.getId(),
            "Partido creado: " + dto.siglas(), request);
        return guardado;
    }

    @Transactional
    public Partido updatePartido(Long id, PartidoRequest dto, Long masterId, HttpServletRequest request) {
        Partido partido = partidoRepository.findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + id));

        partidoRepository.findByNumeroLista(dto.numeroLista())
            .filter(p -> !p.getId().equals(id))
            .ifPresent(p -> {
                throw new ConfiguracionConflictException(
                    "Ya existe un partido con numeroLista " + dto.numeroLista());
            });

        partido.setNombre(dto.nombre());
        partido.setSiglas(dto.siglas());
        partido.setNumeroLista(dto.numeroLista());
        Partido actualizado = partidoRepository.save(partido);

        auditLogService.registrar(masterId, "ACTUALIZAR_PARTIDO", "partido", id,
            "Partido actualizado: " + dto.siglas(), request);
        return actualizado;
    }

    @Transactional
    public void deletePartido(Long id, Long masterId, HttpServletRequest request) {
        Partido partido = partidoRepository.findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + id));

        partidoRepository.delete(partido);

        auditLogService.registrar(masterId, "ELIMINAR_PARTIDO", "partido", id,
            "Partido eliminado: " + partido.getSiglas(), request);
    }

    public List<Partido> listPartidos() {
        return partidoRepository.findAll();
    }

    // ─── Candidato ──────────────────────────────────────────────────────────

    @Transactional
    public Candidato createCandidato(CandidatoRequest dto, Long masterId, HttpServletRequest request) {
        Cargo cargo = cargoRepository.findById(dto.cargoId())
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + dto.cargoId()));

        Partido partido = partidoRepository.findById(dto.partidoId())
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + dto.partidoId()));

        Candidato candidato = new Candidato(dto.nombre(), dto.orden(), cargo, partido);
        Candidato guardado = candidatoRepository.save(candidato);

        auditLogService.registrar(masterId, "CREAR_CANDIDATO", "candidato", guardado.getId(),
            "Candidato: " + dto.nombre() + " para cargo " + cargo.getNombre(), request);
        return guardado;
    }

    @Transactional
    public Candidato updateCandidato(Long id, CandidatoRequest dto, Long masterId, HttpServletRequest request) {
        Candidato candidato = candidatoRepository.findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Candidato no encontrado: " + id));

        Cargo cargo = cargoRepository.findById(dto.cargoId())
            .orElseThrow(() -> new ConfiguracionNotFoundException("Cargo no encontrado: " + dto.cargoId()));

        Partido partido = partidoRepository.findById(dto.partidoId())
            .orElseThrow(() -> new ConfiguracionNotFoundException("Partido no encontrado: " + dto.partidoId()));

        candidato.setNombre(dto.nombre());
        candidato.setOrden(dto.orden());
        candidato.setCargo(cargo);
        candidato.setPartido(partido);
        Candidato actualizado = candidatoRepository.save(candidato);

        auditLogService.registrar(masterId, "ACTUALIZAR_CANDIDATO", "candidato", id,
            "Candidato actualizado: " + dto.nombre(), request);
        return actualizado;
    }

    @Transactional
    public void deleteCandidato(Long id, Long masterId, HttpServletRequest request) {
        Candidato candidato = candidatoRepository.findById(id)
            .orElseThrow(() -> new ConfiguracionNotFoundException("Candidato no encontrado: " + id));

        candidatoRepository.delete(candidato);

        auditLogService.registrar(masterId, "ELIMINAR_CANDIDATO", "candidato", id,
            "Candidato eliminado: " + candidato.getNombre(), request);
    }

    public List<Candidato> listCandidatos(Long cargoId, Long partidoId) {
        if (cargoId != null && partidoId != null) {
            return candidatoRepository.findByCargoIdAndPartidoId(cargoId, partidoId);
        }
        if (cargoId != null) {
            return candidatoRepository.findByCargoId(cargoId);
        }
        if (partidoId != null) {
            return candidatoRepository.findByPartidoId(partidoId);
        }
        return candidatoRepository.findAll();
    }
}
