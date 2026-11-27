package ec.twintailcs.electoral.evidencia.application;

import ec.twintailcs.electoral.auth.security.CurrentUser;
import ec.twintailcs.electoral.electoral.web.ContextoSolicitud;
import ec.twintailcs.electoral.evidencia.application.usecase.CambiarEstadoEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.ConsultarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.EliminarEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.MarcarObservacionesUseCase;
import ec.twintailcs.electoral.evidencia.application.usecase.SubirEvidenciaUseCase;
import ec.twintailcs.electoral.evidencia.domain.EstadoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.domain.RlsContexto;
import ec.twintailcs.electoral.evidencia.domain.TipoObservacion;
import ec.twintailcs.electoral.evidencia.web.dto.DownloadUrlResponse;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.mapper.EvidenciaMapper;
import ec.twintailcs.electoral.storage.StorageException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.UUID;

/**
 * Fachada del modulo de evidencias. Delega la logica en los casos de uso y resuelve el contexto del
 * request (quien llama y desde donde).
 *
 * <p><b>Por que la transaccion vive aca y no en el resource.</b> El backend Spring mapea el DTO en
 * el controlador, con la sesion de JPA todavia abierta porque la transaccion del caso de uso ya
 * termino. Aca el equivalente es mapear dentro de la transaccion: el DTO lee las observaciones de
 * la base y el estado del RLS depende de la conexion de la transaccion. Por eso los metodos de la
 * fachada son transaccionales y arman el DTO antes de devolverlo.
 */
@ApplicationScoped
public class EvidenciaService {

  @Inject ContextoSolicitud contexto;
  @Inject CurrentUser currentUser;
  @Inject RlsContexto rls;
  @Inject EvidenciaMapper mapper;

  @Inject SubirEvidenciaUseCase subirEvidenciaUseCase;
  @Inject ConsultarEvidenciaUseCase consultarEvidenciaUseCase;
  @Inject CambiarEstadoEvidenciaUseCase cambiarEstadoEvidenciaUseCase;
  @Inject EliminarEvidenciaUseCase eliminarEvidenciaUseCase;
  @Inject MarcarObservacionesUseCase marcarObservacionesUseCase;

  /**
   * Sube una evidencia y devuelve su metadata.
   *
   * @param archivo archivo multipart ya leido
   * @param actaId id interno del acta
   * @param xForwardedFor cabecera {@code X-Forwarded-For} (IP del cliente)
   */
  @Transactional
  public EvidenciaResponse subir(ArchivoEvidencia archivo, Long actaId, String xForwardedFor) {
    Long usuarioId = contexto.usuarioId();
    rls.activar(usuarioId);

    Evidencia evidencia =
        subirEvidenciaUseCase.execute(
            archivo, actaId, usuarioId, currentUser.rol(), contexto.ip(xForwardedFor));

    return mapper.toDto(evidencia);
  }

  /** Metadata de una evidencia por su UUID publico. */
  @Transactional
  public EvidenciaResponse obtener(UUID publicId) {
    rls.activar(contexto.usuarioId());

    return mapper.toDto(consultarEvidenciaUseCase.get(publicId));
  }

  /** URL prefirmada de descarga de la evidencia. */
  @Transactional
  public DownloadUrlResponse urlDescarga(UUID publicId) throws StorageException {
    rls.activar(contexto.usuarioId());

    String url = consultarEvidenciaUseCase.presignDownload(publicId);
    return new DownloadUrlResponse(publicId, url);
  }

  /** Transiciona la evidencia a APROBADA o RECHAZADA. */
  @Transactional
  public EvidenciaResponse cambiarEstado(UUID publicId, EstadoEvidencia nuevo) {
    rls.activar(contexto.usuarioId());

    return mapper.toDto(cambiarEstadoEvidenciaUseCase.execute(publicId, nuevo));
  }

  /** Elimina la evidencia (registro y objeto de S3). */
  @Transactional(rollbackOn = StorageException.class)
  public void eliminar(UUID publicId, String xForwardedFor) throws StorageException {
    Long usuarioId = contexto.usuarioId();
    rls.activar(usuarioId);

    eliminarEvidenciaUseCase.execute(publicId, usuarioId, contexto.ip(xForwardedFor));
  }

  /** Marca (o borra, si la lista viene vacia) las observaciones de la evidencia. */
  @Transactional
  public EvidenciaResponse marcarObservaciones(
      UUID publicId, List<TipoObservacion> tipos, String xForwardedFor) {
    Long usuarioId = contexto.usuarioId();
    rls.activar(usuarioId);

    Evidencia evidencia =
        marcarObservacionesUseCase.execute(publicId, tipos, usuarioId, contexto.ip(xForwardedFor));

    return mapper.toDto(evidencia);
  }
}
