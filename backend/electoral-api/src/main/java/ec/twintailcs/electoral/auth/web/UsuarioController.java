package ec.twintailcs.electoral.auth.web;

import ec.twintailcs.electoral.auth.application.EmailGenerator;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.auth.web.dto.CreateUsuarioRequest;
import ec.twintailcs.electoral.auth.web.dto.UpdateUsuarioRequest;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import ec.twintailcs.electoral.shared.web.pagination.PageRequestFactory;
import ec.twintailcs.electoral.shared.web.pagination.PagedResponse;
import jakarta.validation.Valid;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
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
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/admin/usuarios")
public class UsuarioController {

  private static final Set<String> SORTABLE_FIELDS =
      Set.of("creadoEn", "email", "apellido", "nombre");
  private static final SecureRandom RANDOM = new SecureRandom();
  private final UsuarioRepository usuarios;
  private final RolRepository roles;
  private final EmailGenerator emailGenerator;
  private final PasswordHasher passwordHasher;
  private final UsuarioMapper mapper;

  public UsuarioController(
      UsuarioRepository usuarios,
      RolRepository roles,
      EmailGenerator emailGenerator,
      PasswordHasher passwordHasher,
      UsuarioMapper mapper) {
    this.usuarios = usuarios;
    this.roles = roles;
    this.emailGenerator = emailGenerator;
    this.passwordHasher = passwordHasher;
    this.mapper = mapper;
  }

  @GetMapping
  @PreAuthorize("hasAuthority('usuario:read') or hasRole('MASTER')")
  public PagedResponse<UsuarioResponseDto> listar(
      @RequestParam(defaultValue = "0") int page,
      @RequestParam(defaultValue = "20") int size,
      @RequestParam(defaultValue = "creadoEn,desc") String sort,
      @RequestParam(required = false) String rol,
      @RequestParam(required = false) Boolean habilitado) {
    Pageable pageable = pageable(page, size, sort, "creadoEn");
    Page<Usuario> result;
    if (rol != null && habilitado != null) {
      result = usuarios.findByRol_NombreAndHabilitado(rol, habilitado, pageable);
    } else if (rol != null) {
      result = usuarios.findByRol_Nombre(rol, pageable);
    } else if (habilitado != null) {
      result = usuarios.findByHabilitado(habilitado, pageable);
    } else {
      result = usuarios.findAll(pageable);
    }
    return PagedResponse.of(result.map(mapper::toAdminDto));
  }

  @GetMapping("/{publicId}")
  @PreAuthorize("hasAuthority('usuario:read') or hasRole('MASTER')")
  public UsuarioResponseDto obtener(@PathVariable UUID publicId) {
    return mapper.toAdminDto(findUser(publicId));
  }

  @PostMapping
  @PreAuthorize("hasAuthority('usuario:create') or hasRole('MASTER')")
  public ResponseEntity<UsuarioResponseDto> crear(
      @Valid @RequestBody CreateUsuarioRequest request) {
    Rol rol = findRole(request.rolNombre());
    String password = temporaryPassword();
    Usuario usuario = new Usuario();
    usuario.setNombre(request.nombre());
    usuario.setApellido(request.apellido());
    usuario.setEmail(
        emailGenerator.generar(request.nombre(), request.apellido(), request.rolNombre()));
    usuario.setPasswordHash(passwordHasher.hash(password));
    usuario.setRol(rol);
    usuario.setHabilitado(true);
    usuario.validarDatosObligatorios();
    Usuario saved = usuarios.save(usuario);
    UsuarioResponseDto response = mapper.toAdminDto(saved);
    response = withTemporaryPassword(response, password);
    return ResponseEntity.status(HttpStatus.CREATED).body(response);
  }

  @PutMapping("/{publicId}")
  @PreAuthorize("hasAuthority('usuario:update') or hasRole('MASTER')")
  public UsuarioResponseDto actualizar(
      @PathVariable UUID publicId, @Valid @RequestBody UpdateUsuarioRequest request) {
    Usuario usuario = findUser(publicId);
    usuario.setNombre(request.nombre());
    usuario.setApellido(request.apellido());
    usuario.setRol(findRole(request.rolNombre()));
    usuario.setHabilitado(request.habilitado());
    return mapper.toAdminDto(usuarios.save(usuario));
  }

  @DeleteMapping("/{publicId}")
  @PreAuthorize("hasAuthority('usuario:delete') or hasRole('MASTER')")
  public ResponseEntity<Void> deshabilitar(@PathVariable UUID publicId) {
    Usuario usuario = findUser(publicId);
    usuario.setHabilitado(false);
    usuarios.save(usuario);
    return ResponseEntity.noContent().build();
  }

  private Usuario findUser(UUID publicId) {
    return usuarios.findByPublicId(publicId).orElseThrow(() -> notFound());
  }

  private Rol findRole(String name) {
    return roles
        .findByNombre(name)
        .orElseThrow(() -> new IllegalArgumentException("rol invalido: " + name));
  }

  private static String temporaryPassword() {
    byte[] bytes = new byte[18];
    RANDOM.nextBytes(bytes);
    return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
  }

  private static UsuarioResponseDto withTemporaryPassword(
      UsuarioResponseDto response, String password) {
    return new UsuarioResponseDto(
        response.publicId(),
        response.email(),
        response.nombre(),
        response.apellido(),
        response.rolNombre(),
        response.habilitado(),
        response.creadoEn(),
        password);
  }

  private static ResponseStatusException notFound() {
    return new ResponseStatusException(HttpStatus.NOT_FOUND, "Usuario no encontrado");
  }

  private static Pageable pageable(int page, int size, String sort, String defaultField) {
    String[] parts = sort.split(",", 2);
    String direction = parts.length == 2 ? parts[1] : "desc";
    return PageRequestFactory.from(page, size, parts[0], direction, SORTABLE_FIELDS, defaultField);
  }
}
