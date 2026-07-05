package ec.twintailcs.electoral.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
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
class RolPermisoRepositoryTest {

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
    private RolPermisoRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Autowired
    private RolRepository rolRepository;

    private Rol crearRol(String nombre) {
        return rolRepository.save(new Rol(nombre));
    }

    @Test
    void shouldAutoGenerateUuidV7AndRoundTrip() {
        Rol rol = crearRol("TEST_ROL");
        RolPermiso rp = new RolPermiso(rol, 1L);
        RolPermiso saved = repository.saveAndFlush(rp);

        assertThat(saved.getId()).isNotNull();
        UUID publicId = saved.getPublicId();
        assertThat(publicId).isNotNull();
        assertThat(publicId.version()).isEqualTo(7);

        RolPermiso found = repository.findByPublicId(publicId).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldThrowExceptionWhenDuplicatePublicIdIsPersisted() {
        Rol rol1 = crearRol("TEST_ROL1");
        RolPermiso rp1 = new RolPermiso(rol1, 1L);
        RolPermiso saved1 = repository.saveAndFlush(rp1);
        UUID publicId = saved1.getPublicId();

        Rol rol2 = crearRol("TEST_ROL2");

        assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                jdbcTemplate.update(
                    "INSERT INTO rol_permiso (rol_id, permiso_id, public_id) " +
                    "VALUES (?, 2, ?)",
                    rol2.getId(), publicId
                );
            }
        );
    }

    @Test
    void shouldNotUpdatePublicIdWhenEntityIsModified() {
        Rol rol = crearRol("TEST_ROL_IMM");
        RolPermiso rp = new RolPermiso(rol, 1L);
        RolPermiso saved = repository.saveAndFlush(rp);
        UUID originalPublicId = saved.getPublicId();

        UUID newPublicId = UUID.randomUUID();
        saved.setPublicId(newPublicId);
        repository.saveAndFlush(saved);

        entityManager.clear();

        RolPermiso retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getPublicId()).isEqualTo(originalPublicId);
    }
}
