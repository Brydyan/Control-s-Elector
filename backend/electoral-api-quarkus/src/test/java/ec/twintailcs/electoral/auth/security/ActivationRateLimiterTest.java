package ec.twintailcs.electoral.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.auth.domain.InvitacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Limite de intentos de activacion por IP.
 *
 * <p>El endpoint de activacion es publico. Sin este limite, es un oracle que permite adivinar
 * tokens: cada intento unlimited le regala al atacante el trabajo de la fuerza bruta. El test
 * importa porque el limite es la unica defensa de ese endpoint — el token tiene 24 horas de vida y
 * la firma es de 256 bits, pero el tiempo de CPU que el servidor gasta probando es gratis para
 * quien lo pide.
 */
class ActivationRateLimiterTest {

  private final ActivationRateLimiter limiter = new ActivationRateLimiter();

  @Test
  @DisplayName("dentro del tope, los intentos se cuentan y no se rechaza")
  void dentroDelTopeLosIntentosSeCuentanYNoSeRechaza() {
    for (int i = 0; i < ActivationRateLimiter.MAX_INTENTOS; i++) {
      limiter.registrarIntento("10.0.0.1");
    }

    assertThat(limiter.intentos("10.0.0.1")).isEqualTo(ActivationRateLimiter.MAX_INTENTOS);
  }

  @Test
  @DisplayName("el intento que supera el tope se rechaza con 429")
  void elIntentoQueSuperaElTopeSeRechaza() {
    for (int i = 0; i < ActivationRateLimiter.MAX_INTENTOS; i++) {
      limiter.registrarIntento("10.0.0.2");
    }

    assertThatThrownBy(() -> limiter.registrarIntento("10.0.0.2"))
        .isInstanceOf(InvitacionException.class)
        .satisfies(
            e ->
                assertThat(((InvitacionException) e).status())
                    .as("el limite se responde con 429")
                    .isEqualTo(429));
  }

  @Test
  @DisplayName("el límite es por IP: otra dirección no hereda el consumo")
  void elLimiteEsPorIp() {
    for (int i = 0; i < ActivationRateLimiter.MAX_INTENTOS + 1; i++) {
      try {
        limiter.registrarIntento("10.0.0.3");
      } catch (InvitacionException ignorada) {
        // se espera: esta IP ya agoto el tope
      }
    }

    assertThat(limiter.intentos("10.0.0.4")).as("otra IP arranca en cero").isZero();
  }
}
