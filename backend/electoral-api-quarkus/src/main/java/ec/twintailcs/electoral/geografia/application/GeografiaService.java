package ec.twintailcs.electoral.geografia.application;

import ec.twintailcs.electoral.geografia.application.usecase.ActualizarCantonUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.ActualizarJrvUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.ActualizarParroquiaUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.ActualizarProvinciaUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.CrearCantonUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.CrearJrvUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.CrearParroquiaUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.CrearProvinciaUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.EliminarCantonUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.EliminarJrvUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.EliminarParroquiaUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.EliminarProvinciaUseCase;
import ec.twintailcs.electoral.geografia.application.usecase.ListarGeografiaUseCase;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import ec.twintailcs.electoral.geografia.web.dto.GeografiaTreeDto;
import ec.twintailcs.electoral.geografia.web.dto.JrvRequest;
import ec.twintailcs.electoral.geografia.web.dto.ParroquiaRequest;
import ec.twintailcs.electoral.geografia.web.dto.ProvinciaRequest;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

/**
 * Fachada de la geografia electoral.
 *
 * <p>Igual que {@code ConfiguracionService}: la facade no hace trabajo, delega en los casos de uso.
 * Asi el resource no toca dominio y cada operacion tiene su propio caso de uso testeable.
 */
@ApplicationScoped
public class GeografiaService {

  @Inject ListarGeografiaUseCase listarGeografia;
  @Inject CrearProvinciaUseCase crearProvincia;
  @Inject ActualizarProvinciaUseCase actualizarProvincia;
  @Inject EliminarProvinciaUseCase eliminarProvincia;
  @Inject CrearCantonUseCase crearCanton;
  @Inject ActualizarCantonUseCase actualizarCanton;
  @Inject EliminarCantonUseCase eliminarCanton;
  @Inject CrearParroquiaUseCase crearParroquia;
  @Inject ActualizarParroquiaUseCase actualizarParroquia;
  @Inject EliminarParroquiaUseCase eliminarParroquia;
  @Inject CrearJrvUseCase crearJrv;
  @Inject ActualizarJrvUseCase actualizarJrv;
  @Inject EliminarJrvUseCase eliminarJrv;

  /** Arbol geografico completo. */
  public GeografiaTreeDto listarArbol() {
    return listarGeografia.execute();
  }

  /** Alta de provincia. */
  public Provincia crearProvincia(ProvinciaRequest dto, Long masterId, String ipAddress) {
    return crearProvincia.execute(dto, masterId, ipAddress);
  }

  /** Edicion de provincia. */
  public Provincia actualizarProvincia(
      Long id, ProvinciaRequest dto, Long masterId, String ipAddress) {
    return actualizarProvincia.execute(id, dto, masterId, ipAddress);
  }

  /** Baja de provincia. */
  public void eliminarProvincia(Long id, Long masterId, String ipAddress) {
    eliminarProvincia.execute(id, masterId, ipAddress);
  }

  /** Alta de canton. */
  public Canton crearCanton(CantonRequest dto, Long masterId, String ipAddress) {
    return crearCanton.execute(dto, masterId, ipAddress);
  }

  /** Edicion de canton. */
  public Canton actualizarCanton(Long id, CantonRequest dto, Long masterId, String ipAddress) {
    return actualizarCanton.execute(id, dto, masterId, ipAddress);
  }

  /** Baja de canton. */
  public void eliminarCanton(Long id, Long masterId, String ipAddress) {
    eliminarCanton.execute(id, masterId, ipAddress);
  }

  /** Alta de parroquia. */
  public Parroquia crearParroquia(ParroquiaRequest dto, Long masterId, String ipAddress) {
    return crearParroquia.execute(dto, masterId, ipAddress);
  }

  /** Edicion de parroquia. */
  public Parroquia actualizarParroquia(
      Long id, ParroquiaRequest dto, Long masterId, String ipAddress) {
    return actualizarParroquia.execute(id, dto, masterId, ipAddress);
  }

  /** Baja de parroquia. */
  public void eliminarParroquia(Long id, Long masterId, String ipAddress) {
    eliminarParroquia.execute(id, masterId, ipAddress);
  }

  /** Alta de JRV. */
  public Jrv crearJrv(JrvRequest dto, Long masterId, String ipAddress) {
    return crearJrv.execute(dto, masterId, ipAddress);
  }

  /** Edicion de JRV. */
  public Jrv actualizarJrv(Long id, JrvRequest dto, Long masterId, String ipAddress) {
    return actualizarJrv.execute(id, dto, masterId, ipAddress);
  }

  /** Baja de JRV. */
  public void eliminarJrv(Long id, Long masterId, String ipAddress) {
    eliminarJrv.execute(id, masterId, ipAddress);
  }
}
