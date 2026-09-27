package ec.twintailcs.electoral.evidencia.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.config.RequiredPermission;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.web.dto.CambiarEstadoRequest;
import ec.twintailcs.electoral.evidencia.web.dto.DownloadUrlResponse;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import ec.twintailcs.electoral.evidencia.web.dto.mapper.EvidenciaMapper;
import ec.twintailcs.electoral.shared.web.dto.ApiErrorResponseDto;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * Endpoints REST para la gestion de evidencias fotograficas.
 *
 * <p>Rutas: POST /api/v1/evidencias # subir (multipart) — actaId GET /api/v1/evidencias/{id} # ver
 * metadata GET /api/v1/evidencias/{id}/download-url # presigned GET (15 min) PATCH
 * /api/v1/evidencias/{id}/estado # aprobar / rechazar DELETE /api/v1/evidencias/{id} # eliminar
 *
 * <p>Convenciones multipart: - El archivo viaja como part "file" - El actaId viaja como form field
 * {@code actaId}. - Antes (T-BE-14 abierto) se usaba jrvId+cargoId; ahora la FK es al acta.
 */
@Tag(
    name = "Evidencias",
    description = "Subida, descarga, estados y auditoria de actas fisicas en S3/MinIO.")
@RestController
@RequestMapping("/api/v1/evidencias")
public class EvidenciaController {

  private final EvidenciaService service;
  private final EvidenciaMapper evidenciaMapper;

  public EvidenciaController(EvidenciaService service, EvidenciaMapper evidenciaMapper) {
    this.service = service;
    this.evidenciaMapper = evidenciaMapper;
  }

  @Operation(
      summary = "Subir evidencia fotografica",
      description = "Carga imagen (JPG/PNG/WEBP, max 10MB) asociada al acta indicada en actaId.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "201",
        description = "Evidencia subida y vinculada al acta",
        content = @Content(schema = @Schema(implementation = EvidenciaResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Archivo ausente, tipo no permitido o supera el limite de 10MB",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso evidencia:upload",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @RequiredPermission("evidencia:upload")
  @PreAuthorize("@authz.has('evidencia:upload')")
  @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
  public ResponseEntity<EvidenciaResponse> upload(
      @Parameter(description = "Archivo de imagen (max 10MB, formatos: jpg/png/webp)")
          @RequestPart("file")
          MultipartFile file,
      @Parameter(description = "ID del acta a la que se asocia la evidencia")
          @RequestParam("actaId")
          Long actaId) {

    UUID userId = CurrentUser.staticId();
    Evidencia e = service.upload(file, actaId, userId);
    return ResponseEntity.status(HttpStatus.CREATED).body(evidenciaMapper.toDto(e));
  }

  @Operation(
      summary = "Ver metadata de evidencia",
      description = "Devuelve la metadata de la evidencia (no el binario).")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Metadata de la evidencia",
        content = @Content(schema = @Schema(implementation = EvidenciaResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Evidencia no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @RequiredPermission("evidencia:read")
  @PreAuthorize("@authz.has('evidencia:read')")
  @GetMapping("/{id}")
  public EvidenciaResponse get(
      @Parameter(description = "UUID publico de la evidencia") @PathVariable UUID id) {
    return evidenciaMapper.toDto(service.get(id));
  }

  @Operation(
      summary = "Obtener URL prefirmada de descarga",
      description = "Genera una URL S3 presigned (valida 15 min) para descargar la imagen.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "URL generada",
        content = @Content(schema = @Schema(implementation = DownloadUrlResponse.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Evidencia no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @RequiredPermission("evidencia:download")
  @PreAuthorize("@authz.has('evidencia:download')")
  @GetMapping("/{id}/download-url")
  public DownloadUrlResponse downloadUrl(
      @Parameter(description = "UUID publico de la evidencia") @PathVariable UUID id) {
    String url = service.presignDownload(id);
    return new DownloadUrlResponse(id, url);
  }

  @Operation(
      summary = "Cambiar estado de evidencia",
      description =
          "Transiciona la evidencia a APROBADA o RECHAZADA. Requiere acta:aprobar o acta:rechazar.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Estado actualizado",
        content = @Content(schema = @Schema(implementation = EvidenciaResponse.class))),
    @ApiResponse(
        responseCode = "400",
        description = "Estado de destino invalido para la transicion actual",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Evidencia no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @RequiredPermission({"acta:aprobar", "acta:rechazar"})
  @PreAuthorize("@authz.hasAny('acta:aprobar','acta:rechazar')")
  @PatchMapping("/{id}/estado")
  public EvidenciaResponse cambiarEstado(
      @Parameter(description = "UUID publico de la evidencia") @PathVariable UUID id,
      @Valid @org.springframework.web.bind.annotation.RequestBody CambiarEstadoRequest body) {
    Evidencia e = service.cambiarEstado(id, body.estado());
    return evidenciaMapper.toDto(e);
  }

  @Operation(
      summary = "Eliminar evidencia",
      description = "Elimina el registro y el objeto en S3. Solo el dueno o roles con evidencia:delete.")
  @ApiResponses({
    @ApiResponse(responseCode = "204", description = "Evidencia eliminada"),
    @ApiResponse(
        responseCode = "403",
        description = "No autorizado para eliminar esta evidencia",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Evidencia no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @RequiredPermission("evidencia:delete")
  @PreAuthorize("@authz.has('evidencia:delete')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> delete(
      @Parameter(description = "UUID publico de la evidencia") @PathVariable UUID id) {
    service.delete(id, CurrentUser.staticId());
    return ResponseEntity.noContent().build();
  }

  /**
   * T-BE-16: marca hasta {@code ObservacionEvidencia.MAX_POR_EVIDENCIA} observaciones sobre la
   * evidencia. Lista vacía = borrar todas. Solo el OBSERVER dueño puede escribir (RF-OBS-01);
   * autorización fina (autoría) está en el servicio.
   *
   * <p>Roles con permiso {@code evidencia:update}: OBSERVER, COORDINATOR, MASTER. NO ADMIN
   * (RF-OBS-06).
   */
  @Operation(
      summary = "Marcar observaciones de evidencia",
      description =
          "Registra o sobreescribe las observaciones de la evidencia. Lista vacia = borrar todas.")
  @ApiResponses({
    @ApiResponse(
        responseCode = "200",
        description = "Observaciones actualizadas",
        content = @Content(schema = @Schema(implementation = EvidenciaResponse.class))),
    @ApiResponse(
        responseCode = "403",
        description = "Sin permiso evidencia:update o no es el dueno",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class))),
    @ApiResponse(
        responseCode = "404",
        description = "Evidencia no encontrada",
        content = @Content(schema = @Schema(implementation = ApiErrorResponseDto.class)))
  })
  @RequiredPermission("evidencia:update")
  @PreAuthorize("@authz.has('evidencia:update')")
  @PatchMapping("/{id}/observaciones")
  public EvidenciaResponse marcarObservaciones(
      @PathVariable UUID id,
      @Valid @org.springframework.web.bind.annotation.RequestBody
          ec.twintailcs.electoral.evidencia.web.dto.MarcarObservacionesRequest body,
      jakarta.servlet.http.HttpServletRequest request) {
    Evidencia e = service.marcarObservaciones(id, body.tipos(), CurrentUser.staticId(), request);
    return evidenciaMapper.toDto(e);
  }

  /** Tipico: devolver 201 + Location header. Acu devolvemos la entidad. */
  @SuppressWarnings("unused")
  private static class LocationBuilder {}
}
