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
class MenuRepositoryTest {

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
    private MenuRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    @Test
    void shouldAutoGenerateUuidV7AndRoundTrip() {
        Menu menu = new Menu();
        menu.setCodigo("test.menu");
        menu.setLabel("Test Menu");
        menu.setRuta("/test");
        menu.setOrden(1);
        menu.setActivo(true);

        Menu saved = repository.saveAndFlush(menu);

        assertThat(saved.getId()).isNotNull();
        UUID publicId = saved.getPublicId();
        assertThat(publicId).isNotNull();
        assertThat(publicId.version()).isEqualTo(7);

        Menu found = repository.findByPublicId(publicId).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldThrowExceptionWhenDuplicatePublicIdIsPersisted() {
        Menu m1 = new Menu();
        m1.setCodigo("menu1");
        m1.setLabel("Menu 1");
        m1.setRuta("/route1");
        m1.setOrden(1);
        m1.setActivo(true);
        Menu saved1 = repository.saveAndFlush(m1);
        UUID publicId = saved1.getPublicId();

        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                jdbcTemplate.update(
                    "INSERT INTO menu (codigo, label, ruta, orden, activo, public_id) " +
                    "VALUES ('menu2', 'Menu 2', '/route2', 2, true, ?)",
                    publicId
                );
            }
        );
    }

    @Test
    void shouldNotUpdatePublicIdWhenEntityIsModified() {
        Menu menu = new Menu();
        menu.setCodigo("menu.imm");
        menu.setLabel("Menu Imm");
        menu.setRuta("/imm");
        menu.setOrden(1);
        menu.setActivo(true);
        Menu saved = repository.saveAndFlush(menu);
        UUID originalPublicId = saved.getPublicId();

        UUID newPublicId = UUID.randomUUID();
        saved.setPublicId(newPublicId);
        repository.saveAndFlush(saved);

        entityManager.clear();

        Menu retrieved = repository.findById(saved.getId()).orElseThrow();
        assertThat(retrieved.getPublicId()).isEqualTo(originalPublicId);
    }
}
