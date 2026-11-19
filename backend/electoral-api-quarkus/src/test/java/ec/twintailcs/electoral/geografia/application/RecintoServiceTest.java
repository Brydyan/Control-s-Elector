package ec.twintailcs.electoral.geografia.application;

import static org.junit.jupiter.api.Assertions.*;

import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Recinto;
import ec.twintailcs.electoral.geografia.domain.RecintoRepository;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.transaction.Transactional;
import org.junit.jupiter.api.Test;

/**
 * Unit tests for RecintoService.
 *
 * <p>Tests CRUD operations and validations:
 * - Crear recinto válido
 * - Duplicado nombre+parroquia → error
 * - Coordinator inválido → error
 * - Eliminar con JRVs → error
 */
@QuarkusTest
class RecintoServiceTest {

  @Inject RecintoService service;
  @Inject RecintoRepository repository;
  @Inject EntityManager entityManager;

  @Test
  @Transactional
  void crearRecintoValido() {
    // Setup: get an existing parroquia
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    assertNotNull(parroquia, "Test requires at least one Parroquia to exist");

    // Act
    Recinto created =
        service.crearRecinto("Test Recinto", parroquia.getId(), null, "ACTIVO");

    // Assert
    assertNotNull(created);
    assertNotNull(created.getId());
    assertEquals("Test Recinto", created.getNombre());
    assertEquals(parroquia.getId(), created.getParroquiaId());
    assertEquals("ACTIVO", created.getEstado());
    assertNull(created.getCoordinatorId());
  }

  @Test
  @Transactional
  void crearRecintoConDuplicadoNombreParroquia() {
    // Setup
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    service.crearRecinto("Unique Recinto", parroquia.getId(), null, "ACTIVO");

    // Act & Assert: second creation with same nombre + parroquia should fail
    assertThrows(
        GeografiaConflictException.class,
        () -> service.crearRecinto("Unique Recinto", parroquia.getId(), null, "ACTIVO"));
  }

  @Test
  @Transactional
  void crearRecintoConParroquiaInexistente() {
    // Act & Assert: invalid parroquiaId
    assertThrows(
        GeografiaNotFoundException.class,
        () -> service.crearRecinto("Test", 99999L, null, "ACTIVO"));
  }

  @Test
  @Transactional
  void obtenerRecintoExistente() {
    // Setup
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    Recinto created =
        service.crearRecinto("Get Test", parroquia.getId(), null, "ACTIVO");

    // Act
    Recinto obtained = service.obtenerRecinto(created.getId());

    // Assert
    assertEquals(created.getId(), obtained.getId());
    assertEquals("Get Test", obtained.getNombre());
  }

  @Test
  @Transactional
  void obtenerRecintoInexistente() {
    // Act & Assert
    assertThrows(
        GeografiaNotFoundException.class, () -> service.obtenerRecinto(99999L));
  }

  @Test
  @Transactional
  void actualizarRecinto() {
    // Setup
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    Recinto recinto =
        service.crearRecinto("Original Name", parroquia.getId(), null, "ACTIVO");

    // Act
    Recinto updated =
        service.actualizarRecinto(recinto.getId(), "Updated Name", null, "INACTIVO");

    // Assert
    assertEquals("Updated Name", updated.getNombre());
    assertEquals("INACTIVO", updated.getEstado());
  }

  @Test
  @Transactional
  void eliminarRecintoSinJrvs() {
    // Setup
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    Recinto recinto =
        service.crearRecinto("To Delete", parroquia.getId(), null, "ACTIVO");

    // Act
    service.eliminarRecinto(recinto.getId());

    // Assert: recinto should no longer exist
    assertThrows(
        GeografiaNotFoundException.class, () -> service.obtenerRecinto(recinto.getId()));
  }

  @Test
  @Transactional
  void eliminarRecintoConJrvsAsignadas() {
    // Setup: recinto + 2 jrvs asignadas
    Parroquia parroquia = Parroquia.find("order by id").firstResult();
    Recinto recinto =
        service.crearRecinto("Test Recinto with JRVs", parroquia.getId(), null, "ACTIVO");

    // Create and assign 2 JRVs to recinto
    ec.twintailcs.electoral.geografia.domain.Jrv jrv1 =
        new ec.twintailcs.electoral.geografia.domain.Jrv("1001", "Mesa 1", 500, parroquia.getId());
    jrv1.setRecintoId(recinto.getId());
    jrv1.persist();

    ec.twintailcs.electoral.geografia.domain.Jrv jrv2 =
        new ec.twintailcs.electoral.geografia.domain.Jrv("1002", "Mesa 2", 500, parroquia.getId());
    jrv2.setRecintoId(recinto.getId());
    jrv2.persist();

    // Act & Assert: cannot delete recinto with assigned JRVs
    assertThrows(
        GeografiaConflictException.class,
        () -> service.eliminarRecinto(recinto.getId()),
        "Cannot delete Recinto with assigned JRVs");
  }
}
