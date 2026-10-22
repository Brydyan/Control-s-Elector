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
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.List;

/**
 * Logica de negocio para la configuracion preelectoral (MASTER): registro de Cargos, Partidos y
 * Candidatos.
 *
 * <p>Funciona como fachada/orquestador que delega en casos de uso de responsabilidad unica, igual
 * que en el backend Spring. La IP del cliente la resuelve el recurso y la pasa como texto: aca no
 * hay {@code HttpServletRequest}.
 */
@ApplicationScoped
public class ConfiguracionService {

  @Inject CrearCargoUseCase crearCargoUseCase;
  @Inject ActualizarCargoUseCase actualizarCargoUseCase;
  @Inject EliminarCargoUseCase eliminarCargoUseCase;
  @Inject ListarCargosUseCase listarCargosUseCase;

  @Inject CrearPartidoUseCase crearPartidoUseCase;
  @Inject ActualizarPartidoUseCase actualizarPartidoUseCase;
  @Inject EliminarPartidoUseCase eliminarPartidoUseCase;
  @Inject ListarPartidosUseCase listarPartidosUseCase;

  @Inject CrearCandidatoUseCase crearCandidatoUseCase;
  @Inject ActualizarCandidatoUseCase actualizarCandidatoUseCase;
  @Inject EliminarCandidatoUseCase eliminarCandidatoUseCase;
  @Inject ListarCandidatosUseCase listarCandidatosUseCase;

  // ─── Cargo ──────────────────────────────────────────────────────────────

  public Cargo createCargo(CargoRequest dto, Long masterId, String ipAddress) {
    return crearCargoUseCase.execute(dto, masterId, ipAddress);
  }

  public Cargo updateCargo(Long id, CargoRequest dto, Long masterId, String ipAddress) {
    return actualizarCargoUseCase.execute(id, dto, masterId, ipAddress);
  }

  public void deleteCargo(Long id, Long masterId, String ipAddress) {
    eliminarCargoUseCase.execute(id, masterId, ipAddress);
  }

  public List<Cargo> listCargos() {
    return listarCargosUseCase.execute();
  }

  // ─── Partido ────────────────────────────────────────────────────────────

  public Partido createPartido(PartidoRequest dto, Long masterId, String ipAddress) {
    return crearPartidoUseCase.execute(dto, masterId, ipAddress);
  }

  public Partido updatePartido(Long id, PartidoRequest dto, Long masterId, String ipAddress) {
    return actualizarPartidoUseCase.execute(id, dto, masterId, ipAddress);
  }

  public void deletePartido(Long id, Long masterId, String ipAddress) {
    eliminarPartidoUseCase.execute(id, masterId, ipAddress);
  }

  public List<Partido> listPartidos() {
    return listarPartidosUseCase.execute();
  }

  // ─── Candidato ──────────────────────────────────────────────────────────

  public Candidato createCandidato(CandidatoRequest dto, Long masterId, String ipAddress) {
    return crearCandidatoUseCase.execute(dto, masterId, ipAddress);
  }

  public Candidato updateCandidato(
      Long id, CandidatoRequest dto, Long masterId, String ipAddress) {
    return actualizarCandidatoUseCase.execute(id, dto, masterId, ipAddress);
  }

  public void deleteCandidato(Long id, Long masterId, String ipAddress) {
    eliminarCandidatoUseCase.execute(id, masterId, ipAddress);
  }

  public List<Candidato> listCandidatos(Long cargoId, Long partidoId) {
    return listarCandidatosUseCase.execute(cargoId, partidoId);
  }
}
