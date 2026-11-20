package ec.twintailcs.electoral.evidencia.web;

import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.evidencia.application.ArchivoEvidencia;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.evidencia.web.dto.CambiarEstadoRequest;
import ec.twintailcs.electoral.evidencia.web.dto.DownloadUrlResponse;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.MarcarObservacionesRequest;
import ec.twintailcs.electoral.evidencia.web.dto.UploadUrlResponse;
import ec.twintailcs.electoral.storage.s3.S3StorageService;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.PATCH;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.UUID;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Endpoints REST para la gestion de evidencias fotograficas.
 *
 * <p>Rutas: POST /api/v1/evidencias (subir, multipart) — GET /api/v1/evidencias/{id} (metadata) —
 * GET /api/v1/evidencias/{id}/download-url (URL prefirmada) — PATCH /api/v1/evidencias/{id}/estado
 * (aprobar / rechazar) — PATCH /api/v1/evidencias/{id}/observaciones — DELETE
 * /api/v1/evidencias/{id}.
 *
 * <p>Port exacto del {@code EvidenciaController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 *
 * <p><b>Orden de las comprobaciones.</b> En Spring, la resolucion de argumentos (que produce el 400
 * de la parte o del parametro faltantes) corre ANTES que el {@code @PreAuthorize} (que produce el
 * 403). Aca se reproduce ese orden: primero la parte multipart y el query param, despues la
 * autorizacion, y al final el caso de uso.
 */
@Path("/api/v1/evidencias")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class EvidenciaResource {

  @Inject Autorizador autorizador;
  @Inject EvidenciaService service;
  @Inject S3StorageService s3Storage;

  /**
   * Sube la foto de un acta.
   *
   * <p>El archivo viaja como parte multipart {@code file} y el acta como query param {@code
   * actaId}.
   */
  @POST
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response subir(
      @RestForm("file") FileUpload archivo,
      @QueryParam("actaId") Long actaId,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    if (archivo == null || archivo.filePath() == null) {
      throw new EvidenciaInvalidaException("El archivo 'file' es obligatorio");
    }
    if (actaId == null) {
      throw new EvidenciaInvalidaException("El parametro 'actaId' es obligatorio");
    }

    autorizador.exigir("evidencia:upload");

    EvidenciaResponse dto = service.subir(leer(archivo), actaId, xForwardedFor);
    return Response.status(Response.Status.CREATED).entity(dto).build();
  }

  /** Ver la metadata de una evidencia (nunca el binario). */
  @GET
  @Path("/{id}")
  public EvidenciaResponse obtener(@PathParam("id") UUID id) {
    autorizador.exigir("evidencia:read");

    return service.obtener(id);
  }

  /** Obtener la URL prefirmada de descarga (15 min de validez). */
  @GET
  @Path("/{id}/download-url")
  public DownloadUrlResponse urlDescarga(@PathParam("id") UUID id) {
    autorizador.exigir("evidencia:download");

    return service.urlDescarga(id);
  }

  /**
   * Obtener la URL prefirmada para subir la foto de la evidencia (15 min de validez). RF-STG-04.
   */
  @POST
  @Path("/{id}/upload-url")
  public UploadUrlResponse urlSubida(@PathParam("id") UUID id) {
    autorizador.exigir("evidencia:upload");

    String key = "evidencias/" + id + ".webp";
    String url = s3Storage.presignUploadUrl(key);
    return new UploadUrlResponse(id, url);
  }

  /** Transicionar la evidencia a APROBADA o RECHAZADA. */
  @PATCH
  @Path("/{id}/estado")
  public EvidenciaResponse cambiarEstado(
      @PathParam("id") UUID id, @Valid CambiarEstadoRequest body) {
    autorizador.exigir("acta:aprobar", "acta:rechazar");

    return service.cambiarEstado(id, body.estado());
  }

  /** Marcar (o borrar) las observaciones de la evidencia. */
  @PATCH
  @Path("/{id}/observaciones")
  public EvidenciaResponse marcarObservaciones(
      @PathParam("id") UUID id,
      @Valid MarcarObservacionesRequest body,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("evidencia:update");

    return service.marcarObservaciones(id, body.tipos(), xForwardedFor);
  }

  /** Eliminar la evidencia: el registro y el objeto de S3. */
  @DELETE
  @Path("/{id}")
  public Response eliminar(
      @PathParam("id") UUID id, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("evidencia:delete");

    service.eliminar(id, xForwardedFor);
    return Response.noContent().build();
  }

  /** Lee la parte multipart a memoria. El temporal lo borra el framework al terminar el request. */
  private ArchivoEvidencia leer(FileUpload archivo) {
    try {
      byte[] contenido = Files.readAllBytes(archivo.filePath());
      return new ArchivoEvidencia(
          archivo.fileName(), archivo.contentType(), contenido.length, contenido);
    } catch (IOException e) {
      throw new EvidenciaInvalidaException("cannot read uploaded file: " + e.getMessage());
    }
  }
}
