package ec.twintailcs.electoral.acta.web;

import ec.twintailcs.electoral.acta.application.ActaService;
import ec.twintailcs.electoral.acta.incidencia.application.IncidenciaService;
import ec.twintailcs.electoral.acta.incidencia.web.dto.IncidenciaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.ActaDetalleCompletoDto;
import ec.twintailcs.electoral.acta.web.dto.ActaRequestDto;
import ec.twintailcs.electoral.acta.web.dto.ActaResponseDto;
import ec.twintailcs.electoral.acta.web.dto.AnulacionDto;
import ec.twintailcs.electoral.acta.web.dto.DisputaDto;
import ec.twintailcs.electoral.acta.web.dto.HistorialActaDto;
import ec.twintailcs.electoral.auth.security.Autorizador;
import ec.twintailcs.electoral.evidencia.application.ArchivoEvidencia;
import ec.twintailcs.electoral.evidencia.domain.EvidenciaInvalidaException;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.validation.Valid;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DELETE;
import jakarta.ws.rs.DefaultValue;
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
 * Endpoints REST del COORDINATOR y del MASTER: bandeja de actas, detalle, historial, disputa,
 * correccion, aprobacion, anulacion, evidencia de contingencia, incidencias y expediente PDF.
 *
 * <p>Port exacto del {@code CoordinatorController} del backend Spring: mismos paths, mismos
 * permisos, mismos status y mismo cuerpo.
 *
 * <p><b>Guardas.</b> El backend Spring tiene un guard de clase ({@code
 * hasAnyRole('COORDINATOR','MASTER')}) y, en los endpoints que lo necesitan, uno de metodo. Aca no
 * hay guard de clase: cada endpoint declara el suyo, que es el mismo conjunto de reglas:
 *
 * <ul>
 *   <li>La bandeja, el detalle y el historial son de COORDINATOR y MASTER.
 *   <li>Disputar, corregir y aprobar son de COORDINATOR y MASTER.
 *   <li>Anular es solo del MASTER (permiso {@code acta:anular}).
 *   <li>El listado de incidencias y el expediente PDF los puede leer tambien el ADMIN (son
 *       lecturas).
 * </ul>
 */
@Path("/api/v1/coordinator")
@Produces(MediaType.APPLICATION_JSON)
@ApplicationScoped
public class CoordinatorResource {

  @Inject Autorizador autorizador;
  @Inject ActaService actaService;
  @Inject IncidenciaService incidenciaService;

  /**
   * Bandeja de actas, con filtros, busqueda libre y paginacion.
   *
   * @param estado filtro por estado del acta
   * @param jrvId filtro por JRV
   * @param cargoId filtro por cargo
   * @param cantonId filtro por canton
   * @param dignidadId alias de {@code cargoId}
   * @param search busqueda libre (numero de JRV, id o estado)
   */
  @GET
  @Path("/actas")
  public PagedResponse<ActaResponseDto> listarActas(
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
          String estado,
      @QueryParam("jrvId") Long jrvId,
      @QueryParam("cargoId") Long cargoId,
      @QueryParam("cantonId") Long cantonId,
      @QueryParam("dignidadId") Long dignidadId,
      @QueryParam("search") String search,
      @QueryParam("page") @DefaultValue("0") int page,
      @QueryParam("size") @DefaultValue("20") int size,
      @QueryParam("sort") @DefaultValue("fechaRegistro") String sort,
      @QueryParam("dir") @DefaultValue("desc") String dir) {
    autorizador.exigir("COORDINATOR", "MASTER");

    return actaService.listarActas(
        ParametrosActa.estado(estado),
        jrvId,
        cargoId,
        cantonId,
        dignidadId,
        search,
        page,
        size,
        sort,
        dir);
  }

  /** Detalle completo del acta: votos por candidato, evidencia y motivo de disputa. */
  @GET
  @Path("/actas/{actaId}")
  public ActaDetalleCompletoDto verActaDetalle(@PathParam("actaId") Long actaId) {
    autorizador.exigir("COORDINATOR", "MASTER");

    return actaService.detalle(actaId);
  }

  /** Transiciones auditadas del acta, de la mas reciente a la mas antigua. */
  @GET
  @Path("/actas/{actaId}/historial")
  public List<HistorialActaDto> historialActa(@PathParam("actaId") Long actaId) {
    autorizador.exigir("COORDINATOR", "MASTER");

    return actaService.historial(actaId);
  }

  /** Marca el acta en disputa: ENVIADA/CORREGIDA → EN_DISPUTA. */
  @PUT
  @Path("/actas/{actaId}/disputar")
  public ActaResponseDto disputar(
      @PathParam("actaId") Long actaId,
      @Valid DisputaDto dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("COORDINATOR", "MASTER");

    return actaService.disputar(actaId, dto.motivo(), xForwardedFor);
  }

  /** Corrige el acta en disputa: EN_DISPUTA → CORREGIDA. */
  @PUT
  @Path("/actas/{actaId}/corregir")
  public ActaResponseDto corregir(
      @PathParam("actaId") Long actaId,
      @Valid ActaRequestDto dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("COORDINATOR", "MASTER");

    return actaService.corregirComoCoordinator(actaId, dto, xForwardedFor);
  }

  /** Aprueba el acta: ENVIADA/CORREGIDA → APROBADA. */
  @PUT
  @Path("/actas/{actaId}/aprobar")
  public ActaResponseDto aprobar(
      @PathParam("actaId") Long actaId, @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("COORDINATOR", "MASTER");

    return actaService.aprobar(actaId, xForwardedFor);
  }

  /** Anula el acta: APROBADA → ANULADA. Solo el MASTER (permiso {@code acta:anular}). */
  @DELETE
  @Path("/actas/{actaId}")
  public Response anular(
      @PathParam("actaId") Long actaId,
      @Valid AnulacionDto dto,
      @HeaderParam("X-Forwarded-For") String xForwardedFor) {
    autorizador.exigir("acta:anular");

    actaService.anular(actaId, dto.motivo(), xForwardedFor);
    return Response.noContent().build();
  }

  /**
   * Sube la fotografia del acta como contingencia del coordinator.
   *
   * <p>Camino normal: el OBSERVER sube la foto. Este es el relevo cuando el OBSERVER no pudo.
   */
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
    autorizador.exigir("COORDINATOR", "MASTER");

    actaService.subirEvidenciaDeContingencia(actaId, leer(imagen), xForwardedFor);
    return Response.status(Response.Status.CREATED).build();
  }

  /** Listado de incidencias operativas de JRV, con filtro opcional por JRV. */
  @GET
  @Path("/incidencias")
  public PagedResponse<IncidenciaResponseDto> listarIncidencias(
      @QueryParam("jrvId") Long jrvId,
      @Parameter(description = "Número de página (comenzando en 0)") @QueryParam("page")
          Integer page,
      @Parameter(description = "Tamaño de la página (máximo 50)") @QueryParam("size")
          Integer size) {
    autorizador.exigir("COORDINATOR", "MASTER", "ADMIN");

    int pageNum = page != null ? page : 0;
    int pageSize = size != null ? size : 20;

    // Validación: max size = 50
    if (pageSize > 50) {
      pageSize = 50;
    }
    if (pageSize <= 0) {
      pageSize = 20;
    }

    return incidenciaService.listarPaginado(jrvId, pageNum, pageSize);
  }

  /**
   * Expediente de impugnacion CNE en PDF.
   *
   * <p>Se genera en memoria: no toca disco.
   */
  @GET
  @Path("/actas/{actaId}/expediente-pdf")
  @Produces("application/pdf")
  public Response descargarExpedientePdf(@PathParam("actaId") Long actaId) {
    autorizador.exigir("COORDINATOR", "MASTER", "ADMIN");

    byte[] pdf = actaService.expedientePdf(actaId);
    String filename = "expediente-acta-" + actaId + ".pdf";
    return Response.ok(pdf)
        .type("application/pdf")
        .header("Content-Disposition", "inline; filename=\"" + filename + "\"")
        .build();
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
