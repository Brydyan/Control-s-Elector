package ec.twintailcs.electoral.legal.domain;

import io.quarkus.hibernate.orm.panache.PanacheEntityBase;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Solicitud al Delegado de Proteccion de Datos (DPO).
 *
 * <p>El formulario es publico: lo puede usar cualquiera, no solo un usuario del sistema. Por eso
 * esta tabla no tiene FK a {@code usuario}: el mensaje puede venir de alguien que nunca entro.
 *
 * <p>Se guarda y NO se manda por email (decision D7 del design): el DPO puede estar unos dias sin
 * connectivity y el mensaje se pierde. La respuesta la escribe el DPO a mano en la misma fila, de
 * ahi los campos {@code dpoResponse} y {@code respondedAt}.
 */
@Entity
@Table(name = "dpo_contact_requests")
public class DpoContactRequest extends PanacheEntityBase {

  /** Estados posibles de la solicitud. */
  public static final String STATUS_NEW = "NEW";

  public static final String STATUS_IN_PROGRESS = "IN_PROGRESS";
  public static final String STATUS_RESOLVED = "RESOLVED";

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "name", nullable = false, length = 255)
  String name;

  @Column(name = "email", nullable = false, length = 254)
  String email;

  @Column(name = "subject", nullable = false, length = 255)
  String subject;

  @Column(name = "message", nullable = false, columnDefinition = "TEXT")
  String message;

  @Column(name = "submitted_at", nullable = false)
  Instant submittedAt = Instant.now();

  @Column(name = "ip_address", length = 45)
  String ipAddress;

  @Column(name = "status", nullable = false, length = 50)
  String status = STATUS_NEW;

  @Column(name = "dpo_response", columnDefinition = "TEXT")
  String dpoResponse;

  @Column(name = "responded_at")
  Instant respondedAt;

  public Long getId() {
    return id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name == null ? null : name.trim();
  }

  public String getEmail() {
    return email;
  }

  public void setEmail(String email) {
    this.email = email == null ? null : email.trim().toLowerCase();
  }

  public String getSubject() {
    return subject;
  }

  public void setSubject(String subject) {
    this.subject = subject == null ? null : subject.trim();
  }

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message == null ? null : message.trim();
  }

  public Instant getSubmittedAt() {
    return submittedAt;
  }

  public void setSubmittedAt(Instant submittedAt) {
    this.submittedAt = submittedAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getDpoResponse() {
    return dpoResponse;
  }

  public void setDpoResponse(String dpoResponse) {
    this.dpoResponse = dpoResponse;
  }

  public Instant getRespondedAt() {
    return respondedAt;
  }

  public void setRespondedAt(Instant respondedAt) {
    this.respondedAt = respondedAt;
  }
}
