package ec.twintailcs.electoral.electoral.application;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.geografia.application.GeografiaService;
import ec.twintailcs.electoral.shared.cache.InvalidadorDeCache;
import io.quarkus.cache.CacheManager;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManagerFactory;
import java.util.function.Supplier;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * T-CACHE-STRAT-01 F.2: la cache de verdad evita la consulta.
 *
 * <p>Un cache que no evita nada no es un cache: es un almacen de respuestas con pasos extra. Este
 * test cuenta las consultas a la base con el contador de Hibernate y afirma dos cosas:
 *
 * <ul>
 *   <li>sin cache, la llamada consulta;
 *   <li>con la cache llena, la segunda llamada no consulta.
 * </ul>
 *
 * <p>Las dos mitades son necesarias: con la primera sola, un contador roto haria pasar el test; con
 * la segunda sola, un cache que nunca se llena tambien pasaria.
 */
@QuarkusTest
@TestProfile(CacheServicioTestProfile.class)
class CacheServicioIT {

  @Inject ConfiguracionService configuracionService;
  @Inject GeografiaService geografiaService;
  @Inject CacheManager cacheManager;
  @Inject EntityManagerFactory emf;
  @Inject InvalidadorDeCache invalidador;

  @BeforeEach
  void vaciarCaches() {
    cacheManager
        .getCacheNames()
        .forEach(
            nombre ->
                cacheManager
                    .getCache(nombre)
                    .ifPresent(cache -> cache.invalidateAll().await().indefinitely()));
    // En Quarkus 3.40.1, caches no registradas en CacheManager aun pueden tener datos en Redis.
    // Invalidar directamente para limpiar completamente.
    for (String cacheName : new String[] {"cargos", "partidos", "candidatos", "geografia"}) {
      invalidador.invalidarAhora(cacheName);
    }
    // El contador se enciende y se limpia en cada test: asi los deltas son del test, no de la
    // sesion. No hace falta tocar la configuracion de la aplicacion para esto.
    stats().setStatisticsEnabled(true);
    stats().clear();
  }

  @Test
  @DisplayName("cargos: la segunda llamada no vuelve a la base")
  void cargosSeSirvenDeLaCache() {
    afirmarQueLaCacheAhorraConsulta(
        "cargos", () -> configuracionService.listCargos(), () -> configuracionService.listCargos());
  }

  @Test
  @DisplayName("partidos: la segunda llamada no vuelve a la base")
  void partidosSeSirvenDeLaCache() {
    afirmarQueLaCacheAhorraConsulta(
        "partidos",
        () -> configuracionService.listPartidos(),
        () -> configuracionService.listPartidos());
  }

  @Test
  @DisplayName("candidatos: la segunda llamada no vuelve a la base")
  void candidatosSeSirvenDeLaCache() {
    afirmarQueLaCacheAhorraConsulta(
        "candidatos",
        () -> configuracionService.listCandidatos(null, null),
        () -> configuracionService.listCandidatos(null, null));
  }

  @Test
  @DisplayName("geografia: la segunda llamada no vuelve a la base")
  void geografiaSeSirveDeLaCache() {
    afirmarQueLaCacheAhorraConsulta(
        "geografia", () -> geografiaService.listarArbol(), () -> geografiaService.listarArbol());
  }

  /**
   * Comprueba, para un cache dado, que la primera llamada consulta y la segunda no.
   *
   * @param nombreCache cache que se supone que evita la consulta
   * @param primera llamada que llena la cache
   * @param segunda llamada que deberia servirse de la cache
   */
  private void afirmarQueLaCacheAhorraConsulta(
      String nombreCache, Supplier<Object> primera, Supplier<Object> segunda) {
    // Sin cache: tiene que consultar.
    long antesDeLaPrimera = consultas();
    primera.get();
    long despuesDeLaPrimera = consultas();
    assertThat(despuesDeLaPrimera - antesDeLaPrimera)
        .as("la primera llamada, con la cache '%s' vacia, consulta la base", nombreCache)
        .isGreaterThan(0);

    // Con la cache llena: no debe consultar.
    long antesDeLaSegunda = consultas();
    segunda.get();
    long despuesDeLaSegunda = consultas();
    assertThat(despuesDeLaSegunda - antesDeLaSegunda)
        .as("la segunda llamada se sirve de la cache '%s'", nombreCache)
        .isZero();
  }

  /** Estadisticas de Hibernate de la aplicacion. */
  private Statistics stats() {
    return emf.unwrap(SessionFactory.class).getStatistics();
  }

  /** Consultas ejecutadas por Hibernate desde la ultima limpieza. */
  private long consultas() {
    return stats().getQueryExecutionCount();
  }
}
