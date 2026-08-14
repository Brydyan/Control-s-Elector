package ec.twintailcs.electoral.electoral.web;

import ec.twintailcs.electoral.auth.config.CurrentUser;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.electoral.application.ConfiguracionService;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.web.dto.CargoRequest;
import ec.twintailcs.electoral.electoral.web.dto.CargoResponse;
import ec.twintailcs.electoral.electoral.web.dto.mapper.CargoMapper;
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
 * Endpoints REST para la configuracion preelectoral de Cargos.
 *
 * Rutas:
 *   GET    /api/v1/configuracion/cargos       # cualquier usuario autenticado
 *   POST   /api/v1/configuracion/cargos       # MASTER
 *   PUT    /api/v1/configuracion/cargos/{id}  # MASTER
 *   DELETE /api/v1/configuracion/cargos/{id}  # MASTER
 */
@RestController
@RequestMapping("/api/v1/configuracion/cargos")
public class CargoController {

    private final ConfiguracionService service;
    private final CargoMapper mapper;
    private final CurrentUser currentUser;
    private final UsuarioRepository usuarios;

    public CargoController(ConfiguracionService service, CargoMapper mapper,
                           CurrentUser currentUser, UsuarioRepository usuarios) {
        this.service = service;
        this.mapper = mapper;
        this.currentUser = currentUser;
        this.usuarios = usuarios;
    }

    private Long masterId() {
        return usuarios.findByPublicId(currentUser.id())
            .orElseThrow(() -> new IllegalArgumentException("Usuario no existe"))
            .getId();
    }

    @PreAuthorize("isAuthenticated()")
    @GetMapping
    public List<CargoResponse> listar() {
        return service.listCargos().stream().map(mapper::toDto).toList();
    }

    @PreAuthorize("@authz.has('configuracion:write')")
    @PostMapping
    public ResponseEntity<CargoResponse> crear(@Valid @RequestBody CargoRequest dto,
                                                HttpServletRequest request) {
        Cargo cargo = service.createCargo(dto, masterId(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(mapper.toDto(cargo));
    }

    @PreAuthorize("@authz.has('configuracion:write')")
    @PutMapping("/{id}")
    public CargoResponse actualizar(@PathVariable Long id, @Valid @RequestBody CargoRequest dto,
                                    HttpServletRequest request) {
        Cargo cargo = service.updateCargo(id, dto, masterId(), request);
        return mapper.toDto(cargo);
    }

    @PreAuthorize("@authz.has('configuracion:write')")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id, HttpServletRequest request) {
        service.deleteCargo(id, masterId(), request);
        return ResponseEntity.noContent().build();
    }
}
