package ec.twintailcs.electoral.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@DataJpaTest
@Testcontainers
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class SesionRepositoryTest {

  @Container
  static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

  @DynamicPropertySource
  static void configureProperties(DynamicPropertyRegistry registry) {
    registry.add("spring.datasource.url", postgres::getJdbcUrl);
    registry.add("spring.datasource.username", postgres::getUsername);
    registry.add("spring.datasource.password", postgres::getPassword);
    registry.add("spring.liquibase.enabled", () -> "true");
  }

  @Autowired private SesionRepository repository;
  @Autowired private UsuarioRepository usuarios;
  @Autowired private RolRepository roles;

  @Test
  void findByRevocadoFalseAndExpiraEnAfter_excluyeRevocadasYExpiradas() {
    Long usuarioId = usuarioId();
    save(usuarioId, false, Instant.now().plusSeconds(3600));
    save(usuarioId, true, Instant.now().plusSeconds(3600));
    save(usuarioId, false, Instant.now().minusSeconds(1));

    assertThat(
            repository.findByRevocadoFalseAndExpiraEnAfter(
                Instant.now(), org.springframework.data.domain.PageRequest.of(0, 10)))
        .hasSize(1);
  }

  @Test
  void findByUsuarioId_filtraPorUsuario() {
    Long first = usuarioId();
    Long second = usuarioId();
    save(first, false, Instant.now().plusSeconds(3600));
    save(second, false, Instant.now().plusSeconds(3600));

    assertThat(
            repository.findByUsuarioIdAndRevocadoFalseAndExpiraEnAfter(
                first, Instant.now(), org.springframework.data.domain.PageRequest.of(0, 10)))
        .hasSize(1)
        .allMatch(s -> s.getUsuarioId().equals(first));
  }

  @Test
  void findByRevocadoFalse_pagina() {
    Long usuarioId = usuarioId();
    save(usuarioId, false, Instant.now().plusSeconds(3600));
    save(usuarioId, false, Instant.now().plusSeconds(3600));

    assertThat(
            repository
                .findByRevocadoFalse(org.springframework.data.domain.PageRequest.of(0, 1))
                .getContent())
        .hasSize(1);
  }

  private Long usuarioId() {
    Rol rol = roles.findByNombre("OBSERVER").orElseThrow();
    Usuario usuario = new Usuario();
    usuario.setEmail(UUID.randomUUID() + "@example.com");
    usuario.setNombre("Test");
    usuario.setApellido("User");
    usuario.setPasswordHash("hash");
    usuario.setRol(rol);
    return usuarios.saveAndFlush(usuario).getId();
  }

  private void save(Long usuarioId, boolean revoked, Instant expiresAt) {
    Sesion session = new Sesion();
    session.setUsuarioId(usuarioId);
    session.setSesionId(UUID.randomUUID().toString());
    session.setRefreshHash(UUID.randomUUID().toString());
    session.setRevocado(revoked);
    session.setExpiraEn(expiresAt);
    repository.saveAndFlush(session);
  }
}
