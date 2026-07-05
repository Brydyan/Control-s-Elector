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

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class MenuPermisoRepositoryTest {

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
    private MenuPermisoRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldAutoGenerateUuidV7AndRoundTrip() {
        MenuPermiso mp = new MenuPermiso(1L, 1L);
        MenuPermiso saved = repository.saveAndFlush(mp);

        assertThat(saved.getId()).isNotNull();
        UUID publicId = saved.getPublicId();
        assertThat(publicId).isNotNull();
        assertThat(publicId.version()).isEqualTo(7);

        MenuPermiso found = repository.findByPublicId(publicId).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldThrowExceptionWhenDuplicatePublicIdIsPersisted() {
        MenuPermiso mp1 = new MenuPermiso(10L, 10L);
        MenuPermiso saved1 = repository.saveAndFlush(mp1);
        UUID publicId = saved1.getPublicId();

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                jdbcTemplate.update(
                    "INSERT INTO menu_permiso (menu_id, permiso_id, public_id) " +
                    "VALUES (11, 11, ?)",
                    publicId
                );
            }
        );
    }

    @Test
    void shouldNotUpdatePublicIdWhenEntityIsModified() {
        MenuPermiso mp = new MenuPermiso(20L, 20L);
        MenuPermiso saved = repository.saveAndFlush(mp);
        UUID originalPublicId = saved.getPublicId();

        UUID newPublicId = UUID.randomUUID();
        saved.setPublicId(newPublicId);
        repository.saveAndFlush(saved);

        entityManager.clear();

        MenuPermiso retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getPublicId()).isEqualTo(originalPublicId);
    }
}
