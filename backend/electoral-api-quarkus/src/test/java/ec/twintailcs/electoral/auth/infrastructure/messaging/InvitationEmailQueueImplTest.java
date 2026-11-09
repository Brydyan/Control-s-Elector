package ec.twintailcs.electoral.auth.infrastructure.messaging;

import static org.assertj.core.api.Assertions.assertThat;

import ec.twintailcs.electoral.auditlog.application.AuditLogService;
import ec.twintailcs.electoral.auth.application.InvitationAuditService;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailService;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * Cola de emails: asincronia, limite de tasa y reintentos con backoff.
 *
 * <p>La cola se prueba sin CDI y sin base de datos: el servicio de email es un doble que cuenta los
 * intentos, y el de auditoria es un doble que anota las falhas. Lo que se verifica son las tres
 * garantias que hacen que el flujo de invitacion no se caiga cuando el SMTP se cae:
 *
 * <ul>
 *   <li>Encolar no envia: el endpoint que MASTER llama ya respondio 202.
 *   <li>Un envio fallido se reintenta, y al agotar los intentos queda auditado.
 *   <li>El limite de tasa frena la salida para no ser marcado como spam.
 * </ul>
 */
class InvitationEmailQueueImplTest {

  /** Email de prueba que registra cada intento y puede fallar las primeras veces. */
  private static final class EmailServiceDoble implements EmailService {
    private final List<EmailAEnviar> enviados = new ArrayList<>();
    private final AtomicInteger intentos = new AtomicInteger();
    private final int fallosAntesDeAceptar;

    EmailServiceDoble(int fallosAntesDeAceptar) {
      this.fallosAntesDeAceptar = fallosAntesDeAceptar;
    }

    @Override
    public void sendInvitationEmail(EmailAEnviar email) {
      intentos.incrementAndGet();
      if (intentos.get() <= fallosAntesDeAceptar) {
        throw new IllegalStateException("SMTP caido");
      }
      enviados.add(email);
    }

    @Override
    public void sendActivationConfirmation(String destinatario, String nombreCompleto) {
      intentos.incrementAndGet();
    }
  }

  /** Auditoria que no toca la base: solo anota lo que se le pide. */
  private static final class AuditDoble extends InvitationAuditService {
    private final List<String> fallos = new ArrayList<>();

    AuditDoble() {
      super(new AuditLogService(new com.fasterxml.jackson.databind.ObjectMapper()));
    }

    @Override
    public void logInvitationFailed(Long usuarioId, String email, String error) {
      fallos.add(email + ":" + error);
    }
  }

  private EmailServiceDoble emailService;
  private AuditDoble auditoria;

  private InvitationEmailQueueImpl cola(
      int batchSize, int rateLimit, int ventanaSegundos, int capacidad) {
    emailService = new EmailServiceDoble(0);
    auditoria = new AuditDoble();
    return new InvitationEmailQueueImpl(
        emailService,
        auditoria,
        batchSize,
        60_000,
        rateLimit,
        ventanaSegundos,
        capacidad,
        3,
        10,
        2);
  }

  private static EmailAEnviar email(String destinatario) {
    return new EmailAEnviar(
        1L,
        destinatario,
        destinatario,
        "Ana",
        "Perez",
        "OBSERVER",
        "http://front/activate?token=x",
        "2026-11-07",
        1,
        EmailAEnviar.TipoEmail.INVITACION);
  }

  @Test
  @DisplayName("encolar no envía el email: el que llama ya respondió 202")
  void encolarNoEnviaElEmail() {
    InvitationEmailQueueImpl cola = cola(10, 100, 60, 100);

    cola.enqueue(email("ana@control.ttcs"));

    assertThat(emailService.enviados).as("no debería haberse enviado nada todavía").isEmpty();
    assertThat(cola.pendientes()).as("el email debería estar en la cola").isEqualTo(1);
  }

  @Test
  @DisplayName("el worker envía lo que está en la cola")
  void elWorkerEnviaLoQueEstaEnLaCola() {
    InvitationEmailQueueImpl cola = cola(10, 100, 60, 100);
    cola.enqueue(email("ana@control.ttcs"));

    int procesados = cola.drenar();

    assertThat(procesados).as("debería procesar un email").isEqualTo(1);
    assertThat(emailService.enviados).hasSize(1);
    assertThat(cola.pendientes()).as("la cola debería quedar vacía").isZero();
  }

  @Test
  @DisplayName("un envío fallido se reintenta hasta agotar los intentos")
  void unEnvioFallidoSeReintentaHastaAgotarLosIntentos() {
    emailService = new EmailServiceDoble(99); // falla siempre
    auditoria = new AuditDoble();
    InvitationEmailQueueImpl cola =
        new InvitationEmailQueueImpl(emailService, auditoria, 10, 60_000, 100, 60, 100, 3, 10, 2);
    cola.enqueue(email("ana@control.ttcs"));

    cola.drenar();

    assertThat(emailService.intentos.get()).as("debería haber intentado tres veces").isEqualTo(3);
    assertThat(auditoria.fallos).as("el fallo final debería quedar auditado").hasSize(3);
  }

  @Test
  @DisplayName("un envío que falla una vez sale en el segundo intento")
  void unEnvioQueFallaUnaVezSaleEnElSegundoIntento() {
    emailService = new EmailServiceDoble(1); // falla el primero
    auditoria = new AuditDoble();
    InvitationEmailQueueImpl cola =
        new InvitationEmailQueueImpl(emailService, auditoria, 10, 60_000, 100, 60, 100, 3, 10, 2);
    cola.enqueue(email("ana@control.ttcs"));

    cola.drenar();

    assertThat(emailService.intentos.get()).as("debería reintentar una vez").isEqualTo(2);
    assertThat(emailService.enviados).as("el reintento debería haber salido").hasSize(1);
  }

  @Test
  @DisplayName("el retardo entre reintentos crece: el backoff es exponencial")
  void elRetardoEntreReintentosCrece() {
    // Con demora inicial de 60 ms y multiplicador 2, el segundo intento espera 60 ms y
    // el tercero 120 ms: 180 ms en total. Si el multiplicador fuese 1, serían 120 ms y
    // el SMTP recibiría los reintentos pegados, que es justo lo que el backoff evita.
    // Las demoras son amplias a propósito: con 10 ms el redondeo del sleep tapaba la
    // diferencia y la mutación pasaba sin que nadie lo notara.
    emailService = new EmailServiceDoble(99); // falla siempre
    auditoria = new AuditDoble();
    InvitationEmailQueueImpl cola =
        new InvitationEmailQueueImpl(emailService, auditoria, 10, 60_000, 100, 60, 100, 3, 60, 2);
    cola.enqueue(email("ana@control.ttcs"));

    long inicio = System.currentTimeMillis();
    cola.drenar();
    long transcurrido = System.currentTimeMillis() - inicio;

    assertThat(transcurrido)
        .as("el backoff debería esperar al menos 180 ms, tardó %d ms", transcurrido)
        .isGreaterThanOrEqualTo(160L);
  }

  @Test
  @DisplayName("el límite de tasa frena la salida: no se pasa del tope por ventana")
  void elLimiteDeTasaFrenaLaSalida() {
    // Tope de 2 por ventana de 1 segundo: el tercer email tiene que esperar a que se
    // reabra la ventana. Sin el limite, los tres saldrían de corrido y el SMTP nos
    // marcaría como spam (decisión D9 del design).
    InvitationEmailQueueImpl cola = cola(10, 2, 1, 100);
    cola.enqueue(email("a@control.ttcs"));
    cola.enqueue(email("b@control.ttcs"));
    cola.enqueue(email("c@control.ttcs"));

    long inicio = System.currentTimeMillis();
    cola.drenar();
    long transcurrido = System.currentTimeMillis() - inicio;

    assertThat(emailService.enviados).as("los tres deberían salir").hasSize(3);
    assertThat(transcurrido)
        .as("el tercero debería esperar a que se reabra la ventana, Tardó %d ms", transcurrido)
        .isGreaterThanOrEqualTo(900L);
  }

  @Test
  @DisplayName("una cola llena descarta el email y lo audita, pero no propaga el error")
  void unaColaLlenaDescartaElEmailYLoAudita() {
    InvitationEmailQueueImpl cola = cola(10, 100, 60, 1);
    cola.enqueue(email("a@control.ttcs"));

    cola.enqueue(email("b@control.ttcs"));

    assertThat(cola.pendientes()).as("la cola solo tiene lugar para uno").isEqualTo(1);
    assertThat(auditoria.fallos).as("el descarte debería quedar auditado").hasSize(1);
    assertThat(auditoria.fallos.get(0)).contains("cola_llena");
  }
}
