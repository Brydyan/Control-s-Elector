package ec.twintailcs.electoral.shared.cache;

import com.fasterxml.jackson.core.type.TypeReference;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.cache.CaffeineCache;
import io.quarkus.cache.redis.runtime.RedisCache;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.util.TypeLiteral;
import jakarta.inject.Inject;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * T-CACHE-STRAT-01 D.5 / Phase 2 (ODD 2026-11-22): acceso a la cache de lectura, con el hit ratio
 * medido.
 *
 * <p>Por que no {@code @CacheResult}: la anotacion de Quarkus no deja ver si el acierto vino de la
 * cache o de la base, asi que el hit ratio —la metrica que dice si la cache esta sirviendo o si
 * cada pedido esta yendo a la base— queda a ciegas. Aqui el acierto y el fallo se cuentan
 * explicitamente y se publican como {@code cache.hits} y {@code cache.misses}, con el nombre del
 * cache como etiqueta.
 *
 * <p>Phase 2 cambia el backend de Caffeine (in-memory, por replica) a Valkey/Redis (compartido
 * entre replicas). La API publica ({@link #leer} / {@link #guardar}) se conserva para que los casos
 * de uso no cambien. Con backend Redis la deserializacion necesita el tipo concreto, por eso {@code
 * leer} recibe un {@link TypeReference}; el helper sigue soportando Caffeine y "cache apagada" como
 * degradacion.
 *
 * <p>La consulta a la base se queda en el caso de uso, no dentro de este helper: una lambda que
 * llama a un metodo estatico de Panache se ejecuta en otra clase sintetica, y ahi Panache ya no
 * tiene inyectada su implementacion ("did you forget to annotate your entity with @Entity").
 *
 * <p>Si el cache no existe o el backend no responde, se lee siempre de la base: perder la cache es
 * una degradacion, no un error.
 */
@ApplicationScoped
public class CacheDeLectura {

  private static final Logger LOG = Logger.getLogger(CacheDeLectura.class);

  @Inject CacheManager cacheManager;
  @Inject MeterRegistry meterRegistry;

  /**
   * Identificador de la replica que sirve la peticion.
   *
   * <p>Railway reparte el trafico al azar entre las replicas y <b>agrega</b> las metricas de todas
   * ellas en un solo tablero. Sin esta etiqueta, un tablero dice "el hit ratio es 90 %" sin poder
   * decir si las dos replicas coinciden o si una quedo con la cache desfasada respecto de la otra.
   * Con la etiqueta, cada replica tiene su propia linea y la divergencia se ve.
   *
   * <p>En Railway la variable {@code RAILWAY_REPLICA_ID} la inyecta la plataforma, una por replica.
   */
  @ConfigProperty(name = "replica.id", defaultValue = "local")
  String replicaId;

  /**
   * Busca el valor en la cache.
   *
   * @param cacheName nombre del cache
   * @param clave clave dentro del cache
   * @param tipo tipo concreto para deserializar valores del backend Redis/Valkey
   * @param <T> tipo del valor cacheado
   * @return el valor cacheado, o vacio si no estaba o el backend no esta disponible
   */
  public <T> Optional<T> leer(String cacheName, Object clave, TypeReference<T> tipo) {
    Optional<Cache> cache = cacheManager.getCache(cacheName);
    if (cache.isEmpty()) {
      return Optional.empty();
    }

    Cache actual = cache.get();
    Optional<RedisCache> redis = comoRedis(actual);
    if (redis.isPresent()) {
      return leerDeRedis(cacheName, clave, tipo, redis.get());
    }

    Optional<CaffeineCache> caffeine = comoCaffeine(actual);
    if (caffeine.isPresent()) {
      return leerDeCaffeine(cacheName, clave, caffeine.get());
    }

    // Cache apagada (NoOpCache) u otro backend no soportado: degradar a base.
    return Optional.empty();
  }

  /**
   * Compatibilidad con Phase 1: lectura sin tipo explicito.
   *
   * <p>Funciona con Caffeine (objeto en memoria) y con cache apagada. Con backend Redis/Valkey no
   * hay tipo para deserializar, asi que degrada a miss y se sirve desde la base; los call sites
   * deben preferir {@link #leer(String, Object, TypeReference)}.
   */
  public <T> Optional<T> leer(String cacheName, Object clave) {
    Optional<Cache> cache = cacheManager.getCache(cacheName);
    if (cache.isEmpty()) {
      return Optional.empty();
    }
    Optional<CaffeineCache> caffeine = comoCaffeine(cache.get());
    if (caffeine.isPresent()) {
      return leerDeCaffeine(cacheName, clave, caffeine.get());
    }
    return Optional.empty();
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

    Cache actual = cache.get();
    Optional<RedisCache> redis = comoRedis(actual);
    if (redis.isPresent()) {
      guardarEnRedis(cacheName, clave, valor, redis.get());
      return;
    }

    Optional<CaffeineCache> caffeine = comoCaffeine(actual);
    if (caffeine.isPresent()) {
      caffeine.get().put(clave, CompletableFuture.completedFuture(valor));
      contarFallo(cacheName);
      return;
    }

    contarFallo(cacheName);
  }

  private <T> Optional<T> leerDeRedis(
      String cacheName, Object clave, TypeReference<T> tipo, RedisCache redis) {
    try {
      @SuppressWarnings("unchecked")
      T valor = (T) redis.getOrNull(clave, typeLiteral(tipo)).await().indefinitely();
      if (valor == null) {
        return Optional.empty();
      }
      contador("cache.hits", cacheName).increment();
      return Optional.of(valor);
    } catch (Exception e) {
      LOG.debugf(
          e,
          "Cache '%s' no disponible para lectura (Redis/Valkey); sirviendo desde la base",
          cacheName);
      return Optional.empty();
    }
  }

  @SuppressWarnings("unchecked")
  private <T> Optional<T> leerDeCaffeine(String cacheName, Object clave, CaffeineCache caffeine) {
    CompletableFuture<Object> guardado = caffeine.getIfPresent(clave);
    Object valor = guardado == null ? null : guardado.getNow(null);
    if (valor == null) {
      return Optional.empty();
    }
    contador("cache.hits", cacheName).increment();
    return Optional.of((T) valor);
  }

  private void guardarEnRedis(String cacheName, Object clave, Object valor, RedisCache redis) {
    try {
      redis.put(clave, valor).await().indefinitely();
    } catch (Exception e) {
      LOG.debugf(
          e,
          "Cache '%s' no disponible para escritura (Redis/Valkey); se omite la carga",
          cacheName);
    } finally {
      // Contar fallo aunque la escritura falle: el dato se sirvio desde la base.
      contarFallo(cacheName);
    }
  }

  private Optional<RedisCache> comoRedis(Cache cache) {
    try {
      return Optional.of(cache.as(RedisCache.class));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  /**
   * Intenta ver el cache como {@link CaffeineCache}.
   *
   * <p>{@code Cache.as(Class)} lanza {@link IllegalStateException} (no {@link ClassCastException})
   * cuando el cache no es de ese tipo. Con {@code quarkus.cache.enabled=false} Quarkus entrega un
   * {@code NoOpCache}: tratarlo como degradacion evita un 500 en cada lectura de configuracion.
   */
  private Optional<CaffeineCache> comoCaffeine(Cache cache) {
    try {
      return Optional.of(cache.as(CaffeineCache.class));
    } catch (RuntimeException e) {
      return Optional.empty();
    }
  }

  private void contarFallo(String cacheName) {
    contador("cache.misses", cacheName).increment();
  }

  /**
   * Contador de aciertos o fallos de una cache, etiquetado con su nombre y con la replica.
   *
   * <p>La etiqueta {@code replica} es lo que permite distinguir "el hit ratio bajo" de "una de las
   * replicas quedo desfasada": Railway agrega las metricas de todas sus replicas, asi que sin ella
   * el tablero no distingue los dos casos.
   */
  private Counter contador(String nombre, String cacheName) {
    return meterRegistry.counter(nombre, "cache", cacheName, "replica", replicaId);
  }

  /**
   * Adaptador para pasar un {@link com.fasterxml.jackson.core.type.TypeReference} a la API de
   * Quarkus Redis Cache.
   *
   * <p>Quarkus Redis Cache resuelve el tipo por reflexion sobre la superclase generica de {@link
   * TypeLiteral}. Para tipos no-Class (por ejemplo {@code List<Cargo>}) construimos una subclase
   * anonima con ese tipo como parametro real usando {@code TypeLiteral.getType()} del literal
   * anonimo creado en el caller no sirve; en su lugar se usa el TypeReference del caller a traves
   * de una clase auxiliar que fija el tipo en el campo {@code actualType} via reflexion.
   */
  @SuppressWarnings("unchecked")
  private static <T> TypeLiteral<T> typeLiteral(
      com.fasterxml.jackson.core.type.TypeReference<T> reference) {
    java.lang.reflect.Type type = reference.getType();
    try {
      TypeLiteral<T> literal = new TypeLiteral<T>() {};
      java.lang.reflect.Field field = TypeLiteral.class.getDeclaredField("actualType");
      field.setAccessible(true);
      field.set(literal, type);
      return literal;
    } catch (ReflectiveOperationException e) {
      throw new IllegalStateException("No se pudo construir TypeLiteral para la cache", e);
    }
  }
}
