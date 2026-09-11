package ec.twintailcs.electoral.notificacion.domain;

import ec.twintailcs.electoral.acta.domain.Acta;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Notificación enviada a un usuario (disputa abierta, corrección solicitada, acta
 * aprobada/anulada).
 *
 * <p>Nota histórica: la columna se llama {@code destinatario_id} (no {@code observer_id}) desde la
 * consolidación del esquema en PR α.
 *
 * <p>El {@code NotificacionService} persiste filas en operaciones críticas. Falta el canal de
 * entrega (controller + websocket/email/push); eso se cierra en PR δ junto con T-BE-16.
 */
@Entity
@Table(name = "notificacion")
public class Notificacion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "destinatario_id", nullable = false)
  private Long destinatarioId;

  @Column(nullable = false, length = 64)
  private String tipo;

  @ManyToOne
  @JoinColumn(name = "acta_id")
  private Acta acta;

  @Column(nullable = false, length = 500)
  private String mensaje;

  @Column(nullable = false)
  private Boolean leida = false;

  @CreationTimestamp
  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;

  public Notificacion() {}

  public Long getId() {
    return id;
  }

  public Long getDestinatarioId() {
    return destinatarioId;
  }

  public void setDestinatarioId(Long destinatarioId) {
    this.destinatarioId = destinatarioId;
  }

  public String getTipo() {
    return tipo;
  }

  public void setTipo(String tipo) {
    this.tipo = tipo;
  }

  public Acta getActa() {
    return acta;
  }

  public void setActa(Acta acta) {
    this.acta = acta;
  }

  public String getMensaje() {
    return mensaje;
  }

  public void setMensaje(String mensaje) {
    this.mensaje = mensaje;
  }

  public Boolean getLeida() {
    return leida;
  }

  public void setLeida(Boolean leida) {
    this.leida = leida;
  }

  public Instant getCreadoEn() {
    return creadoEn;
  }
}
