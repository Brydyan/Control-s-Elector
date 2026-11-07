package ec.twintailcs.electoral.auth.infrastructure.messaging;

import ec.twintailcs.electoral.auth.application.InvitationAuditService;
import ec.twintailcs.electoral.auth.application.port.InvitationEmailQueue;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailAEnviar;
import ec.twintailcs.electoral.auth.infrastructure.email.EmailService;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Cola de emails en memoria, con worker propio, limite de tasa y reintentos.
 *
 * <p>Por que en memoria y no en Redis: el design (D3) pide una cola asincrona con rate limit y
 * reintentos, y la decision D5 deja el reenvio en manos de MASTER. Un SMTP caido durante un
 * reinicio no deja al usuario sin salida: el envio quedo registrado como fallido y MASTER lo
 * reenvia. Meter Redis para esta ventana de 24 horas agregaria una dependencia de infraestructura
 * (un servicio mas que desplegar, monitorear y respaldar) a cambio de sobrevivir a un reinicio del
 * proceso que la decision D5 ya resuelve por otra via.
 *
 * <p>El worker es un hilo daemon que sondea la cola cada {@code pollIntervalMs} y procesa hasta
 * {@code batchSize} emails por vuelta, con dos límites:
 *
 * <ul>
 *   <li>Limite de tasa: {@code rateLimit} emails por ventana de {@code rateLimitWindowSeconds}. Es
 *       lo que evita que el SMTP nos throttlee o nos marque como spam (decision D9).
 *   <li>Reintentos: hasta {@code maxAttempts} intentos por email, esperando {@code initialDelay *
 *       multiplicador^n} entre ellos.
 * </ul>
 *
 * <p>Cada intento fallido queda auditado, de modo que MASTER ve en el panel exactamente a quien no
 * le llego el correo y por que.
 */
@ApplicationScoped
public class InvitationEmailQueueImpl implements InvitationEmailQueue {

  private static final Logger log = Logger.getLogger(InvitationEmailQueueImpl.class);

  private final EmailService emailService;
  private final InvitationAuditService auditService;

  private final int batchSize;
  private final long pollIntervalMs;
  private final int rateLimit;
  private final long rateLimitWindowSeconds;
  private final int maxAttempts;
  private final long initialDelayMs;
  private final double backoffMultiplier;

  private final BlockingQueue<EmailAEnviar> pendientes;

  /** Emails enviados en la ventana de rate limit actual. */
  private final AtomicInteger enviadosEnLaVentana = new AtomicInteger(0);

  /** Instante (ms) en que arranca la ventana de rate limit actual. */
  private volatile long inicioVentana;

  private final Thread worker;
  private volatile boolean activo = true;

  public InvitationEmailQueueImpl(
      EmailService emailService,
      InvitationAuditService auditService,
      @ConfigProperty(name = "app.email.queue.batch-size") int batchSize,
      @ConfigProperty(name = "app.email.queue.poll-interval-ms") long pollIntervalMs,
      @ConfigProperty(name = "app.email.queue.rate-limit") int rateLimit,
      @ConfigProperty(name = "app.email.queue.rate-limit-window-seconds")
          long rateLimitWindowSeconds,
      @ConfigProperty(name = "app.email.queue.capacity") int capacity,
      @ConfigProperty(name = "app.email.retry.max-attempts") int maxAttempts,
      @ConfigProperty(name = "app.email.retry.initial-delay-ms") long initialDelayMs,
      @ConfigProperty(name = "app.email.retry.backoff-multiplier") double backoffMultiplier) {
    this.emailService = emailService;
    this.auditService = auditService;
    this.batchSize = batchSize;
    this.pollIntervalMs = pollIntervalMs;
    this.rateLimit = rateLimit;
    this.rateLimitWindowSeconds = rateLimitWindowSeconds;
    this.maxAttempts = maxAttempts;
    this.initialDelayMs = initialDelayMs;
    this.backoffMultiplier = backoffMultiplier;
    this.pendientes = new LinkedBlockingQueue<>(capacity);

    this.inicioVentana = System.currentTimeMillis();
    this.worker = new Thread(this::bucle, "invitacion-email-worker");
    this.worker.setDaemon(true);
    this.worker.start();
  }

  @Override
  public void enqueue(EmailAEnviar email) {
    if (email == null) {
      return;
    }
    boolean encolado = pendientes.offer(email);
    if (!encolado) {
      // La cola esta llena: se registra el fallo en vez de propagarlo. El endpoint
      // ya respondio 202 y el MASTER va a ver el fallo en el panel.
      log.warnf("Cola de invitaciones llena, se descarta el email a %s", email.destinatario());
      auditService.logInvitationFailed(email.usuarioId(), email.destinatario(), "cola_llena");
    }
  }

  @Override
  public int pendientes() {
    return pendientes.size();
  }

  /**
   * Procesa la cola hasta el limite de la tanda.
   *
   * <p>Esta es la operacion que el worker repite y la que los tests invocan de forma sincrona.
   *
   * @return cantidad de emails procesados en esta tanda
   */
  public int drenar() {
    int procesados = 0;
    for (int i = 0; i < batchSize; i++) {
      EmailAEnviar email = pendientes.poll();
      if (email == null) {
        break;
      }
      enviar(email);
      procesados++;
    }
    return procesados;
  }

  /** Envia un email con reintentos y backoff. */
  private void enviar(EmailAEnviar email) {
    long espera = initialDelayMs;
    for (int intento = 1; intento <= maxAttempts; intento++) {
      if (intento > 1) {
        dormir(espera);
        espera = (long) (espera * backoffMultiplier);
      }
      try {
        esperarTurnoDeTasa();
        if (email.tipo() == EmailAEnviar.TipoEmail.CONFIRMACION) {
          emailService.sendActivationConfirmation(email.destinatario(), email.nombreCompleto());
        } else {
          emailService.sendInvitationEmail(email);
        }
        log.debugf("Email enviado a %s (intento %d)", email.destinatario(), intento);
        return;
      } catch (RuntimeException e) {
        log.errorf(
            e,
            "Falló el envío del email a %s (intento %d de %d)",
            email.destinatario(),
            intento,
            maxAttempts);
        auditService.logInvitationFailed(
            email.usuarioId(),
            email.destinatario(),
            e.getMessage() == null ? "error" : e.getMessage());
      }
    }
  }

  /**
   * Espera a que haya cupo en la ventana de rate limit.
   *
   * <p>La ventana corre de forma independiente del reloj de envio: si ya se envio el tope, se
   * espera lo que falte para que la ventana termine y se reabra.
   */
  private void esperarTurnoDeTasa() {
    if (rateLimit <= 0) {
      return;
    }
    while (true) {
      long ahora = System.currentTimeMillis();
      long duracionVentana = rateLimitWindowSeconds * 1000L;
      if (ahora - inicioVentana >= duracionVentana) {
        // Ventana vencida: se reabre.
        inicioVentana = ahora;
        enviadosEnLaVentana.set(0);
      }
      if (enviadosEnLaVentana.get() < rateLimit) {
        enviadosEnLaVentana.incrementAndGet();
        return;
      }
      long restante = duracionVentana - (ahora - inicioVentana);
      dormir(Math.max(restante, 1L));
    }
  }

  private void dormir(long milisegundos) {
    if (milisegundos <= 0) {
      return;
    }
    try {
      TimeUnit.MILLISECONDS.sleep(milisegundos);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
    }
  }

  /**
   * Bucle del worker: sondea la cola y procesa tandas.
   *
   * <p>La espera va antes del drenado: al arrancar la cola esta vacia y no hay nada que enviar.
   * Drenar primero ademas abre una carrera entre el arranque y quien encola — el worker se llevaria
   * un email que todavia no deberia haberse enviado.
   */
  private void bucle() {
    while (activo) {
      try {
        TimeUnit.MILLISECONDS.sleep(pollIntervalMs);
        drenar();
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      } catch (RuntimeException e) {
        log.errorf("Error inesperado en el worker de invitaciones: %s", e.getMessage());
      }
    }
  }

  /** Detiene el worker al apagar la aplicacion. */
  @PreDestroy
  void detener() {
    activo = false;
    worker.interrupt();
  }
}
