package ec.twintailcs.electoral.geografia.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.geografia.domain.Canton;
import ec.twintailcs.electoral.geografia.domain.GeografiaConflictException;
import ec.twintailcs.electoral.geografia.domain.GeografiaNotFoundException;
import ec.twintailcs.electoral.geografia.domain.Jrv;
import ec.twintailcs.electoral.geografia.domain.Parroquia;
import ec.twintailcs.electoral.geografia.domain.Provincia;
import ec.twintailcs.electoral.geografia.web.GeografiaTestProfile;
import ec.twintailcs.electoral.geografia.web.dto.CantonRequest;
import ec.twintailcs.electoral.geografia.web.dto.JrvRequest;
import ec.twintailcs.electoral.geografia.web.dto.ParroquiaRequest;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import java.util.UUID;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * CRUD de parroquias y de JRVs contra una base real.
 *
 * <p>Las reglas que se comprueban aqui dependen de la base: el nombre unico de la parroquia dentro
 * del canton, el numero de JRV unico en el sistema, y el conteo de JRVs y de actas que bloquea el
 * borrado en cascada. Un test sin base real no probaria nada de eso.
 *
 * <p>Los tests de este modulo comparten el contenedor de Postgres, asi que cada uno crea su propia
 * cadena provincia → canton → parroquia y trabaja sobre ella.
 */
@QuarkusTest
@TestProfile(GeografiaTestProfile.class)
class GeografiaParroquiaJrvIT {

  @Inject GeografiaService service;
  @Inject EntityManager em;

  /** Provincia propia de cada test. */
  private Long provinciaId() {
    return QuarkusTransaction.requiringNew()
        .call(
            () -> {
              Provincia provincia = new Provincia("Provincia de prueba " + UUID.randomUUID(), null);
              provincia.persist();
              return provincia.getId();
            });
  }

  /** Canton propio de cada test, colgado de una provincia propia. */
  private Long cantonId() {
    Long provincia = provinciaId();
    Canton canton =
        service.crearCanton(
            new CantonRequest(
                "Canton de prueba " + UUID.randomUUID(), "C" + numeroUnico(), provincia),
            masterId(),
            "127.0.0.1");
    return canton.getId();
  }

  /** Parroquia propia de cada test, colgada de un canton propio. */
  private Long parroquiaId() {
    Long canton = cantonId();
    Parroquia parroquia =
        service.crearParroquia(
            new ParroquiaRequest("Parroquia de prueba " + UUID.randomUUID(), true, canton),
            masterId(),
            "127.0.0.1");
    return parroquia.getId();
  }

  /** Id del MASTER seedeado: es quien puede mutar la geografia. */
  private Long masterId() {
    return em.createQuery("select u.id from Usuario u where u.email = :email", Long.class)
        .setParameter("email", "mastersolon@example.local")
        .getSingleResult();
  }

  /** Numero de JRV unico por test. */
  private String numeroJrvUnico() {
    return "JRV-IT-" + numeroUnico();
  }

  /** Sufijo numerico unico, para no chocar con el indice global de codigo de canton. */
  private String numeroUnico() {
    return String.valueOf(Math.abs(UUID.randomUUID().getLeastSignificantBits() % 100000));
  }

  // ---------------------------------------------------------------------------------------------
  // Parroquias
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("crea una parroquia valida bajo un canton")
  void crearParroquiaValida() {
    Long canton = cantonId();
    String nombre = "Parroquia de prueba " + UUID.randomUUID();

    Parroquia parroquia =
        service.crearParroquia(new ParroquiaRequest(nombre, true, canton), masterId(), "127.0.0.1");

    assertThat(parroquia.getId()).isNotNull();
    assertThat(parroquia.getNombre()).isEqualTo(nombre);
    assertThat(parroquia.isEsUrbana()).isTrue();
    assertThat(parroquia.getCantonId()).isEqualTo(canton);
  }

  @Test
  @DisplayName("rechaza dos parroquias con el mismo nombre en el mismo canton")
  void crearParroquiaConNombreRepetido() {
    Long canton = cantonId();
    String nombre = "Parroquia repetida " + UUID.randomUUID();

    service.crearParroquia(new ParroquiaRequest(nombre, true, canton), masterId(), "127.0.0.1");

    assertThatThrownBy(
            () ->
                service.crearParroquia(
                    new ParroquiaRequest(nombre, false, canton), masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining(nombre);
  }

  @Test
  @DisplayName("rechaza una parroquia con un canton que no existe")
  void crearParroquiaConCantonInexistente() {
    assertThatThrownBy(
            () ->
                service.crearParroquia(
                    new ParroquiaRequest("Huerfana", true, 999_999L), masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }

  @Test
  @DisplayName("no deja eliminar una parroquia que todavia tiene JRVs")
  void eliminarParroquiaConJrvs() {
    Long parroquia = parroquiaId();
    service.crearJrv(
        new JrvRequest(numeroJrvUnico(), "Recinto de prueba", 100, parroquia),
        masterId(),
        "127.0.0.1");

    assertThatThrownBy(() -> service.eliminarParroquia(parroquia, masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("JRVs")
        .satisfies(
            ex ->
                assertThat(((GeografiaConflictException) ex).getCampos())
                    .containsEntry("childrenCount", "1"));
  }

  @Test
  @DisplayName("elimina una parroquia sin JRVs")
  void eliminarParroquiaSinJrvs() {
    Long parroquia = parroquiaId();

    service.eliminarParroquia(parroquia, masterId(), "127.0.0.1");

    assertThat(em.find(Parroquia.class, parroquia)).isNull();
  }

  @Test
  @DisplayName("edita el nombre y el indicador urbano de la parroquia")
  void actualizarParroquia() {
    Long parroquia = parroquiaId();
    Long canton = em.find(Parroquia.class, parroquia).getCantonId();
    String nombre = "Parroquia editada " + UUID.randomUUID();

    Parroquia editada =
        service.actualizarParroquia(
            parroquia, new ParroquiaRequest(nombre, false, canton), masterId(), "127.0.0.1");

    assertThat(editada.getNombre()).isEqualTo(nombre);
    assertThat(editada.isEsUrbana()).isFalse();
    assertThat(editada.getId()).isEqualTo(parroquia);
  }

  @Test
  @DisplayName("no deja editar una parroquia que no existe")
  void actualizarParroquiaInexistente() {
    assertThatThrownBy(
            () ->
                service.actualizarParroquia(
                    999_999L,
                    new ParroquiaRequest("Fantasma", true, cantonId()),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }

  // ---------------------------------------------------------------------------------------------
  // JRVs
  // ---------------------------------------------------------------------------------------------

  @Test
  @DisplayName("crea una JRV valida bajo una parroquia")
  void crearJrvValida() {
    Long parroquia = parroquiaId();
    String numero = numeroJrvUnico();

    Jrv jrv =
        service.crearJrv(
            new JrvRequest(numero, "Escuela de prueba", 250, parroquia), masterId(), "127.0.0.1");

    assertThat(jrv.getId()).isNotNull();
    assertThat(jrv.getNumero()).isEqualTo(numero);
    assertThat(jrv.getRecinto()).isEqualTo("Escuela de prueba");
    assertThat(jrv.getTotalElectores()).isEqualTo(250);
    assertThat(jrv.getParroquiaId()).isEqualTo(parroquia);
  }

  @Test
  @DisplayName("rechaza una JRV con un numero que ya existe en el sistema")
  void crearJrvConNumeroRepetido() {
    Long parroquia = parroquiaId();

    // "JRV-SE-001" es el numero de la JRV del seed.
    assertThatThrownBy(
            () ->
                service.crearJrv(
                    new JrvRequest("JRV-SE-001", "Duplicada", 100, parroquia),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("JRV-SE-001");
  }

  @Test
  @DisplayName("rechaza una JRV con una parroquia que no existe")
  void crearJrvConParroquiaInexistente() {
    assertThatThrownBy(
            () ->
                service.crearJrv(
                    new JrvRequest(numeroJrvUnico(), "Huerfana", 100, 999_999L),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }

  @Test
  @DisplayName("no deja eliminar una JRV que ya tiene actas registradas")
  void eliminarJrvConActas() {
    // La JRV del seed "JRV-SE-001" tiene un acta ENVIADA: es el caso que el backend tiene que
    // rechazar sin borrar nada.
    Long jrvDelSeed =
        em.createQuery("select j.id from Jrv j where j.numero = :numero", Long.class)
            .setParameter("numero", "JRV-SE-001")
            .getSingleResult();

    assertThatThrownBy(() -> service.eliminarJrv(jrvDelSeed, masterId(), "127.0.0.1"))
        .isInstanceOf(GeografiaConflictException.class)
        .hasMessageContaining("actas")
        .satisfies(
            ex ->
                assertThat(((GeografiaConflictException) ex).getCampos())
                    .containsEntry("actasCount", "1"));

    // La JRV sigue viva: el 409 tiene que venir antes del borrado, no despues.
    assertThat(em.find(Jrv.class, jrvDelSeed)).isNotNull();
  }

  @Test
  @DisplayName("elimina una JRV sin actas registradas")
  void eliminarJrvSinActas() {
    Long parroquia = parroquiaId();
    Jrv jrv =
        service.crearJrv(
            new JrvRequest(numeroJrvUnico(), "Recinto efimero", 50, parroquia),
            masterId(),
            "127.0.0.1");

    service.eliminarJrv(jrv.getId(), masterId(), "127.0.0.1");

    assertThat(em.find(Jrv.class, jrv.getId())).isNull();
  }

  @Test
  @DisplayName("edita el numero, el recinto y el total de electores de la JRV")
  void actualizarJrv() {
    Long parroquia = parroquiaId();
    Jrv jrv =
        service.crearJrv(
            new JrvRequest(numeroJrvUnico(), "Antes", 100, parroquia), masterId(), "127.0.0.1");
    String numeroNuevo = numeroJrvUnico();

    Jrv editada =
        service.actualizarJrv(
            jrv.getId(),
            new JrvRequest(numeroNuevo, "Despues", 175, parroquia),
            masterId(),
            "127.0.0.1");

    assertThat(editada.getNumero()).isEqualTo(numeroNuevo);
    assertThat(editada.getRecinto()).isEqualTo("Despues");
    assertThat(editada.getTotalElectores()).isEqualTo(175);
    assertThat(editada.getId()).isEqualTo(jrv.getId());
  }

  @Test
  @DisplayName("no deja editar una JRV que no existe")
  void actualizarJrvInexistente() {
    assertThatThrownBy(
            () ->
                service.actualizarJrv(
                    999_999L,
                    new JrvRequest(numeroJrvUnico(), "Fantasma", 10, parroquiaId()),
                    masterId(),
                    "127.0.0.1"))
        .isInstanceOf(GeografiaNotFoundException.class);
  }
}
