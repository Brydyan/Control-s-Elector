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

  public Cargo createCargo(CargoRequest dto, Long masterId, HttpServletRequest request) {
    return crearCargoUseCase.execute(dto, masterId, request);
  }

  public Cargo updateCargo(Long id, CargoRequest dto, Long masterId, HttpServletRequest request) {
    return actualizarCargoUseCase.execute(id, dto, masterId, request);
  }

  public void deleteCargo(Long id, Long masterId, HttpServletRequest request) {
    eliminarCargoUseCase.execute(id, masterId, request);
  }

  public List<Cargo> listCargos() {
    return listarCargosUseCase.execute();
  }

  // ─── Partido ────────────────────────────────────────────────────────────

  public Partido createPartido(PartidoRequest dto, Long masterId, HttpServletRequest request) {
    return crearPartidoUseCase.execute(dto, masterId, request);
  }

  public Partido updatePartido(
      Long id, PartidoRequest dto, Long masterId, HttpServletRequest request) {
    return actualizarPartidoUseCase.execute(id, dto, masterId, request);
  }

  public void deletePartido(Long id, Long masterId, HttpServletRequest request) {
    eliminarPartidoUseCase.execute(id, masterId, request);
  }

  public List<Partido> listPartidos() {
    return listarPartidosUseCase.execute();
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
