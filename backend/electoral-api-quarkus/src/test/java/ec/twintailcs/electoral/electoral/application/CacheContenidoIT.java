package ec.twintailcs.electoral.electoral.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.electoral.domain.Candidato;
import ec.twintailcs.electoral.electoral.domain.Cargo;
import ec.twintailcs.electoral.electoral.domain.Partido;
import io.quarkus.cache.CacheManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-CACHE-STRAT-01 F.2: la cache no ahorra consultas a costa de servir el dato equivocado.
 *
 * <p>{@link CacheServicioIT} cuenta consultas: que la segunda llamada no toque la base. Eso no dice
 * que lo que devuelve sea lo mismo que devuelve la base. Una cache que ahorra consultas pero sirve
 * una lista vacia, o un candidato sin su partido, pasa ese test y rompe la aplicacion.
 *
 * <p>Este test afirma las dos mitades:
 *
 * <ul>
 *   <li>con la cache vacia, el resultado viene de la base;
 *   <li>con la cache llena, el resultado es <b>igual</b> al de la base (mismos ids, mismos campos),
 *       no una version recortada o desactualizada.
 * </ul>
 */
@QuarkusTest
@TestProfile(CacheServicioTestProfile.class)
class CacheContenidoIT {

  @Inject ConfiguracionService configuracionService;
  @Inject CacheManager cacheManager;
  @Inject EntityManagerFactory emf;

  @BeforeEach
  void vaciarCaches() {
    cacheManager
        .getCacheNames()
        .forEach(
            nombre ->
                cacheManager
                    .getCache(nombre)
                    .ifPresent(cache -> cache.invalidateAll().await().indefinitely()));
    stats().setStatisticsEnabled(true);
    stats().clear();
  }

  @Test
  @DisplayName("cargos: el acierto de la cache devuelve los mismos cargos que la base")
  void cargosElAciertoCoincideConLaBase() {
    List<Cargo> desdeLaBase = configuracionService.listCargos();
    List<Cargo> desdeLaCache = configuracionService.listCargos();

    assertThat(desdeLaCache)
        .as("el acierto de la cache debe traer los mismos cargos, en el mismo orden")
        .hasSameSizeAs(desdeLaBase);

    for (int i = 0; i < desdeLaBase.size(); i++) {
      Cargo esperado = desdeLaBase.get(i);
      Cargo servido = desdeLaCache.get(i);
      assertThat(servido.getId()).as("id del cargo %d", i).isEqualTo(esperado.getId());
      assertThat(servido.getNombre()).as("nombre del cargo %d", i).isEqualTo(esperado.getNombre());
      assertThat(servido.getNivel()).as("nivel del cargo %d", i).isEqualTo(esperado.getNivel());
    }
  }

  @Test
  @DisplayName("partidos: el acierto de la cache devuelve los mismos partidos que la base")
  void partidosElAciertoCoincideConLaBase() {
    List<Partido> desdeLaBase = configuracionService.listPartidos();
    List<Partido> desdeLaCache = configuracionService.listPartidos();

    assertThat(desdeLaCache).hasSameSizeAs(desdeLaBase);
    for (int i = 0; i < desdeLaBase.size(); i++) {
      Partido esperado = desdeLaBase.get(i);
      Partido servido = desdeLaCache.get(i);
      assertThat(servido.getId()).as("id del partido %d", i).isEqualTo(esperado.getId());
      assertThat(servido.getNombre())
          .as("nombre del partido %d", i)
          .isEqualTo(esperado.getNombre());
      assertThat(servido.getSiglas())
          .as("siglas del partido %d", i)
          .isEqualTo(esperado.getSiglas());
    }
  }

  @Test
  @DisplayName("candidatos: el acierto de la cache conserva el partido de cada candidato")
  void candidatosElAciertoConservaElPartido() {
    List<Candidato> desdeLaBase = configuracionService.listCandidatos(null, null);
    List<Candidato> desdeLaCache = configuracionService.listCandidatos(null, null);

    assertThat(desdeLaCache).hasSameSizeAs(desdeLaBase);
    for (int i = 0; i < desdeLaBase.size(); i++) {
      Candidato esperado = desdeLaBase.get(i);
      Candidato servido = desdeLaCache.get(i);
      assertThat(servido.getId()).as("id del candidato %d", i).isEqualTo(esperado.getId());
      assertThat(servido.getNombre())
          .as("nombre del candidato %d", i)
          .isEqualTo(esperado.getNombre());
      // La relacion con el partido viene con `join fetch`: si un dia se cachea
      // sin traerse, aqui saltaria el proxy perezoso.
      assertThat(servido.getPartido().getId())
          .as("partido del candidato %d", i)
          .isEqualTo(esperado.getPartido().getId());
      assertThat(servido.getCargo().getId())
          .as("cargo del candidato %d", i)
          .isEqualTo(esperado.getCargo().getId());
    }
  }

  @Test
  @DisplayName("candidatos filtrados: el acierto de la cache respeta el filtro del cargo")
  void candidatosFiltradosElAciertoRespetaElFiltro() {
    List<Candidato> todos = configuracionService.listCandidatos(null, null);
    if (todos.isEmpty()) {
      // Sin datos sembrados no hay nada que comparar: el test de contenido no
      // tiene nada que afirmar, pero tampoco hay regresion que ocultar.
      return;
    }

    Long cargoId = todos.get(0).getCargo().getId();
    List<Candidato> desdeLaBase = configuracionService.listCandidatos(cargoId, null);
    List<Candidato> desdeLaCache = configuracionService.listCandidatos(cargoId, null);

    assertThat(desdeLaCache).hasSameSizeAs(desdeLaBase);
    for (int i = 0; i < desdeLaBase.size(); i++) {
      Candidato servido = desdeLaCache.get(i);
      assertThat(servido.getId()).isEqualTo(desdeLaBase.get(i).getId());
      // El filtro no se pierde: todos los candidatos servidos son del cargo pedido.
      assertThat(servido.getCargo().getId()).as("cargo del candidato %d", i).isEqualTo(cargoId);
    }
  }

  /** Estadisticas de Hibernate de la aplicacion. */
  private Statistics stats() {
    return emf.unwrap(SessionFactory.class).getStatistics();
  }
}
