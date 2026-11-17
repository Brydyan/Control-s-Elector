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
 * Aceptación de los términos y la política de privacidad.
 *
 * <p>Es un registro de solo agregado: se escribe cuando alguien acepta y nunca se modifica. La
 * versión vigente en {@code usuario.accepted_terms_version} es un atajo; esta tabla es la prueba:
 * qué aceptó cada persona, cuándo, desde qué IP y con qué cliente.
 *
 * <p>El historico se conserva aunque el usuario borre su cuenta: la FK es {@code ON DELETE
 * CASCADE}, pero las aceptaciones se archivan aparte por la obligacion de demostrar el
 * consentimiento. Ver {@code 026-legal-compliance.sql}.
 */
@Entity
@Table(name = "terms_acceptance_audit")
public class TermsAcceptanceAudit extends PanacheEntityBase {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  Long id;

  @Column(name = "usuario_id", nullable = false)
  Long usuarioId;

  /** Version de los terminos aceptada, tal como viene del claim ("1.0", "1.1"). */
  @Column(name = "terms_version", nullable = false, length = 10)
  String termsVersion;

  /** Version de la politica de privacidad aceptada. Puede ir por detras de la de terminos. */
  @Column(name = "privacy_version", length = 10)
  String privacyVersion;

  @Column(name = "accepted_at", nullable = false)
  Instant acceptedAt = Instant.now();

  @Column(name = "ip_address", length = 45)
  String ipAddress;

  @Column(name = "user_agent", length = 500)
  String userAgent;

  @Column(name = "locale", length = 16)
  String locale;

  /**
   * Tipo de consentimiento: {@code initial} (primera aceptacion) o {@code update} (el usuario ya
   * habia aceptado una version anterior y se le pide una nueva).
   */
  @Column(name = "consent_type", nullable = false, length = 50)
  String consentType = "initial";

  public Long getId() {
    return id;
  }

  public Long getUsuarioId() {
    return usuarioId;
  }

  public void setUsuarioId(Long usuarioId) {
    this.usuarioId = usuarioId;
  }

  public String getTermsVersion() {
    return termsVersion;
  }

  public void setTermsVersion(String termsVersion) {
    this.termsVersion = termsVersion;
  }

  public String getPrivacyVersion() {
    return privacyVersion;
  }

  public void setPrivacyVersion(String privacyVersion) {
    this.privacyVersion = privacyVersion;
  }

  public Instant getAcceptedAt() {
    return acceptedAt;
  }

  public void setAcceptedAt(Instant acceptedAt) {
    this.acceptedAt = acceptedAt;
  }

  public String getIpAddress() {
    return ipAddress;
  }

  public void setIpAddress(String ipAddress) {
    this.ipAddress = ipAddress;
  }

  public String getUserAgent() {
    return userAgent;
  }

  public void setUserAgent(String userAgent) {
    this.userAgent = userAgent;
  }

  public String getLocale() {
    return locale;
  }

  public void setLocale(String locale) {
    this.locale = locale;
  }

  public String getConsentType() {
    return consentType;
  }

  public void setConsentType(String consentType) {
    this.consentType = consentType;
  }
}
