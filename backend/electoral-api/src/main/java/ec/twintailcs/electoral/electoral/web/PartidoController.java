package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Partido;
import ec.twintailcs.electoral.electoral.web.dto.PartidoRequest;
import ec.twintailcs.electoral.electoral.web.dto.PartidoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.PartidoMapper;
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
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints REST para la configuracion preelectoral de Partidos.
 *
 * <p>Rutas: GET /api/v1/configuracion/partidos # cualquier usuario autenticado POST
 * /api/v1/configuracion/partidos # MASTER PUT /api/v1/configuracion/partidos/{id} # MASTER DELETE
 * /api/v1/configuracion/partidos/{id} # MASTER
 */
@RestController
@RequestMapping("/api/v1/configuracion/partidos")
public class PartidoController {

  private final ConfiguracionService service;
  private final PartidoMapper mapper;
  private final CurrentUser currentUser;
  private final UsuarioRepository usuarios;

  public PartidoController(
      ConfiguracionService service,
      PartidoMapper mapper,
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
  public List<PartidoResponse> listar() {
    return service.listPartidos().stream().map(mapper::toDto).toList();
  }

  @PreAuthorize("@authz.has('configuracion:write')")
  @PostMapping
  public ResponseEntity<PartidoResponse> crear(
      @Valid @RequestBody PartidoRequest dto, HttpServletRequest request) {
    Partido partido = service.createPartido(dto, masterId(), request);
    return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(partido));
  }

  @PreAuthorize("@authz.has('configuracion:write')")
  @PutMapping("/{id}")
  public PartidoResponse actualizar(
      @PathVariable Long id, @Valid @RequestBody PartidoRequest dto, HttpServletRequest request) {
    Partido partido = service.updatePartido(id, dto, masterId(), request);
    return mapper.toDto(partido);
  }

  @PreAuthorize("@authz.has('configuracion:write')")
  @DeleteMapping("/{id}")
  public ResponseEntity<Void> eliminar(@PathVariable Long id, HttpServletRequest request) {
    service.deletePartido(id, masterId(), request);
    return ResponseEntity.noContent().build();
  }
}
