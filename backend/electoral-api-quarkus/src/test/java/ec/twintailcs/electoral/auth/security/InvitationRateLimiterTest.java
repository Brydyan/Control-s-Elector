package ec.twintailcs.electoral.auth.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.auth.domain.InvitacionException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Limite de invitaciones por IP: una cada 5 segundos (T-AUTH-INV-02, D5).
 *
 * <p>El endpoint crea cuentas y manda correos. Sin limite, un script puede fills el sistema de
 * cuentas y usarlo como una central de spam. El test importa porque el limite es la unica defensa
 * contra eso en el lado del servidor.
 */
class InvitationRateLimiterTest {

  private final InvitationRateLimiter limiter = new InvitationRateLimiter();

  @Test
  @DisplayName("la primera invitacion de una IP se acepta")
  void laPrimeraInvitacionSeAcepta() {
    limiter.registrar("203.0.113.1");

    assertThat(limiter.invitaciones("203.0.113.1")).isEqualTo(1);
  }

  @Test
  @DisplayName("la segunda invitacion dentro de la ventana se rechaza con 429 y Retry-After")
  void laSegundaInvitacionDentroDeLaVentanaSeRechaza() {
    limiter.registrar("203.0.113.2");

    assertThatThrownBy(() -> limiter.registrar("203.0.113.2"))
        .isInstanceOf(InvitacionException.class)
        .satisfies(
            e -> {
              InvitacionException ex = (InvitacionException) e;
              assertThat(ex.status()).as("el limite se responde con 429").isEqualTo(429);
              assertThat(ex.retryAfterSeconds())
                  .as("dice cuanto falta para reintentar")
                  .isBetween(1, (int) InvitationRateLimiter.VENTANA_SEGUNDOS);
            });
  }

  @Test
  @DisplayName("el limite es por IP: otra direccion no hereda el consumo")
  void elLimiteEsPorIp() {
    limiter.registrar("203.0.113.3");

    assertThat(limiter.invitaciones("203.0.113.4")).as("otra IP arranca en cero").isZero();
  }

  @Test
  @DisplayName("sin IP la cuenta cae en una clave compartida y tambien se limita")
  void sinIpLaCuentaCaeEnUnaClaveCompartida() {
    limiter.registrar(null);

    assertThatThrownBy(() -> limiter.registrar(" "))
        .as("una peticion sin IP real no esquiva el limite")
        .isInstanceOf(InvitacionException.class);
  }
}
