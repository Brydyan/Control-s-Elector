package ec.twintailcs.electoral.common.application.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy.FailureMode;
import ec.twintailcs.electoral.common.application.ratelimit.RateLimitPolicy.KeyType;
import java.time.Duration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

@DisplayName("RateLimitPolicy y RateLimitDecision")
class RateLimitPolicyTest {

  private static RateLimitPolicy policy(long limit, Duration window) {
    return new RateLimitPolicy("p", limit, window, KeyType.USER, FailureMode.ALLOW);
  }

  @Test
  @DisplayName("una politica valida se construye")
  void politicaValida() {
    RateLimitPolicy p = policy(5, Duration.ofMinutes(5));

    assertThat(p.limit()).isEqualTo(5);
    assertThat(p.window()).isEqualTo(Duration.ofMinutes(5));
  }

  @Test
  @DisplayName("limit 0 o negativo impide construir la politica (falla al arrancar, no en runtime)")
  void limiteInvalido() {
    assertThatThrownBy(() -> policy(0, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("limit > 0");
    assertThatThrownBy(() -> policy(-1, Duration.ofMinutes(1)))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("una ventana menor a 1s se rechaza")
  void ventanaInvalida() {
    assertThatThrownBy(() -> policy(5, Duration.ofMillis(500)))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("al menos 1s");
  }

  @Test
  @DisplayName("sin nombre se rechaza")
  void sinNombre() {
    assertThatThrownBy(
            () -> new RateLimitPolicy(" ", 1, Duration.ofSeconds(1), KeyType.IP, FailureMode.ALLOW))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  @DisplayName("consume: permite hasta el limite inclusive y rechaza al superarlo")
  void consumeBloqueaAlSuperar() {
    assertThat(RateLimitDecision.fromCounter(5, 1000, 5, false, false).allowed()).isTrue();
    RateLimitDecision sexto = RateLimitDecision.fromCounter(6, 1000, 5, false, false);
    assertThat(sexto.allowed()).isFalse();
    assertThat(sexto.remaining()).isZero();
  }

  @Test
  @DisplayName("check: rechaza al llegar al limite (no cuenta la operacion actual)")
  void checkBloqueaAlLlegar() {
    assertThat(RateLimitDecision.fromCounter(4, 1000, 5, true, false).allowed()).isTrue();
    assertThat(RateLimitDecision.fromCounter(5, 1000, 5, true, false).allowed()).isFalse();
  }

  @Test
  @DisplayName("Retry-After redondea hacia arriba y nunca es 0 cuando se rechaza")
  void retryAfterRedondeaArriba() {
    assertThat(RateLimitDecision.fromCounter(6, 1500, 5, false, false).retryAfterSeconds())
        .isEqualTo(2);
    assertThat(RateLimitDecision.fromCounter(6, 0, 5, false, false).retryAfterSeconds())
        .isEqualTo(1);
    assertThat(RateLimitDecision.fromCounter(1, 1500, 5, false, false).retryAfterSeconds())
        .isZero();
  }

  @Test
  @DisplayName("remaining baja con cada operacion y no es negativo")
  void remaining() {
    assertThat(RateLimitDecision.fromCounter(1, 1000, 5, false, false).remaining()).isEqualTo(4);
    assertThat(RateLimitDecision.fromCounter(9, 1000, 5, false, false).remaining()).isZero();
  }
}
