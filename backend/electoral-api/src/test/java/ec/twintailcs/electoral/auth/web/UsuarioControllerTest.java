package ec.twintailcs.electoral.auth.web;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.auth.application.EmailGenerator;
import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.application.PasswordHasher;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.auth.web.dto.UsuarioResponseDto;
import ec.twintailcs.electoral.auth.web.dto.mapper.UsuarioMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(controllers = UsuarioController.class)
@AutoConfigureMockMvc(addFilters = false)
class UsuarioControllerTest {

  @Autowired private MockMvc mockMvc;

  @MockitoBean private UsuarioRepository usuarios;

  @MockitoBean private RolRepository roles;

  // JwtAuthFilter requiere JwtService en su constructor; mockeamos para que
  // ApplicationContext pueda construir el filter chain aunque esté deshabilitado
  // por addFilters = false.
  @MockitoBean private JwtService jwtService;

  @MockitoBean private EmailGenerator emailGenerator;

  @MockitoBean private PasswordHasher passwordHasher;

  @MockitoBean private UsuarioMapper usuarioMapper;

  private Usuario usuarioMock;
  private Rol rolMaster;

  @BeforeEach
  void setUp() {
    rolMaster = new Rol("MASTER");

    usuarioMock = new Usuario();
    usuarioMock.setId(1L);
    usuarioMock.setPublicId(UUID.randomUUID());
    usuarioMock.setEmail("coord@test.local");
    usuarioMock.setNombre("Coord");
    usuarioMock.setApellido("Test");
    usuarioMock.setRol(rolMaster);
    usuarioMock.setHabilitado(true);
    usuarioMock.setCreadoEn(Instant.now());

    Rol rolObserver = new Rol("OBSERVER");
    when(roles.findByNombre("MASTER")).thenReturn(Optional.of(rolMaster));
    when(roles.findByNombre("OBSERVER")).thenReturn(Optional.of(rolObserver));

    when(usuarioMapper.toAdminDto(any(Usuario.class)))
        .thenAnswer(
            inv -> {
              Usuario u = inv.getArgument(0);
              UsuarioResponseDto dto =
                  new UsuarioResponseDto(
                      u.getPublicId(),
                      u.getEmail(),
                      u.getNombre(),
                      u.getApellido(),
                      u.getRol() != null ? u.getRol().getNombre() : null,
                      u.isHabilitado(),
                      u.getCreadoEn());
              return dto;
            });

    when(emailGenerator.generar(any(String.class), any(String.class), any(String.class)))
        .thenReturn("nuevo.coordinator@test.local");
    when(passwordHasher.hash(any(String.class))).thenReturn("$2a$10$fakehash");
    when(passwordHasher.matches(any(String.class), any(String.class))).thenReturn(true);
  }

  @Test
  void listar_returnsPagedUsuarios() throws Exception {
    Page<Usuario> page = new PageImpl<>(List.of(usuarioMock));
    when(usuarios.findAll(any(Pageable.class))).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/admin/usuarios"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data[0].email").value("coord@test.local"))
        .andExpect(jsonPath("$.data[0].rolNombre").value("MASTER"));
  }

  @Test
  void listar_conFiltroRol_filtraPorRol() throws Exception {
    Page<Usuario> page = new PageImpl<>(List.of(usuarioMock));
    when(usuarios.findByRol_Nombre(eq("MASTER"), any(Pageable.class))).thenReturn(page);

    mockMvc
        .perform(get("/api/v1/admin/usuarios").param("rol", "MASTER"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.data.length()").value(1));
  }

  @Test
  void obtener_retornaUsuarioExistente() throws Exception {
    UUID publicId = usuarioMock.getPublicId();
    when(usuarios.findByPublicId(publicId)).thenReturn(Optional.of(usuarioMock));

    mockMvc
        .perform(get("/api/v1/admin/usuarios/" + publicId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("coord@test.local"));
  }

  @Test
  void obtener_inexistente_returns404() throws Exception {
    UUID id = UUID.randomUUID();
    when(usuarios.findByPublicId(id)).thenReturn(Optional.empty());

    mockMvc.perform(get("/api/v1/admin/usuarios/" + id)).andExpect(status().isNotFound());
  }

  @Test
  void crear_generaEmailYPasswordTemporal_returns201() throws Exception {
    when(usuarios.save(any(Usuario.class)))
        .thenAnswer(
            inv -> {
              Usuario u = inv.getArgument(0);
              u.setId(99L);
              u.setCreadoEn(Instant.now());
              return u;
            });

    String body = "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"rolNombre\":\"OBSERVER\"}";

    mockMvc
        .perform(
            post("/api/v1/admin/usuarios").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isCreated())
        .andExpect(jsonPath("$.email").exists())
        .andExpect(jsonPath("$.passwordTemporal").exists())
        .andExpect(jsonPath("$.rolNombre").value("OBSERVER"));
  }

  @Test
  void crear_rolInvalido_returns400() throws Exception {
    when(roles.findByNombre("NO_EXISTE")).thenReturn(Optional.empty());

    String body = "{\"nombre\":\"Ana\",\"apellido\":\"Perez\",\"rolNombre\":\"NO_EXISTE\"}";

    mockMvc
        .perform(
            post("/api/v1/admin/usuarios").contentType(MediaType.APPLICATION_JSON).content(body))
        .andExpect(status().isBadRequest());
  }

  @Test
  void actualizar_actualizaDatos_retornaUsuario() throws Exception {
    UUID publicId = usuarioMock.getPublicId();
    when(usuarios.findByPublicId(publicId)).thenReturn(Optional.of(usuarioMock));
    when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    String body =
        "{\"nombre\":\"Ana Updated\",\"apellido\":\"Perez\",\"rolNombre\":\"COORDINATOR\",\"habilitado\":true}";

    when(roles.findByNombre("COORDINATOR")).thenReturn(Optional.of(rolMaster));

    mockMvc
        .perform(
            put("/api/v1/admin/usuarios/" + publicId)
                .contentType(MediaType.APPLICATION_JSON)
                .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.nombre").value("Ana Updated"));
  }

  @Test
  void deshabilitar_marcaComoHabilitadoFalse_returns204() throws Exception {
    UUID publicId = usuarioMock.getPublicId();
    when(usuarios.findByPublicId(publicId)).thenReturn(Optional.of(usuarioMock));
    when(usuarios.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

    mockMvc.perform(delete("/api/v1/admin/usuarios/" + publicId)).andExpect(status().isNoContent());
  }
}
