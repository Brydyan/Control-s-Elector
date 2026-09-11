package ec.twintailcs.electoral.evidencia.domain;

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

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class EvidenciaRepositoryTest {

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
    private EvidenciaRepository repository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private jakarta.persistence.EntityManager entityManager;

    /**
     * Liquibase deja las tablas vacias para evidencia/jrv/cargo/usuario
     * (no hay seed automatico). Como evidencia.usuario_id tiene FK a
     * usuario.id (changelog 005-07) y los helpers first*Id() necesitan
     * filas existentes, sembramos manualmente un set minimo antes de
     * cada test. Las tablas jrv y cargo no tienen FK hacia evidencia,
     * pero las sembramos igual para que los helpers no fallen.
     */
    @BeforeEach
    void seedParentRows() {
        Long rolId = jdbcTemplate.queryForObject("SELECT id FROM rol LIMIT 1", Long.class);
        jdbcTemplate.update(
            "INSERT INTO provincia (nombre, codigo) VALUES ('SE-TEST', '24')");
        Long provinciaId = jdbcTemplate.queryForObject(
            "SELECT id FROM provincia WHERE codigo='24'", Long.class);
        jdbcTemplate.update(
            "INSERT INTO canton (nombre, codigo, provincia_id) VALUES ('CT-TEST', '2401', ?)",
            provinciaId);
        Long cantonId = jdbcTemplate.queryForObject(
            "SELECT id FROM canton WHERE codigo='2401'", Long.class);
        jdbcTemplate.update(
            "INSERT INTO parroquia (nombre, es_urbana, canton_id) VALUES ('PR-TEST', true, ?)",
            cantonId);
        Long parroquiaId = jdbcTemplate.queryForObject(
            "SELECT id FROM parroquia WHERE nombre='PR-TEST'", Long.class);
        jdbcTemplate.update(
            "INSERT INTO jrv (numero, recinto, total_electores, parroquia_id) VALUES ('JRV-TEST', 'Recinto Test', 100, ?)",
            parroquiaId);
        jdbcTemplate.update(
            "INSERT INTO cargo (nombre, nivel, puestos) VALUES ('Cargo Test', 'PROVINCIA', 1)");
        jdbcTemplate.update(
            "INSERT INTO usuario (email, password_hash, rol_id, habilitado, creado_en, nombre, apellido, public_id) "
          + "VALUES ('evidencia-test@test.local', 'hash', ?, true, NOW(), 'Test', 'User', ?)",
            rolId, UUID.randomUUID());
    }

    @Test
    void shouldAutoGenerateUuidV7AndRoundTrip() {
        Evidencia evidencia = new Evidencia();
        evidencia.setJrvId(firstJrvId());
        evidencia.setCargoId(firstCargoId());
        evidencia.setUsuarioId(firstUsuarioId());
        evidencia.setS3Key("actas/1/2/test.webp");
        evidencia.setSizeBytes(1024L);
        evidencia.setContentType("image/webp");
        evidencia.setSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
        evidencia.setEstado(EstadoEvidencia.EN_REVISION);

        Evidencia saved = repository.saveAndFlush(evidencia);

        assertThat(saved.getId()).isNotNull();
        UUID publicId = saved.getPublicId();
        assertThat(publicId).isNotNull();
        assertThat(publicId.version()).isEqualTo(7);

        Evidencia found = repository.findByPublicId(publicId).orElseThrow();
        assertThat(found.getId()).isEqualTo(saved.getId());
    }

    @Test
    void shouldThrowExceptionWhenDuplicatePublicIdIsPersisted() {
        Evidencia ev1 = new Evidencia();
        ev1.setJrvId(firstJrvId());
        ev1.setCargoId(firstCargoId());
        ev1.setUsuarioId(firstUsuarioId());
        ev1.setS3Key("actas/1/2/test1.webp");
        ev1.setSizeBytes(1024L);
        ev1.setContentType("image/webp");
        ev1.setSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        Evidencia saved1 = repository.saveAndFlush(ev1);
        UUID publicId = saved1.getPublicId();

        // Forzamos un insert SQL nativo con JdbcTemplate para evitar que Hibernate intercepte y autogenere un UUID nuevo.
        // FKs padres (jrv, cargo, usuario) son validos (los mismos del primer insert); la tabla evidencia
        // NO tiene UNIQUE (jrv_id, cargo_id, usuario_id), asi que reusarlos es seguro y dispara
        // uk_evidencia_public_id (la constraint que el test pretende validar).
        org.junit.jupiter.api.Assertions.assertThrows(
            org.springframework.dao.DataIntegrityViolationException.class,
            () -> {
                jdbcTemplate.update(
                    "INSERT INTO evidencia (jrv_id, cargo_id, usuario_id, s3_key, size_bytes, content_type, sha256, estado, version, creado_en, actualizado_en, public_id) " +
                    "VALUES (?, ?, ?, 'actas/1/2/test2.webp', 1024, 'image/webp', 'e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b856', 'EN_REVISION', 0, NOW(), NOW(), ?)",
                    firstJrvId(), firstCargoId(), firstUsuarioId(), publicId
                );
            }
        );
    }

    @Test
    void shouldNotUpdatePublicIdWhenEntityIsModified() {
        Evidencia evidencia = new Evidencia();
        evidencia.setJrvId(firstJrvId());
        evidencia.setCargoId(firstCargoId());
        evidencia.setUsuarioId(firstUsuarioId());
        evidencia.setS3Key("actas/1/2/test_immutability.webp");
        evidencia.setSizeBytes(1024L);
        evidencia.setContentType("image/webp");
        evidencia.setSha256("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");

        Evidencia saved = repository.saveAndFlush(evidencia);
        UUID originalPublicId = saved.getPublicId();

        // Intentamos cambiar el publicId
        UUID newPublicId = UUID.randomUUID();
        saved.setPublicId(newPublicId);
        repository.saveAndFlush(saved);

        // Despejamos el EntityManager para forzar una consulta fresca a la DB
        entityManager.clear();

        Evidencia retrieved = repository.findById(saved.getId()).orElseThrow();
        // El publicId debe seguir siendo el original debido a updatable = false
        assertThat(retrieved.getPublicId()).isEqualTo(originalPublicId);
    }

    private Long firstJrvId() { return jdbcTemplate.queryForObject("SELECT id FROM jrv ORDER BY id LIMIT 1", Long.class); }
    private Long firstCargoId() { return jdbcTemplate.queryForObject("SELECT id FROM cargo ORDER BY id LIMIT 1", Long.class); }
    private Long firstUsuarioId() { return jdbcTemplate.queryForObject("SELECT id FROM usuario ORDER BY id LIMIT 1", Long.class); }
}
