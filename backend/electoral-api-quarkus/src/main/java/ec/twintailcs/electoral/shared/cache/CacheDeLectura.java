package ec.twintailcs.electoral.shared.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

/**
 * T-CACHE-STRAT-01 D.5: acceso a la cache de lectura, con el hit ratio medido.
 *
 * <p>Por que no {@code @CacheResult}: la anotacion de Quarkus no deja ver si el acierto vino de la
 * cache o de la base, asi que el hit ratio —la metrica que dice si la cache esta sirviendo o si
 * cada pedido esta yendo a la base— queda a ciegas. Aqui el acierto y el fallo se cuentan
 * explicitamente y se publican como {@code cache.hits} y {@code cache.misses}, con el nombre del
 * cache como etiqueta.
 *
 * <p>La API es de dos pasos a proposito: {@link #leer} y {@link #guardar}. La consulta a la base se
 * queda en el caso de uso, no dentro de este helper: una lambda que llama a un metodo estatico de
 * Panache se ejecuta en otra clase sintetica, y ahi Panache ya no tiene inyectada su implementacion
 * ("did you forget to annotate your entity with @Entity").
 *
 * <p>Si el cache no existe (porque no este configurada), se lee siempre de la base: perder la cache
 * es una degradacion, no un error.
 */
@ApplicationScoped
public class CacheDeLectura {

  @Inject CacheManager cacheManager;
  @Inject MeterRegistry meterRegistry;

  /**
   * Busca el valor en la cache.
   *
   * @param cacheName nombre del cache
   * @param clave clave dentro del cache
   * @param <T> tipo del valor cacheado
   * @return el valor cacheado, o vacio si no estaba
   */
  @SuppressWarnings("unchecked")
  public <T> Optional<T> leer(String cacheName, Object clave) {
    Optional<Cache> cache = cacheManager.getCache(cacheName);
    if (cache.isEmpty()) {
      return Optional.empty();
    }

    Optional<CaffeineCache> caffeine = comoCaffeine(cache.get());
    if (caffeine.isEmpty()) {
      // Cache que no es de caffeine (por ejemplo, Redis) o cache apagada: se
      // sirve siempre desde la base, sin contadores.
      return Optional.empty();
    }

    CompletableFuture<Object> guardado = caffeine.get().getIfPresent(clave);
    Object valor = guardado == null ? null : guardado.getNow(null);
    if (valor == null) {
      return Optional.empty();
    }

    meterRegistry.counter("cache.hits", "cache", cacheName).increment();
    return Optional.of((T) valor);
  }

  /**
   * Guarda el valor en la cache.
   *
   * <p>Guardar es, por definicion, el camino que no fue acierto: se cuenta como fallo para que el
   * hit ratio cuadre con lo que la base vio de verdad.
   *
   * @param cacheName nombre del cache
   * @param clave clave dentro del cache
   * @param valor valor a guardar
   */
  public void guardar(String cacheName, Object clave, Object valor) {
    Optional<Cache> cache = cacheManager.getCache(cacheName);
    if (cache.isEmpty()) {
      contarFallo(cacheName);
      return;
    }

    Optional<CaffeineCache> caffeine = comoCaffeine(cache.get());
    if (caffeine.isEmpty()) {
      contarFallo(cacheName);
      return;
    }
    caffeine.get().put(clave, CompletableFuture.completedFuture(valor));
    contarFallo(cacheName);
  }

  /**
   * Intenta ver el cache como {@link CaffeineCache}.
   *
   * <p><b>Por que este metodo existe y no un {@code catch (ClassCastException)}:</b> {@code
   * Cache.as(Class)} lanza {@link IllegalStateException} cuando el cache no es de ese tipo — no
   * {@code ClassCastException}. Con la cache apagada ({@code quarkus.cache.enabled=false}) Quarkus
   * entrega un {@code NoOpCache}, que tampoco sobrescribe {@code as()}: la excepcion escapaba del
   * helper y cada lectura de configuracion devolvia <b>500</b>. Perder la cache es una degradacion,
   * no un error.
   *
   * <p>El mismo camino cubre el caso de Redis (D2 pendiente): si un dia el backend de cache cambia,
   * este helper sigue sirviendo desde la base en vez de tirar la aplicacion.
   *
   * @param cache cache del que se quiere leer
   * @return el cache como Caffeine, o vacio si no lo es
   */
  private Optional<CaffeineCache> comoCaffeine(Cache cache) {
    try {
      return Optional.of(cache.as(CaffeineCache.class));
    } catch (IllegalStateException e) {
      return Optional.empty();
    }
  }

  private void contarFallo(String cacheName) {
    meterRegistry.counter("cache.misses", "cache", cacheName).increment();
  }
}
