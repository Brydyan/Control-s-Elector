package ec.twintailcs.electoral.evidencia.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.config.RequiredPermission;
import ec.twintailcs.electoral.evidencia.application.EvidenciaService;
import ec.twintailcs.electoral.evidencia.domain.Evidencia;
import ec.twintailcs.electoral.evidencia.web.dto.CambiarEstadoRequest;
import ec.twintailcs.electoral.evidencia.web.dto.DownloadUrlResponse;
import ec.twintailcs.electoral.evidencia.web.dto.EvidenciaResponse;
import jakarta.validation.Valid;
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
 * Rutas:
 *   POST   /api/v1/evidencias                       # subir (multipart)
 *   GET    /api/v1/evidencias/{id}                   # ver metadata
 *   GET    /api/v1/evidencias/{id}/download-url      # presigned GET (15 min)
 *   PATCH  /api/v1/evidencias/{id}/estado            # aprobar / rechazar
 *   DELETE /api/v1/evidencias/{id}                  # eliminar
 *
 * Convenciones multipart:
 *   - El archivo viaja como part "file"
 *   - Los metadatos (jrvId, cargoId) viajan como part "metadata"
 *     serializada como JSON (application/json) o form fields.
 *     Acu usamos form fields para simplicidad con clientes nativos.
 */
@RestController
@RequestMapping("/api/v1/evidencias")
public class EvidenciaController {

    private final EvidenciaService service;

    public EvidenciaController(EvidenciaService service) {
        this.service = service;
    }

    @RequiredPermission("evidencia:upload")
    @PreAuthorize("@authz.has('evidencia:upload')")
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<EvidenciaResponse> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam("jrvId") Long jrvId,
            @RequestParam("cargoId") Long cargoId) {

        Long userId = CurrentUser.staticId();
        Evidencia e = service.upload(file, jrvId, cargoId, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(EvidenciaResponse.from(e));
    }

    @RequiredPermission("evidencia:read")
    @PreAuthorize("@authz.has('evidencia:read')")
    @GetMapping("/{id}")
    public EvidenciaResponse get(@PathVariable Long id) {
        return EvidenciaResponse.from(service.get(id));
    }

    @RequiredPermission("evidencia:download")
    @PreAuthorize("@authz.has('evidencia:download')")
    @GetMapping("/{id}/download-url")
    public DownloadUrlResponse downloadUrl(@PathVariable Long id) {
        String url = service.presignDownload(id);
        return new DownloadUrlResponse(id, url);
    }

    @RequiredPermission({"acta:aprobar", "acta:rechazar"})
    @PreAuthorize("@authz.hasAny('acta:aprobar','acta:rechazar')")
    @PatchMapping("/{id}/estado")
    public EvidenciaResponse cambiarEstado(
            @PathVariable Long id,
            @Valid @org.springframework.web.bind.annotation.RequestBody
                    CambiarEstadoRequest body) {
        Evidencia e = service.cambiarEstado(id, body.estado());
        return EvidenciaResponse.from(e);
    }

    @RequiredPermission("evidencia:delete")
    @PreAuthorize("@authz.has('evidencia:delete')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        service.delete(id, CurrentUser.staticId());
        return ResponseEntity.noContent().build();
    }

    /** Tipico: devolver 201 + Location header. Acu devolvemos la entidad. */
    @SuppressWarnings("unused")
    private static class LocationBuilder {}
}
