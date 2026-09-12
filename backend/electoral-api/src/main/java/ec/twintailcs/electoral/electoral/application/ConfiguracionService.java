package ec.twintailcs.electoral.electoral.application;

import ec.twintailcs.electoral.electoral.application.usecase.ActualizarCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ActualizarCargoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ActualizarPartidoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.CrearCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.CrearCargoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.CrearPartidoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.EliminarCandidatoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.EliminarCargoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.EliminarPartidoUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ListarCandidatosUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ListarCargosUseCase;
import ec.twintailcs.electoral.electoral.application.usecase.ListarPartidosUseCase;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Logica de negocio para la configuracion preelectoral (MASTER): registro de Cargos, Partidos y
 * Candidatos.
 *
 * <p>Funciona como fachada/orquestador que delega en casos de uso de responsabilidad unica.
 */
@Service
public class ConfiguracionService {

  private final CrearCargoUseCase crearCargoUseCase;
  private final ActualizarCargoUseCase actualizarCargoUseCase;
  private final EliminarCargoUseCase eliminarCargoUseCase;
  private final ListarCargosUseCase listarCargosUseCase;

  private final CrearPartidoUseCase crearPartidoUseCase;
  private final ActualizarPartidoUseCase actualizarPartidoUseCase;
  private final EliminarPartidoUseCase eliminarPartidoUseCase;
  private final ListarPartidosUseCase listarPartidosUseCase;

  private final CrearCandidatoUseCase crearCandidatoUseCase;
  private final ActualizarCandidatoUseCase actualizarCandidatoUseCase;
  private final EliminarCandidatoUseCase eliminarCandidatoUseCase;
  private final ListarCandidatosUseCase listarCandidatosUseCase;

  public ConfiguracionService(
      CrearCargoUseCase crearCargoUseCase,
      ActualizarCargoUseCase actualizarCargoUseCase,
      EliminarCargoUseCase eliminarCargoUseCase,
      ListarCargosUseCase listarCargosUseCase,
      CrearPartidoUseCase crearPartidoUseCase,
      ActualizarPartidoUseCase actualizarPartidoUseCase,
      EliminarPartidoUseCase eliminarPartidoUseCase,
      ListarPartidosUseCase listarPartidosUseCase,
      CrearCandidatoUseCase crearCandidatoUseCase,
      ActualizarCandidatoUseCase actualizarCandidatoUseCase,
      EliminarCandidatoUseCase eliminarCandidatoUseCase,
      ListarCandidatosUseCase listarCandidatosUseCase) {
    this.crearCargoUseCase = crearCargoUseCase;
    this.actualizarCargoUseCase = actualizarCargoUseCase;
    this.eliminarCargoUseCase = eliminarCargoUseCase;
    this.listarCargosUseCase = listarCargosUseCase;
    this.crearPartidoUseCase = crearPartidoUseCase;
    this.actualizarPartidoUseCase = actualizarPartidoUseCase;
    this.eliminarPartidoUseCase = eliminarPartidoUseCase;
    this.listarPartidosUseCase = listarPartidosUseCase;
    this.crearCandidatoUseCase = crearCandidatoUseCase;
    this.actualizarCandidatoUseCase = actualizarCandidatoUseCase;
    this.eliminarCandidatoUseCase = eliminarCandidatoUseCase;
    this.listarCandidatosUseCase = listarCandidatosUseCase;
  }

  // ─── Cargo ──────────────────────────────────────────────────────────────

  public Cargo createCargo(CargoRequest dto, Long masterId, String ipAddress) {
    return crearCargoUseCase.execute(dto, masterId, ipAddress);
  }

  public Cargo createCargo(CargoRequest dto, Long masterId, HttpServletRequest request) {
    return createCargo(dto, masterId, extractIp(request));
  }

  public Cargo updateCargo(Long id, CargoRequest dto, Long masterId, String ipAddress) {
    return actualizarCargoUseCase.execute(id, dto, masterId, ipAddress);
  }

  public Cargo updateCargo(Long id, CargoRequest dto, Long masterId, HttpServletRequest request) {
    return updateCargo(id, dto, masterId, extractIp(request));
  }

  public void deleteCargo(Long id, Long masterId, String ipAddress) {
    eliminarCargoUseCase.execute(id, masterId, ipAddress);
  }

  public void deleteCargo(Long id, Long masterId, HttpServletRequest request) {
    deleteCargo(id, masterId, extractIp(request));
  }

  public List<Cargo> listCargos() {
    return listarCargosUseCase.execute();
  }

  // ─── Partido ────────────────────────────────────────────────────────────

  public Partido createPartido(PartidoRequest dto, Long masterId, String ipAddress) {
    return crearPartidoUseCase.execute(dto, masterId, ipAddress);
  }

  public Partido createPartido(PartidoRequest dto, Long masterId, HttpServletRequest request) {
    return createPartido(dto, masterId, extractIp(request));
  }

  public Partido updatePartido(Long id, PartidoRequest dto, Long masterId, String ipAddress) {
    return actualizarPartidoUseCase.execute(id, dto, masterId, ipAddress);
  }

  public Partido updatePartido(
      Long id, PartidoRequest dto, Long masterId, HttpServletRequest request) {
    return updatePartido(id, dto, masterId, extractIp(request));
  }

  public void deletePartido(Long id, Long masterId, String ipAddress) {
    eliminarPartidoUseCase.execute(id, masterId, ipAddress);
  }

  public void deletePartido(Long id, Long masterId, HttpServletRequest request) {
    deletePartido(id, masterId, extractIp(request));
  }

  public List<Partido> listPartidos() {
    return listarPartidosUseCase.execute();
  }

  // ─── Candidato ──────────────────────────────────────────────────────────

  public Candidato createCandidato(CandidatoRequest dto, Long masterId, String ipAddress) {
    return crearCandidatoUseCase.execute(dto, masterId, ipAddress);
  }

  public Candidato createCandidato(
      CandidatoRequest dto, Long masterId, HttpServletRequest request) {
    return createCandidato(dto, masterId, extractIp(request));
  }

  public Candidato updateCandidato(Long id, CandidatoRequest dto, Long masterId, String ipAddress) {
    return actualizarCandidatoUseCase.execute(id, dto, masterId, ipAddress);
  }

  public Candidato updateCandidato(
      Long id, CandidatoRequest dto, Long masterId, HttpServletRequest request) {
    return updateCandidato(id, dto, masterId, extractIp(request));
  }

  public void deleteCandidato(Long id, Long masterId, String ipAddress) {
    eliminarCandidatoUseCase.execute(id, masterId, ipAddress);
  }

  public void deleteCandidato(Long id, Long masterId, HttpServletRequest request) {
    deleteCandidato(id, masterId, extractIp(request));
  }

  public List<Candidato> listCandidatos(Long cargoId, Long partidoId) {
    return listarCandidatosUseCase.execute(cargoId, partidoId);
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
