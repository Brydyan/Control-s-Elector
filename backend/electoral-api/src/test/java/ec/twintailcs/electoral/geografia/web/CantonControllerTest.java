package ec.twintailcs.electoral.geografia.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import ec.twintailcs.electoral.auth.application.JwtService;
import ec.twintailcs.electoral.auth.domain.Rol;
import ec.twintailcs.electoral.auth.domain.RolRepository;
import ec.twintailcs.electoral.auth.domain.Usuario;
import ec.twintailcs.electoral.auth.domain.UsuarioRepository;
import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.CantonRepository;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.domain.ProvinciaRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@Transactional
class CantonControllerTest {

  @Container
  @SuppressWarnings("resource")
  private static final PostgreSQLContainer<?> postgres =
      new PostgreSQLContainer<>("postgres:16-alpine").withDatabaseName("canton-test-db");

  @DynamicPropertySource
  static void postgresProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
  }

  @Autowired private MockMvc mockMvc;
  @Autowired private JwtService jwtService;
  @Autowired private UsuarioRepository usuarioRepo;
  @Autowired private RolRepository rolRepo;
  @Autowired private CantonRepository cantonRepo;
  @Autowired private ProvinciaRepository provinciaRepo;

  private String token;
  private Canton guayaquil;
  private Canton cuenca;

  @BeforeEach
  void setUp() {
    Rol rolCoordinator =
        rolRepo
            .findByNombre("COORDINATOR")
            .orElseThrow(() -> new IllegalStateException("Rol COORDINATOR no encontrado"));

    Usuario usuario = new Usuario();
    usuario.setEmail("coord-" + UUID.randomUUID() + "@test.local");
    usuario.setNombre("Coord");
    usuario.setApellido("Test");
    usuario.setPasswordHash("hash");
    usuario.setRol(rolCoordinator);
    usuario.setHabilitado(true);
    usuario = usuarioRepo.saveAndFlush(usuario);

    token =
        jwtService
            .issue(usuario.getPublicId(), usuario.getEmail(), "COORDINATOR", List.of(), "sess")
            .access();

    Provincia provincia = provinciaRepo.saveAndFlush(new Provincia("Azuay", "01"));

    guayaquil = cantonRepo.saveAndFlush(canton("Guayaquil", "2401", provincia));
    cuenca = cantonRepo.saveAndFlush(canton("Cuenca", "0101", provincia));
  }

  private Canton canton(String nombre, String codigo, Provincia provincia) {
    Canton c = new Canton();
    c.setNombre(nombre);
    c.setCodigo(codigo);
    c.setProvincia(provincia);
    return c;
  }

  @Test
  void listar_returnsCantonesSortedByNombre() throws Exception {
    mockMvc
        .perform(get("/api/v1/configuracion/cantones").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$").isArray())
        .andExpect(jsonPath("$[0].nombre").value("Cuenca"))
        .andExpect(jsonPath("$[1].nombre").value("Guayaquil"))
        .andExpect(jsonPath("$[0].codigo").value("0101"))
        .andExpect(jsonPath("$[0].id").isNumber());
  }

  @Test
  void listar_sinToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v1/configuracion/cantones")).andExpect(status().isUnauthorized());
  }

  @Test
  void listar_incluyeCodigoNoSoloNombre() throws Exception {
    mockMvc
        .perform(get("/api/v1/configuracion/cantones").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$[0].codigo").exists());
    assertThat(guayaquil.getNombre()).isEqualTo("Guayaquil");
    assertThat(cuenca.getNombre()).isEqualTo("Cuenca");
  }
}
