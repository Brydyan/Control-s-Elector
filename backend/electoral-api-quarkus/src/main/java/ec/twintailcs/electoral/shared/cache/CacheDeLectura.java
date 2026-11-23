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
import java.time.Duration;
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
   * Techo de una operacion contra el backend de cache (Valkey/Redis).
   *
   * <p>Sin este techo, una llamada a Valkey que no responde —el contenedor del CI que todavia no
   * esta listo, un backend saturado, una red lenta— se queda esperando para siempre. Y como la
   * cache se consulta <b>dentro</b> de la transaccion de los casos de uso, esa espera indefinida
   * agota el tiempo de la transaccion: Narayana la cancela a los 60 s y el commit falla con {@code
   * jakarta.transaction.RollbackException} (checked), que ARC envuelve en {@code
   * ArcUndeclaredThrowableException}. El resultado visible era un 500 en listados que solo deberian
   * degradarse a "se sirve desde la base".
   *
   * <p>Con el techo, un backend que no responde es una degradacion, que es lo que el diseno de la
   * cache exige: perder la cache no es un error.
   *
   * <p>El valor tiene que quedar por debajo del timeout de la transaccion (60 s por defecto en
   * Narayana) con margen: si no, la cache seguiria siendo capaz de tumbar la transaccion.
   */
  @Inject
  @ConfigProperty(name = "app.cache.timeout", defaultValue = "10s")
  Duration timeoutCache;

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
      LOG.warnf("DIAGNOSTIC: Cache '%s' not found. Available: %s", cacheName, cacheManager.getCacheNames());
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
      T valor = (T) redis.getOrNull(clave, typeLiteral(tipo)).await().atMost(timeoutCache);
      if (valor == null) {
        return Optional.empty();
      }
      contador("cache.hits", cacheName).increment();
      return Optional.of(valor);
    } catch (Exception e) {
      // Excepciones esperadas: (1) TimeoutException si atMost(timeoutCache) expira,
      // (2) SerializationException si el valor en Redis no se puede deserializar,
      // (3) IllegalStateException si TypeLiteral.actualType no es accesible (Java 17+ restrictivo).
      // El fallo de la cache es una degradacion, no un error: se sigue con la base. Pero queda
      // registrado (log y contador) porque una cache que falla siempre es un incidente, y con el
      // log en DEBUG ese incidente era invisible en el CI.
      //
      // Con un broad catch(Exception) se engloban todos sin distinguir: los esperados se log en
      // WARN (degradacion), y los inesperados tambien (si uno nuevo emerge, el logging lo hace
      // visible). El costo es que si el backend remoto cambia el tipo de excepcion en una
      // version futura de Quarkus, aqui se maneja sin sorpresas.
      LOG.debugf(
          e,
          "Cache '%s' no disponible para lectura (Redis/Valkey); sirviendo desde la base. Exception type: %s",
          cacheName,
          e.getClass().getSimpleName());
      LOG.warnf(
          "Cache '%s' no disponible para lectura (Redis/Valkey); sirviendo desde la base",
          cacheName);
      contarError(cacheName);
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
      redis.put(clave, valor).await().atMost(timeoutCache);
    } catch (Exception e) {
      // Ver comentario en leerDeRedis() sobre el broad catch(Exception). La escritura en Redis
      // no bloquea la respuesta: una falla aqui deja la cache vacia para ese caller, pero el
      // siguiente caller lo vuelve a intentar (ver contarFallo en finally).
      LOG.debugf(
          e,
          "Cache '%s' no disponible para escritura (Redis/Valkey); se omite la carga. Exception type: %s",
          cacheName,
          e.getClass().getSimpleName());
      LOG.warnf(
          "Cache '%s' no disponible para escritura (Redis/Valkey); se omite la carga", cacheName);
      contarError(cacheName);
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
   * Contador de errores de la cache (el backend no respondio, el valor no se pudo deserializar).
   *
   * <p>Va aparte de {@code cache.misses}: un fallo es una linea de log y un contador de error, no
   * un simple acierto perdido. Sin el, "el hit ratio bajo" y "el backend esta caido" se ven igual.
   */
  private void contarError(String cacheName) {
    contador("cache.errors", cacheName).increment();
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
   * <p><b>Resolucion de tipos genéricos.</b> Quarkus Redis Cache resuelve el tipo por reflexion
   * sobre la superclase generica de {@link TypeLiteral}. Para tipos no-Class (por ejemplo {@code
   * List<Cargo>}) construimos una subclase anonima con ese tipo como parametro real. El {@code
   * TypeLiteral.getType()} del literal anonimo creado inline no funciona (el tipo se pierde en la
   * compilacion); en su lugar se usa el {@link TypeReference} del caller para fijar el tipo en el
   * campo privado {@code actualType} via reflexion.
   *
   * <p><b>Riesgo: Java 17+ con módulos restrictivos.</b> El acceso a campos privados mediante
   * reflexion puede fallar con {@code InaccessibleObjectException} en Java 17+ si Quarkus o su
   * dependencia (Jakarta EE) restringe el acceso a {@code java.lang.reflect.Field}. Quarkus 3.40.1
   * registra {@code TypeLiteral.actualType} para reflexion en {@code native-image.properties}, asi
   * que esta linea sigue siendo segura en la imagen nativa. En cambio, una futura version de
   * Quarkus que quite ese registro, o un entorno donde {@code --illegal-access=deny} este fijo,
   * haria fallar este código con {@code IllegalStateException}.
   *
   * <p><b>Mitigacion.</b> Ver {@link
   * ec.twintailcs.electoral.shared.cache.CacheDeLecturaValkeyIT#testReadWithGenericType()} y {@code
   * ci-native.yml}: el test IT verifica que la reflexion funciona, y la asercion del CI verifica
   * que el cache tiene aciertos en la imagen nativa (si la reflexion falla, el hit ratio es 0).
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
