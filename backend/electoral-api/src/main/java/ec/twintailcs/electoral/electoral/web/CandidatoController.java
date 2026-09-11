package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CandidatoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CandidatoMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST para la configuracion preelectoral de Candidatos.
 *
 * <p>Rutas: GET /api/v1/configuracion/candidatos?cargoId=&partidoId= # cualquier autenticado,
 * filtros opcionales POST /api/v1/configuracion/candidatos # MASTER PUT
 * /api/v1/configuracion/candidatos/{id} # MASTER DELETE /api/v1/configuracion/candidatos/{id} #
 * MASTER
 */
@RestController
@RequestMapping("/api/v1/configuracion/candidatos")
public class CandidatoController {

  private final ConfiguracionService service;
  private final CandidatoMapper mapper;
  private final CurrentUser currentUser;
  private final UsuarioRepository usuarios;

  public CandidatoController(
      ConfiguracionService service,
      CandidatoMapper mapper,
      CurrentUser currentUser,
      UsuarioRepository usuarios) {
    this.service = service;
    this.mapper = mapper;
    this.currentUser = currentUser;
    this.usuarios = usuarios;
  }

  private Long masterId() {
    return usuarios
        .findByPublicId(currentUser.id())
        .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
        .getId();
  }

  @PreAuthorize("isAuthenticated()")
  @GetMapping
  public List<CandidatoResponse> listar(
      @RequestParam(required = false) Long cargoId,
      @RequestParam(required = false) Long partidoId) {
    return service.listCandidatos(cargoId, partidoId).stream().map(mapper::toDto).toList();
  }

  @PreAuthorize("@authz.has('configuracion:write')")
  @PostMapping
  public ResponseEntity<CandidatoResponse> crear(
      @Valid @RequestBody CandidatoRequest dto, HttpServletRequest request) {
    Candidato candidato = service.createCandidato(dto, masterId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(candidato));
  }

  @PreAuthorize("@authz.has('configuracion:write')")
  @PutMapping("/{id}")
  public CandidatoResponse actualizar(
      @PathVariable Long id, @Valid @RequestBody CandidatoRequest dto, HttpServletRequest request) {
    Candidato candidato = service.updateCandidato(id, dto, masterId(), request);
    return mapper.toDto(candidato);
  }

  @PreAuthorize("@authz.has('configuracion:write')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(@PathVariable Long id, HttpServletRequest request) {
    service.deleteCandidato(id, masterId(), request);
    return ResponseEntity.noContent().build();
  }
}
