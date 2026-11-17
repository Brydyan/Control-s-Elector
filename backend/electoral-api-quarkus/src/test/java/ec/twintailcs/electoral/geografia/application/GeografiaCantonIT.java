package ec.twintailcs.electoral.geografia.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.GeografiaTestProfile;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CRUD de cantones contra una base real.
 *
 * <p>El borrado con hijos y la unicidad del codigo se apoyan en la base (indice unico y conteo de
 * parroquias), asi que un test sin base real no probaria nada.
 */
@QuarkusTest
@TestProfile(GeografiaTestProfile.class)
class GeografiaCantonIT {

  @Inject GeografiaService service;
  @Inject EntityManager em;

  private Long provinciaId() {
    Provincia provincia = Provincia.find("nombre", "Santa Elena").firstResult();
    return provincia == null ? null : provincia.getId();
  }

  private Long masterId() {
    return em.createQuery("select u.id from Usuario u where u.email = :email", Long.class)
        .setParameter("email", "mastersolon@example.local")
        .getSingleResult();
  }

  @Test
  @DisplayName("crea un canton valido bajo la provincia")
  void crearCantonValido() {
    Long provincia = provinciaId();

    Canton canton =
        service.crearCanton(
            new CantonRequest("Canton de prueba " + UUID.randomUUID(), "9901", provincia),
            masterId(),
            "127.0.0.1");

    assertThat(canton.getId()).isNotNull();
    assertThat(canton.getNombre()).startsWith("Canton de prueba");
    assertThat(canton.getCodigo()).isEqualTo("9901");
    assertThat(canton.getProvinciaId()).isEqualTo(provincia);
  }

  @Test
  @DisplayName("rechaza un codigo repetido en la misma provincia")
  void crearCantonCodigoDuplicado() {
    Long provincia = provinciaId();
    // "2401" es el codigo del canton Santa Elena del seed, en la misma provincia.
    CantonRequest dto = new CantonRequest("Duplicado", "2401", provincia);

    assertThatThrownBy(() -> service.crearCanton(dto, masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("2401");
  }

  @Test
  @DisplayName("rechaza una provincia que no existe")
  void crearCantonConProvinciaInexistente() {
    CantonRequest dto = new CantonRequest("Huerfano", "9902", 999_999L);

    assertThatThrownBy(() -> service.crearCanton(dto, masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }

  @Test
  @DisplayName("no deja eliminar un canton que todavia tiene parroquias")
  void eliminarCantonConParroquias() {
    Long provincia = provinciaId();
    Canton canton =
        service.crearCanton(
            new CantonRequest("Con hijos " + UUID.randomUUID(), "9903", provincia),
            masterId(),
            "127.0.0.1");

    Parroquia parroquia =
        QuarkusTransaction.requiringNew()
            .call(
                () -> {
                  Parroquia p =
                      new Parroquia("Parroquia hija " + UUID.randomUUID(), true, canton.getId());
                  p.persist();
                  return p;
                });

    assertThatThrownBy(() -> service.eliminarCanton(canton.getId(), masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("parroquias")
        .satisfies(
            ex ->
                assertThat(((GeografiaConflictException) ex).getCampos())
                    .containsEntry("childrenCount", "1"));
  }

  @Test
  @DisplayName("elimina un canton sin parroquia")
  void eliminarCantonSinParroquias() {
    Long provincia = provinciaId();
    Canton canton =
        service.crearCanton(
            new CantonRequest("Sin hijos " + UUID.randomUUID(), "9904", provincia),
            masterId(),
            "127.0.0.1");

    service.eliminarCanton(canton.getId(), masterId(), "127.0.0.1");

    assertThat(em.find(Canton.class, canton.getId())).isNull();
  }

  @Test
  @DisplayName("edita el nombre y el codigo del canton")
  void actualizarCanton() {
    Long provincia = provinciaId();
    Canton canton =
        service.crearCanton(
            new CantonRequest("Antes " + UUID.randomUUID(), "9905", provincia),
            masterId(),
            "127.0.0.1");

    Canton editado =
        service.actualizarCanton(
            canton.getId(),
            new CantonRequest("Despues " + UUID.randomUUID(), "9906", provincia),
            masterId(),
            "127.0.0.1");

    assertThat(editado.getNombre()).startsWith("Despues");
    assertThat(editado.getCodigo()).isEqualTo("9906");
    assertThat(editado.getId()).isEqualTo(canton.getId());
  }

  @Test
  @DisplayName("no deja editar un canton que no existe")
  void actualizarCantonInexistente() {
    Long provincia = provinciaId();

    assertThatThrownBy(
            () ->
                service.actualizarCanton(
                    999_999L,
                    new CantonRequest("Fantasma", "9907", provincia),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }
}
