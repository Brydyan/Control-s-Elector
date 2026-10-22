package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.incidencia.application.IncidenciaService;
import ec.twintailcs.electoral.acta.incidencia.web.dto.IncidenciaRequestDto;
import ec.twintailcs.electoral.acta.incidencia.web.dto.IncidenciaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.CargoConEstadoDto;
import ec.twintailcs.electoral.acta.web.dto.JrvConEstadoDto;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.evidencia.application.ArchivoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import java.io.IOException;
import java.nio.file.Files;
import java.util.List;
import org.eclipse.microprofile.openapi.annotations.media.Schema;
import org.eclipse.microprofile.openapi.annotations.parameters.Parameter;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.multipart.FileUpload;

/**
 * Endpoints REST del OBSERVER: catalogo de JRVs, registro de actas, borradores, envio, correccion
 * y consultas del veedor.
 *
 * <p>Port exacto del {@code ObserverController} del backend Spring: mismos paths, mismos permisos,
 * mismos status y mismo cuerpo.
 *
 * <p>Rutas:
 *
 * <ul>
 *   <li>GET /api/v1/observer/mis-actas
 *   <li>GET /api/v1/observer/correcciones-pendientes
 *   <li>GET /api/v1/observer/jrvs
 *   <li>GET /api/v1/observer/jrv/{jrvId}/cargos
 *   <li>POST /api/v1/observer/actas (alta directa como ENVIADA, legacy)
 *   <li>POST /api/v1/observer/actas/borrador (guardar o reanudar BORRADOR)
 *   <li>POST /api/v1/observer/actas/{actaId}/enviar (BORRADOR → ENVIADA)
 *   <li>PUT /api/v1/observer/actas/{actaId}/corregir (corregir EN_DISPUTA)
 *   <li>POST /api/v1/observer/actas/{actaId}/imagen (subir la foto del acta)
 *   <li>POST /api/v1/observer/incidencias (contingencia operativa de JRV)
 *   <li>GET /api/v1/observer/actas/{actaId} (detalle completo de mi acta)
 * </ul>
 *
 * <p><b>Orden de las comprobaciones.</b> En Spring la resolucion de argumentos (que produce el 400
 * de la parte o del parametro faltantes) corre ANTES que el {@code @PreAuthorize} (que produce el
 * 403). Aca se reproduce ese orden: primero la validacion del body y de la parte multipart,
 * despues la autorizacion, y al final el caso de uso.
 */
@Path("/api/v1/observer")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class ObserverResource {

  @Inject Autorizador autorizador;
  @Inject ActaService actaService;
  @Inject IncidenciaService incidenciaService;

  /** Actas registradas por el observer autenticado, con filtro opcional por estado. */
  @GET
  @Path("/mis-actas")
  public List<ActaResponseDto> misActas(
      @Parameter(
              description = "Filtrar por estado del acta",
              schema =
                  @Schema(
                      enumeration = {
                        "BORRADOR",
                        "ENVIADA",
                        "EN_DISPUTA",
                        "CORREGIDA",
                        "APROBADA",
                        "ANULADA"
                      }))
          @QueryParam("estado")
          String estado) {
    return actaService.misActas(ParametrosActa.estado(estado));
  }

  /** Actas en disputa que el observer autenticado tiene que corregir. */
  @GET
  @Path("/correcciones-pendientes")
  public List<ActaResponseDto> correccionesPendientes() {
    return actaService.correccionesPendientes();
  }

  /** JRVs con su estado de registro. */
  @GET
  @Path("/jrvs")
  public List<JrvConEstadoDto> jrvs() {
    return actaService.jrvs();
  }

  /** Cargos de una JRV con el estado del acta de cada par. */
  @GET
  @Path("/jrv/{jrvId}/cargos")
  public List<CargoConEstadoDto> cargosDeJrv(@PathParam("jrvId") Long jrvId) {
    return actaService.cargosDeJrv(jrvId);
  }

  /**
   * Registra el acta y la envia (estado ENVIADA).
   *
   * <p>Equivale a guardar el borrador y enviarlo, en un solo paso.
   */
  @POST
  @Path("/actas")
  public Response registrarActa(
      @Valid ActaRequestDto dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("acta:upload");

    return Response.status(Response.Status.CREATED)
        .entity(actaService.registrar(dto, xForwardedFor))
        .build();
  }

  /** Guarda o reanuda un borrador (estado BORRADOR). No valida la integridad electoral. */
  @POST
  @Path("/actas/borrador")
  public ActaResponseDto guardarBorrador(
      @Valid ActaRequestDto dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("acta:upload");

    return actaService.guardarBorrador(dto, xForwardedFor);
  }

  /** Envia el borrador: BORRADOR → ENVIADA, con la validacion completa. */
  @POST
  @Path("/actas/{actaId}/enviar")
  public ActaResponseDto enviarBorrador(
      @PathParam("actaId") Long actaId, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("acta:upload");

    return actaService.enviar(actaId, xForwardedFor);
  }

  /** Corrige el acta en disputa: EN_DISPUTA → CORREGIDA. */
  @PUT
  @Path("/actas/{actaId}/corregir")
  public ActaResponseDto corregirActa(
      @PathParam("actaId") Long actaId,
      @Valid ActaRequestDto dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("acta:upload");

    return actaService.corregir(actaId, dto, false, xForwardedFor);
  }

  /** Sube la fotografia del acta como evidencia. */
  @POST
  @Path("/actas/{actaId}/imagen")
  @Consumes(MediaType.MULTIPART_FORM_DATA)
  public Response subirImagen(
      @PathParam("actaId") Long actaId,
      @RestForm("imagen") FileUpload imagen,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    if (imagen == null || imagen.filePath() == null) {
      throw new EvidenciaInvalidaException("El archivo 'imagen' es obligatorio");
    }
    autorizador.exigir("acta:upload");

    actaService.subirImagen(actaId, leer(imagen), xForwardedFor);
    return Response.status(Response.Status.CREATED).build();
  }

  /** Registra una incidencia operativa de la JRV. */
  @POST
  @Path("/incidencias")
  public Response registrarIncidencia(
      @Valid IncidenciaRequestDto dto, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("acta:upload");

    return Response.status(Response.Status.CREATED)
        .entity(incidenciaService.registrar(dto, xForwardedFor))
        .build();
  }

  /** Detalle completo del acta. Solo el OBSERVER autor puede consultarlo. */
  @GET
  @Path("/actas/{actaId}")
  public ActaDetalleCompletoDto verMiActa(@PathParam("actaId") Long actaId) {
    autorizador.exigir("acta:upload");

    return actaService.verMiActa(actaId);
  }

  /** Lee la parte multipart a memoria. El temporal lo borra el framework al terminar el request. */
  private ArchivoEvidencia leer(FileUpload imagen) {
    try {
      byte[] contenido = Files.readAllBytes(imagen.filePath());
      return new ArchivoEvidencia(
          imagen.fileName(), imagen.contentType(), contenido.length, contenido);
    } catch (IOException e) {
      throw new EvidenciaInvalidaException("cannot read uploaded file: " + e.getMessage());
    }
  }
}
