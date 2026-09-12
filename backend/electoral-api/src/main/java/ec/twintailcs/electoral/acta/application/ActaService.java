package ec.twintailcs.electoral.acta.application;

import ec.twintailcs.electoral.acta.application.usecase.AnularActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.AprobarActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.CorregirActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.EnviarActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.GuardarBorradorActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.MarcarEnDisputaActaUseCase;
import ec.twintailcs.electoral.acta.application.usecase.RegistrarActaUseCase;
import ec.twintailcs.electoral.acta.domain.Acta;
import ec.twintailcs.electoral.acta.web.ActaRequestDto;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Service;

/**
 * Fachada para la lógica de negocio y ciclo de vida de Actas.
 *
 * <p>Delega en casos de uso de responsabilidad única y mantiene compatibilidad con los
 * controladores HTTP.
 */
@Service
public class ActaService {

  private final GuardarBorradorActaUseCase guardarBorradorActaUseCase;
  private final EnviarActaUseCase enviarActaUseCase;
  private final RegistrarActaUseCase registrarActaUseCase;
  private final MarcarEnDisputaActaUseCase marcarEnDisputaActaUseCase;
  private final CorregirActaUseCase corregirActaUseCase;
  private final AprobarActaUseCase aprobarActaUseCase;
  private final AnularActaUseCase anularActaUseCase;

  public ActaService(
      GuardarBorradorActaUseCase guardarBorradorActaUseCase,
      EnviarActaUseCase enviarActaUseCase,
      RegistrarActaUseCase registrarActaUseCase,
      MarcarEnDisputaActaUseCase marcarEnDisputaActaUseCase,
      CorregirActaUseCase corregirActaUseCase,
      AprobarActaUseCase aprobarActaUseCase,
      AnularActaUseCase anularActaUseCase) {
    this.guardarBorradorActaUseCase = guardarBorradorActaUseCase;
    this.enviarActaUseCase = enviarActaUseCase;
    this.registrarActaUseCase = registrarActaUseCase;
    this.marcarEnDisputaActaUseCase = marcarEnDisputaActaUseCase;
    this.corregirActaUseCase = corregirActaUseCase;
    this.aprobarActaUseCase = aprobarActaUseCase;
    this.anularActaUseCase = anularActaUseCase;
  }

  // ---------------------------------------------------------------------
  // Creación y transición inicial
  // ---------------------------------------------------------------------

  public Acta guardarBorrador(ActaRequestDto dto, Long observerId, String ipAddress) {
    return guardarBorradorActaUseCase.execute(dto, observerId, ipAddress);
  }

  public Acta guardarBorrador(ActaRequestDto dto, Long observerId, HttpServletRequest request) {
    return guardarBorrador(dto, observerId, extractIp(request));
  }

  public Acta enviar(Long actaId, Long observerId, String ipAddress) {
    return enviarActaUseCase.execute(actaId, observerId, ipAddress);
  }

  public Acta enviar(Long actaId, Long observerId, HttpServletRequest request) {
    return enviar(actaId, observerId, extractIp(request));
  }

  public Acta registrar(ActaRequestDto dto, Long observerId, String ipAddress) {
    return registrarActaUseCase.execute(dto, observerId, ipAddress);
  }

  public Acta registrar(ActaRequestDto dto, Long observerId, HttpServletRequest request) {
    return registrar(dto, observerId, extractIp(request));
  }

  // ---------------------------------------------------------------------
  // Transiciones
  // ---------------------------------------------------------------------

  public Acta marcarEnDisputa(Long actaId, Long coordinatorId, String motivo, String ipAddress) {
    return marcarEnDisputaActaUseCase.execute(actaId, coordinatorId, motivo, ipAddress);
  }

  public Acta marcarEnDisputa(
      Long actaId, Long coordinatorId, String motivo, HttpServletRequest request) {
    return marcarEnDisputa(actaId, coordinatorId, motivo, extractIp(request));
  }

  public Acta corregir(
      Long actaId, ActaRequestDto dto, Long usuarioId, boolean esCoordinator, String ipAddress) {
    return corregirActaUseCase.execute(actaId, dto, usuarioId, esCoordinator, ipAddress);
  }

  public Acta corregir(
      Long actaId,
      ActaRequestDto dto,
      Long usuarioId,
      boolean esCoordinator,
      HttpServletRequest request) {
    return corregir(actaId, dto, usuarioId, esCoordinator, extractIp(request));
  }

  public Acta aprobar(Long actaId, Long coordinatorId, String ipAddress) {
    return aprobarActaUseCase.execute(actaId, coordinatorId, ipAddress);
  }

  public Acta aprobar(Long actaId, Long coordinatorId, HttpServletRequest request) {
    return aprobar(actaId, coordinatorId, extractIp(request));
  }

  public Acta anular(Long actaId, Long masterId, String motivo, String ipAddress) {
    return anularActaUseCase.execute(actaId, masterId, motivo, ipAddress);
  }

  public Acta anular(Long actaId, Long masterId, String motivo, HttpServletRequest request) {
    return anular(actaId, masterId, motivo, extractIp(request));
  }

  private String extractIp(HttpServletRequest request) {
    if (request == null) return null;
    String xForwardedFor = request.getHeader("X-Forwarded-For");
    if (xForwardedFor != null && !xForwardedFor.isBlank()) {
      return xForwardedFor.split(",")[0].trim();
    }
    return request.getRemoteAddr();
  }
}
