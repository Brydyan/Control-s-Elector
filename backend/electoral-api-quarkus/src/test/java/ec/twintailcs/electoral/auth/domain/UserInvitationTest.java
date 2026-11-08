package ec.twintailcs.electoral.auth.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Estado y vencimiento de la invitacion (T-AUTH-INV-02).
 *
 * <p>El estado guardado es {@code INVITED} o {@code ACTIVATED}. {@code EXPIRED} no se escribe: se
 * deriva del tiempo. Estas pruebas fijan esa regla, que es la que hace que una invitacion vieja no
 * se pueda dar por buena.
 */
class UserInvitationTest {

  private UserInvitation invitacion() {
    UserInvitation invitacion = new UserInvitation();
    invitacion.setUsuarioId(1L);
    invitacion.setEmailPersonal("juan@example.com");
    invitacion.setInvitedBy(2L);
    invitacion.setInvitedAt(Instant.now());
    invitacion.setExpiryAt(Instant.now().plus(Duration.ofDays(UserInvitation.DIAS_VIGENCIA)));
    return invitacion;
  }

  @Test
  @DisplayName("una invitacion nueva esta INVITED y no expira")
  void unaInvitacionNuevaEstaInvited() {
    UserInvitation invitacion = invitacion();

    assertThat(invitacion.getStatus()).isEqualTo(UserInvitation.STATUS_INVITED);
    assertThat(invitacion.estadoActual()).isEqualTo(UserInvitation.STATUS_INVITED);
    assertThat(invitacion.estaExpirada()).isFalse();
  }

  @Test
  @DisplayName("al activarse queda ACTIVATED y con su marca de tiempo")
  void alActivarseQuedaActivated() {
    UserInvitation invitacion = invitacion();
    Instant momento = Instant.now();

    invitacion.marcarActivada(momento);

    assertThat(invitacion.getStatus()).isEqualTo(UserInvitation.STATUS_ACTIVATED);
    assertThat(invitacion.estadoActual()).isEqualTo(UserInvitation.STATUS_ACTIVATED);
    assertThat(invitacion.getActivatedAt()).isEqualTo(momento);
  }

  @Test
  @DisplayName("pasada la ventana la invitacion queda EXPIRED aunque nadie la marque")
  void pasadaLaVentanaQuedaExpired() {
    UserInvitation invitacion = invitacion();
    invitacion.setExpiryAt(Instant.now().minus(Duration.ofDays(1)));

    assertThat(invitacion.estadoActual()).isEqualTo(UserInvitation.STATUS_EXPIRED);
    assertThat(invitacion.estaExpirada()).isTrue();
  }

  @Test
  @DisplayName("una invitacion activada no caduca aunque pase el tiempo")
  void unaInvitacionActivadaNoCaduca() {
    UserInvitation invitacion = invitacion();
    invitacion.marcarActivada(Instant.now());
    invitacion.setExpiryAt(Instant.now().minus(Duration.ofDays(1)));

    assertThat(invitacion.estadoActual()).isEqualTo(UserInvitation.STATUS_ACTIVATED);
    assertThat(invitacion.estaExpirada()).isFalse();
  }

  @Test
  @DisplayName("la ventana de vigencia es de 30 dias")
  void laVigenciaEsDe30Dias() {
    assertThat(UserInvitation.DIAS_VIGENCIA).isEqualTo(30);
  }
}
