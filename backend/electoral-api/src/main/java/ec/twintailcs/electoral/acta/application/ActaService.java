package ec.twintailcs.electoral.acta.application;

import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.domain.ActaDetalle;
import ec.twintailcs.electoral.acta.domain.ActaDetalleRepository;
import ec.twintailcs.electoral.acta.domain.ActaRepository;
import ec.twintailcs.electoral.acta.domain.EstadoActa;
import ec.twintailcs.electoral.acta.domain.ObserverJrvAsignacion;
import ec.twintailcs.electoral.acta.domain.ObserverJrvAsignacionRepository;
import ec.twintailcs.electoral.acta.web.AccesoNoPermitidoException;
import ec.twintailcs.electoral.acta.web.ActaDetalleDto;
import ec.twintailcs.electoral.acta.web.ActaInvalidaException;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import ec.twintailcs.electoral.acta.web.JrvCargoYaRegistradoException;
import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.CandidatoRepository;
import ec.twintailcs.electoral.electoral.domain.CargoRepository;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.JrvRepository;
import jakarta.servlet.http.HttpServletRequest;
import java.util.logging.Logger;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ActaService {

    private static final Logger log = Logger.getLogger(ActaService.class.getName());

    private final ActaRepository actaRepository;
    private final ActaDetalleRepository actaDetalleRepository;
    private final JrvRepository jrvRepository;
    private final UsuarioRepository usuarioRepository;
    private final CandidatoRepository candidatoRepository;
    private final CargoRepository cargoRepository;
    private final ObserverJrvAsignacionRepository asignacionRepository;
    private final AuditLogService auditLogService;

    public ActaService(ActaRepository actaRepository,
                       ActaDetalleRepository actaDetalleRepository,
                       JrvRepository jrvRepository,
                       UsuarioRepository usuarioRepository,
                       CandidatoRepository candidatoRepository,
                       CargoRepository cargoRepository,
                       ObserverJrvAsignacionRepository asignacionRepository,
                       AuditLogService auditLogService) {
        this.actaRepository = actaRepository;
        this.actaDetalleRepository = actaDetalleRepository;
        this.jrvRepository = jrvRepository;
        this.usuarioRepository = usuarioRepository;
        this.candidatoRepository = candidatoRepository;
        this.cargoRepository = cargoRepository;
        this.asignacionRepository = asignacionRepository;
        this.auditLogService = auditLogService;
    }

    @Transactional
    public Acta registrar(ActaRequestDto dto, Long observerId, HttpServletRequest request) {
        Jrv jrv = jrvRepository.findById(dto.getJrvId())
            .orElseThrow(() -> new ActaInvalidaException("JRV no encontrada: " + dto.getJrvId()));

        Cargo cargo = cargoRepository.findById(dto.getCargoId())
            .orElseThrow(() -> new ActaInvalidaException("Cargo no encontrado: " + dto.getCargoId()));

        Usuario observer = usuarioRepository.findById(observerId)
            .orElseThrow(() -> new ActaInvalidaException("Observer no encontrado"));

        boolean tieneAsignacion = asignacionRepository.existsByObserverAndJrv(observer, jrv);
        if (!tieneAsignacion) {
            throw new AccesoNoPermitidoException(
                "Observer " + observerId + " no tiene asignación en JRV " + dto.getJrvId()
            );
        }

        actaRepository.findActaActivaConLock(jrv, cargo).ifPresent(actaExistente -> {
            throw new JrvCargoYaRegistradoException(dto.getJrvId(), dto.getCargoId());
        });

        int sumaVotos = dto.getDetalles().stream()
            .mapToInt(ActaDetalleDto::getVotos)
            .sum();

        int total = sumaVotos + dto.getVotosBlancos() + dto.getVotosNulos();

        if (total != dto.getTotalSufragantes()) {
            throw new ActaInvalidaException(
                "Los votos no cuadran. " +
                "Candidatos: " + sumaVotos +
                " + Blancos: " + dto.getVotosBlancos() +
                " + Nulos: " + dto.getVotosNulos() +
                " = " + total +
                " ≠ " + dto.getTotalSufragantes() + " (total sufragantes)"
            );
        }

        Acta acta = new Acta();
        acta.setJrv(jrv);
        acta.setCargo(cargo);
        acta.setRegistradaPor(observer);
        acta.setEstado(EstadoActa.ENVIADA);
        acta.setTotalSufragantes(dto.getTotalSufragantes());
        acta.setVotosBlancos(dto.getVotosBlancos());
        acta.setVotosNulos(dto.getVotosNulos());

        Acta actaGuardada = actaRepository.save(acta);

        for (ActaDetalleDto detalleDto : dto.getDetalles()) {
            Candidato candidato = candidatoRepository.findById(detalleDto.getCandidatoId())
                .orElseThrow(() -> new ActaInvalidaException(
                    "Candidato no encontrado: " + detalleDto.getCandidatoId()
                ));

            ActaDetalle detalle = new ActaDetalle();
            detalle.setActa(actaGuardada);
            detalle.setCandidato(candidato);
            detalle.setVotos(detalleDto.getVotos());
            actaDetalleRepository.save(detalle);
        }

        auditLogService.registrar(
            observerId,
            "REGISTRO_ACTA",
            "acta",
            actaGuardada.getId(),
            "Acta registrada: JRV=" + dto.getJrvId() + ", cargo=" + dto.getCargoId() +
            ", sufragantes=" + dto.getTotalSufragantes(),
            request
        );

        return actaGuardada;
    }
}
