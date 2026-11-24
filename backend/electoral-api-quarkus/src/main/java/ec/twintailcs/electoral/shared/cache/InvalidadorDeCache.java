package ec.twintailcs.electoral.shared.cache;

import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.cache.Cache;
import io.quarkus.cache.CacheManager;
import io.quarkus.redis.datasource.ReactiveRedisDataSource;
import io.quarkus.redis.datasource.keys.ReactiveKeyCommands;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Event;
import jakarta.enterprise.event.Observes;
import jakarta.enterprise.event.TransactionPhase;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import jakarta.transaction.Status;
import jakarta.transaction.TransactionSynchronizationRegistry;
import java.time.Duration;
import java.util.Optional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Invalidacion de la cache que tolera que el backend no este.
 *
 * <p><b>Por que no {@code @CacheInvalidateAll}.</b> El interceptor de Quarkus ({@code
 * io.quarkus.cache.runtime.CacheInvalidateAllInterceptor}) resuelve la invalidacion con {@code
 * await().indefinitely()} y <b>sin capturar la excepcion</b>. Con Valkey inalcanzable, la excepcion
 * escapa del caso de uso y <b>toda escritura</b> —crear un cargo, un partido, una provincia—
 * responde 500, aunque la escritura ya se haya confirmado en la base. Es el camino inverso al de la
 * lectura, que si degrada a "se sirve desde la base".
 *
 * <p>Ademas el interceptor espera sin techo: un backend que acepta la conexion pero no contesta
 * deja la peticion colgada indefinidamente.
 *
 * <p>Este helper hace las dos cosas que el diseno de la cache exige: la invalidacion tiene un
 * <b>techo</b> (el mismo {@code app.cache.timeout} que usa la lectura) y un <b>fallo del backend es
 * una degradacion, no un error</b>. El error queda registrado en el log y en el contador {@code
 * cache.errors}: una cache que nunca invalida es un incidente, no un detalle.
 *
 * <p><b>Despues del commit, no antes.</b> El interceptor de Quarkus tiene prioridad 0 y el de la
 * transaccion 200: el de la cache envuelve al de la transaccion, asi que la invalidacion ocurre
 * <b>despues</b> del commit. Invalidar antes dejaria una ventana en la que un lector concurrente
 * puede volver a cachear el dato viejo (el commit todavia no ocurrio) y servirlo durante el TTL
 * completo. Para no perder esa garantia, la invalidacion se dispara con un evento transaccional
 * observado en {@code AFTER_SUCCESS}: si la transaccion se revierte, no se invalida nada, que es lo
 * correcto.
 */
@ApplicationScoped
public class InvalidadorDeCache {

  private static final Logger LOG = Logger.getLogger(InvalidadorDeCache.class);

  @Inject CacheManager cacheManager;
  @Inject MeterRegistry meterRegistry;
  @Inject TransactionSynchronizationRegistry registry;
  @Inject Event<Invalidacion> evento;
  @Inject Instance<ReactiveRedisDataSource> redisClientInstance;

  /**
   * Identificador de la replica que sirve la peticion: la misma etiqueta que usa la lectura, para
   * que el tablero de cache no tenga dos series con el mismo nombre y etiquetas distintas.
   */
  @ConfigProperty(name = "replica.id", defaultValue = "local")
  String replicaId;

  /**
   * Techo de una operacion contra el backend de cache.
   *
   * <p>El mismo que usa {@link CacheDeLectura}: la cache nunca puede tumbar una escritura.
   */
  @ConfigProperty(name = "app.cache.timeout", defaultValue = "5s")
  Duration timeoutCache;

  /**
   * Invalida una cache completa.
   *
   * <p>Si hay una transaccion activa, la invalidacion se aplaza al commit; si no, ocurre de
   * inmediato. En los dos casos un fallo del backend se degrada a un aviso, nunca a un error.
   *
   * @param cacheName nombre del cache a invalidar
   */
  public void invalidar(String cacheName) {
    if (transaccionActiva()) {
      evento.fire(new Invalidacion(cacheName));
      return;
    }
    invalidarAhora(cacheName);
  }

  /**
   * Invalida una cache completa ahora mismo, sin esperar al commit.
   *
   * <p>Para el vaciado manual (endpoint de administracion): ahi el que pide el vaciado quiere el
   * efecto ya, no despues del commit de otra cosa.
   *
   * <p>En Quarkus 3.40.1 con Redis, los caches se crean on-demand: si un cache no ha sido accedido
   * via @CacheResult aun, CacheManager.getCache() devuelve empty y no hay nada que invalidar via
   * CacheManager. Este metodo implementa un fallback directo a Redis para limpiar las claves que
   * fueron escritas via guardarDirectoEnRedis().
   *
   * @param cacheName nombre del cache a invalidar
   * @return {@code true} si la invalidacion se completo, {@code false} si el backend no respondio
   */
  public boolean invalidarAhora(String cacheName) {
    Optional<Cache> cache = cacheManager.getCache(cacheName);
    if (cache.isPresent()) {
      try {
        cache.get().invalidateAll().await().atMost(timeoutCache);
        return true;
      } catch (Exception e) {
        // Excepciones esperadas: (1) TimeoutException si atMost(timeoutCache) expira,
        // (2) la excepcion del cliente Redis/Valkey si el backend no esta (UnknownHostException,
        // ConnectionException). El broad catch cubre los dos sin depender del tipo exacto que
        // devuelva la version de Quarkus.
        LOG.warnf(
            e,
            "Cache '%s' no disponible para invalidacion via CacheManager; intentando fallback directo",
            cacheName);
        // Fallthrough a fallback directo
      }
    }

    // Fallback: invalida directo en Redis si el cliente esta disponible.
    // Esto es necesario porque en Quarkus 3.40.1, caches no registrados en CacheManager aun
    // pueden tener claves en Redis si fueron escritas via guardarDirectoEnRedis().
    if (redisClientInstance.isResolvable()) {
      try {
        invalidarDirectoEnRedis(cacheName, redisClientInstance.get());
        return true;
      } catch (Exception e) {
        LOG.warnf(
            e,
            "Cache '%s' no disponible para invalidacion directa contra Redis/Valkey; se sigue con la escritura",
            cacheName);
        contador(cacheName).increment();
        return false;
      }
    }

    // El cache no existe y no tenemos cliente Redis: considerar exitosa la invalidacion
    // (no hay nada que limpiar).
    return true;
  }

  /**
   * Invalida la cache cuando la transaccion se confirma.
   *
   * <p>Con {@code AFTER_SUCCESS} el evento solo se entrega si la transaccion se confirmo: una
   * escritura revertida no toca la cache, que es lo que se espera.
   */
  void invalidarAlCommit(
      @Observes(during = TransactionPhase.AFTER_SUCCESS) Invalidacion invalidacion) {
    invalidarAhora(invalidacion.cacheName());
  }

  /**
   * Invalida una cache completa directamente contra Redis, sin pasar por CacheManager.
   *
   * <p>Las claves del cache se almacenan con formato cacheName:clave. Para invalidar todas las
   * claves de un cache, buscamos todas las claves que coinciden con el patron cacheName:* y las
   * borramos de una vez usando los comandos de Redis disponibles en Quarkus 3.40.1.
   */
  private void invalidarDirectoEnRedis(String cacheName, ReactiveRedisDataSource redis)
      throws Exception {
    ReactiveKeyCommands<String> keyCommands = redis.key();
    String patron = cacheName + ":*";

    // Buscar todas las claves que coinciden con el patron
    var claves = keyCommands.keys(patron).await().atMost(timeoutCache);

    // Si hay claves, borrarlas todas de una vez
    if (claves != null && !claves.isEmpty()) {
      keyCommands.del(claves.toArray(new String[0])).await().atMost(timeoutCache);
    }

    LOG.debugf(
        "Cache '%s' invalidada directamente en Redis (patron '%s', claves borradas: %d)",
        cacheName, patron, claves == null ? 0 : claves.size());
  }

  /** Si hay una transaccion activa en este hilo. */
  private boolean transaccionActiva() {
    try {
      return registry.getTransactionStatus() != Status.STATUS_NO_TRANSACTION;
    } catch (RuntimeException e) {
      // Si el registro de sincronizaciones no responde, se invalida de inmediato: es peor invalidar
      // antes del commit que no invalidar nunca.
      return false;
    }
  }

  /** Contador de fallos de invalidacion, con las mismas etiquetas que el de la lectura. */
  private io.micrometer.core.instrument.Counter contador(String cacheName) {
    return meterRegistry.counter("cache.errors", "cache", cacheName, "replica", replicaId);
  }

  /**
   * Solicitud de invalidacion de una cache.
   *
   * <p>Es el evento que viaja del caso de uso (dentro de la transaccion) al observador que invalida
   * de verdad (despues del commit).
   *
   * @param cacheName nombre del cache a invalidar
   */
  public record Invalidacion(String cacheName) {}
}
