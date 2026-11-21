package ec.twintailcs.electoral.common.infrastructure.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("LocalRateLimitBackend (fallback en memoria)")
class LocalRateLimitBackendTest {

  private static final Duration WINDOW = Duration.ofSeconds(60);
  private static final Duration TIMEOUT = Duration.ofMillis(50);

  private final AtomicLong now = new AtomicLong(1_000_000);

  @Test
  @DisplayName("cada incremento suma uno dentro de la ventana")
  void incrementaDentroDeLaVentana() {
    LocalRateLimitBackend backend = new LocalRateLimitBackend(100, now::get);

    assertThat(backend.increment("k", WINDOW, TIMEOUT).count()).isEqualTo(1);
    assertThat(backend.increment("k", WINDOW, TIMEOUT).count()).isEqualTo(2);
    assertThat(backend.increment("otra", WINDOW, TIMEOUT).count())
        .as("claves distintas no comparten contador")
        .isEqualTo(1);
  }

  @Test
  @DisplayName("al vencer la ventana el contador se reinicia")
  void reiniciaAlVencer() {
    LocalRateLimitBackend backend = new LocalRateLimitBackend(100, now::get);
    backend.increment("k", WINDOW, TIMEOUT);
    backend.increment("k", WINDOW, TIMEOUT);

    now.addAndGet(WINDOW.toMillis());

    assertThat(backend.increment("k", WINDOW, TIMEOUT).count()).isEqualTo(1);
  }

  @Test
  @DisplayName("el TTL devuelto es lo que le queda a la ventana")
  void ttlRestante() {
    LocalRateLimitBackend backend = new LocalRateLimitBackend(100, now::get);
    backend.increment("k", WINDOW, TIMEOUT);

    now.addAndGet(20_000);

    assertThat(backend.increment("k", WINDOW, TIMEOUT).ttlMillis()).isEqualTo(40_000);
  }

  @Test
  @DisplayName("peek no modifica el contador y devuelve 0 si la ventana no existe o vencio")
  void peekNoModifica() {
    LocalRateLimitBackend backend = new LocalRateLimitBackend(100, now::get);

    assertThat(backend.peek("k", TIMEOUT).count()).isZero();
    backend.increment("k", WINDOW, TIMEOUT);
    assertThat(backend.peek("k", TIMEOUT).count()).isEqualTo(1);
    assertThat(backend.peek("k", TIMEOUT).count()).as("peek no incrementa").isEqualTo(1);

    now.addAndGet(WINDOW.toMillis());
    assertThat(backend.peek("k", TIMEOUT).count()).isZero();
  }

  @Test
  @DisplayName("la memoria esta acotada: al llenarse purga ventanas vencidas")
  void purgaVencidasAlLlenarse() {
    LocalRateLimitBackend backend = new LocalRateLimitBackend(2, now::get);
    backend.increment("a", WINDOW, TIMEOUT);
    backend.increment("b", WINDOW, TIMEOUT);

    now.addAndGet(WINDOW.toMillis());
    backend.increment("c", WINDOW, TIMEOUT);

    assertThat(backend.size()).as("a y b vencieron y se purgaron").isEqualTo(1);
  }

  @Test
  @DisplayName("sin lugar y sin vencidas, permite sin registrar en vez de crecer sin limite")
  void sinLugarNoCrece() {
    LocalRateLimitBackend backend = new LocalRateLimitBackend(2, now::get);
    backend.increment("a", WINDOW, TIMEOUT);
    backend.increment("b", WINDOW, TIMEOUT);

    RateLimitBackend.Counter c = backend.increment("c", WINDOW, TIMEOUT);

    assertThat(c.count()).isEqualTo(1);
    assertThat(backend.size()).isEqualTo(2);
    assertThat(backend.increment("a", WINDOW, TIMEOUT).count())
        .as("las claves existentes siguen contando")
        .isEqualTo(2);
  }
}
