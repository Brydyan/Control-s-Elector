package ec.twintailcs.electoral.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertThrows;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UsuarioRepositoryTest {

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        registry.add("spring.liquibase.enabled", () -> "true");
    }

    @Autowired
    private UsuarioRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private RolRepository rolRepository;

    private Rol obtenerRol(String nombre) {
        return rolRepository.findByNombre(nombre)
                .orElseThrow(() -> new RuntimeException(
                        "Rol " + nombre + " no encontrado. Liquibase debe haberlo sembrado."));
    }

    @Test
    void shouldAutoGenerateUuidV7AndRoundTrip() {
        Usuario usuario = new Usuario();
        usuario.setEmail("test@example.com");
        usuario.setPasswordHash("hashedpassword");
        usuario.setRol(obtenerRol("OBSERVER"));
        usuario.setHabilitado(true);

        Usuario saved = repository.saveAndFlush(usuario);

        assertThat(saved.getId()).isNotNull();
        UUID publicId = saved.getPublicId();
        assertThat(publicId).isNotNull();
        assertThat(publicId.version()).isEqualTo(7);

        Usuario found = repository.findByPublicId(publicId).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldThrowExceptionWhenDuplicatePublicIdIsPersisted() {
        Rol rol = obtenerRol("OBSERVER");

        Usuario u1 = new Usuario();
        u1.setEmail("user1@example.com");
        u1.setPasswordHash("hashed1");
        u1.setRol(rol);

        Usuario saved1 = repository.saveAndFlush(u1);
        UUID publicId = saved1.getPublicId();

        assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                jdbcTemplate.update(
                    "INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, public_id) " +
                    "VALUES ('user2@example.com', 'hashed2', ?, true, NOW(), ?)",
                    rol.getId(), publicId
                );
            }
        );
    }

    @Test
    void shouldNotUpdatePublicIdWhenEntityIsModified() {
        Usuario usuario = new Usuario();
        usuario.setEmail("immutable@example.com");
        usuario.setPasswordHash("hashed");
        usuario.setRol(obtenerRol("OBSERVER"));

        Usuario saved = repository.saveAndFlush(usuario);
        UUID originalPublicId = saved.getPublicId();

        UUID newPublicId = UUID.randomUUID();
        saved.setPublicId(newPublicId);
        repository.saveAndFlush(saved);

        entityManager.clear();

        Usuario retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getPublicId()).isEqualTo(originalPublicId);
    }
}
