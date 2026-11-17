package ec.twintailcs.electoral.geografia.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.GeografiaTestProfile;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import ec.twintailcs.electoral.geografia.web.dto.ProvinciaRequest;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CRUD de provincias contra una base real.
 *
 * <p>El codigo unico y el borrado con hijos se apoyan en la base (indice unico y conteo de
 * cantones), asi que un test sin base real no probaria nada.
 */
@QuarkusTest
@TestProfile(GeografiaTestProfile.class)
class GeografiaProvinciaIT {

  @Inject GeografiaService service;
  @Inject EntityManager em;

  private Long masterId() {
    return em.createQuery("select u.id from Usuario u where u.email = :email", Long.class)
        .setParameter("email", "mastersolon@example.local")
        .getSingleResult();
  }

  /** Codigo de provincia unico por test. */
  private String codigoUnico() {
    return "P" + Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000);
  }

  @Test
  @DisplayName("crea una provincia valida con codigo")
  void crearProvinciaValida() {
    String codigo = codigoUnico();
    String nombre = "Provincia de prueba " + UUID.randomUUID();

    Provincia provincia =
        service.crearProvincia(new ProvinciaRequest(nombre, codigo), masterId(), "127.0.0.1");

    assertThat(provincia.getId()).isNotNull();
    assertThat(provincia.getNombre()).isEqualTo(nombre);
    assertThat(provincia.getCodigo()).isEqualTo(codigo);
  }

  @Test
  @DisplayName("crea una provincia sin codigo")
  void crearProvinciaSinCodigo() {
    Provincia provincia =
        service.crearProvincia(
            new ProvinciaRequest("Provincia sin codigo " + UUID.randomUUID(), "  "),
            masterId(),
            "127.0.0.1");

    assertThat(provincia.getId()).isNotNull();
    assertThat(provincia.getCodigo()).isNull();
  }

  @Test
  @DisplayName("rechaza un codigo de provincia repetido")
  void crearProvinciaConCodigoDuplicado() {
    // "24" es el codigo de la provincia Santa Elena del seed.
    assertThatThrownBy(
            () ->
                service.crearProvincia(
                    new ProvinciaRequest("Duplicada " + UUID.randomUUID(), "24"),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("24");
  }

  @Test
  @DisplayName("no deja eliminar una provincia que todavia tiene cantones")
  void eliminarProvinciaConCantones() {
    Provincia provincia =
        service.crearProvincia(
            new ProvinciaRequest("Con hijos " + UUID.randomUUID(), codigoUnico()),
            masterId(),
            "127.0.0.1");
    service.crearCanton(
        new CantonRequest("Canton hijo " + UUID.randomUUID(), codigoUnico(), provincia.getId()),
        masterId(),
        "127.0.0.1");

    assertThatThrownBy(() -> service.eliminarProvincia(provincia.getId(), masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("cantones")
        .satisfies(
            ex ->
                assertThat(((GeografiaConflictException) ex).getCampos())
                    .containsEntry("childrenCount", "1"));
  }

  @Test
  @DisplayName("elimina una provincia sin cantones")
  void eliminarProvinciaSinCantones() {
    Provincia provincia =
        service.crearProvincia(
            new ProvinciaRequest("Sin hijos " + UUID.randomUUID(), codigoUnico()),
            masterId(),
            "127.0.0.1");

    service.eliminarProvincia(provincia.getId(), masterId(), "127.0.0.1");

    assertThat(em.find(Provincia.class, provincia.getId())).isNull();
  }

  @Test
  @DisplayName("edita el nombre y el codigo de la provincia")
  void actualizarProvincia() {
    Provincia provincia =
        service.crearProvincia(
            new ProvinciaRequest("Antes " + UUID.randomUUID(), codigoUnico()),
            masterId(),
            "127.0.0.1");
    String codigoNuevo = codigoUnico();

    Provincia editada =
        service.actualizarProvincia(
            provincia.getId(),
            new ProvinciaRequest("Despues " + UUID.randomUUID(), codigoNuevo),
            masterId(),
            "127.0.0.1");

    assertThat(editada.getNombre()).startsWith("Despues");
    assertThat(editada.getCodigo()).isEqualTo(codigoNuevo);
    assertThat(editada.getId()).isEqualTo(provincia.getId());
  }

  @Test
  @DisplayName("no deja editar una provincia que no existe")
  void actualizarProvinciaInexistente() {
    assertThatThrownBy(
            () ->
                service.actualizarProvincia(
                    999_999L,
                    new ProvinciaRequest("Fantasma", codigoUnico()),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }

  @Test
  @DisplayName("no deja tomar el codigo de otra provincia al editar")
  void actualizarProvinciaConCodigoAjeno() {
    Provincia provincia =
        service.crearProvincia(
            new ProvinciaRequest("Editable " + UUID.randomUUID(), codigoUnico()),
            masterId(),
            "127.0.0.1");

    assertThatThrownBy(
            () ->
                service.actualizarProvincia(
                    provincia.getId(),
                    new ProvinciaRequest("Editada", "24"),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class);
  }

  @Test
  @DisplayName("el canton cuelga de la provincia nueva")
  void cantonDebajoDeProvinciaNueva() {
    Provincia provincia =
        service.crearProvincia(
            new ProvinciaRequest("Con canton " + UUID.randomUUID(), codigoUnico()),
            masterId(),
            "127.0.0.1");

    Canton canton =
        service.crearCanton(
            new CantonRequest("Canton " + UUID.randomUUID(), codigoUnico(), provincia.getId()),
            masterId(),
            "127.0.0.1");

    assertThat(canton.getProvinciaId()).isEqualTo(provincia.getId());
  }
}
