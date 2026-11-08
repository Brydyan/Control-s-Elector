package ec.twintailcs.electoral.legal.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import ec.twintailcs.electoral.legal.domain.LegalException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Limite de solicitudes al DPO por IP.
 *
 * <p>El formulario del DPO es publico: lo puede usar cualquiera, no solo un usuario del sistema.
 * Sin limite se convierte en un canal de spam y en una forma de inflar la tabla a voluntad. El test
 * importa porque el limite es la unica defensa de ese endpoint.
 *
 * <p>El limite es por IP y no por usuario a proposito: quien no tiene cuenta tampoco tiene sesion
 * que lo identifique, y el que si la tiene no por eso puede mandar diez solicitudes seguidas.
 */
class DpoRateLimiterTest {

  private final DpoRateLimiter limiter = new DpoRateLimiter();

  @Test
  @DisplayName("la primera solicitud de una IP se acepta")
  void laPrimeraSolicitudSeAcepta() {
    limiter.registrar("203.0.113.1");

    assertThat(limiter.solicitudes("203.0.113.1")).isEqualTo(1);
  }

  @Test
  @DisplayName("la segunda solicitud dentro de la ventana se rechaza con 429")
  void laSegundaSolicitudDentroDeLaVentanaSeRechaza() {
    limiter.registrar("203.0.113.2");

    assertThatThrownBy(() -> limiter.registrar("203.0.113.2"))
        .isInstanceOf(LegalException.class)
        .satisfies(
            e -> {
              LegalException ex = (LegalException) e;
              assertThat(ex.status()).as("el limite se responde con 429").isEqualTo(429);
              assertThat(ex.retryAfterSeconds())
                  .as("dice cuanto falta para reintentar")
                  .isBetween(1, (int) DpoRateLimiter.VENTANA_SEGUNDOS);
              assertThat(ex.getMessage()).contains("30 seconds");
            });
  }

  @Test
  @DisplayName("el limite es por IP: otra direccion no hereda el consumo")
  void elLimiteEsPorIp() {
    limiter.registrar("203.0.113.3");

    assertThat(limiter.solicitudes("203.0.113.4")).as("otra IP arranca en cero").isZero();
    assertThatCode(() -> limiter.registrar("203.0.113.4"))
        .as("otra IP puede enviar la suya")
        .doesNotThrowAnyException();
  }

  @Test
  @DisplayName("sin IP la cuenta cae en una clave compartida y tambien se limita")
  void sinIpLaCuentaCaenEnUnaClaveCompartida() {
    limiter.registrar(null);

    assertThatThrownBy(() -> limiter.registrar(" "))
        .as("una peticion sin IP real no esquiva el limite")
        .isInstanceOf(LegalException.class);
  }
}
