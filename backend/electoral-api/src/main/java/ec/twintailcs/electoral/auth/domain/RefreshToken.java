package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "refresh_token")
public class RefreshToken {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String token;

  @ManyToOne(fetch = FetchType.LAZY)
  @JoinColumn(name = "usuario_id", nullable = false)
  private Usuario usuario;

  @Column(nullable = false)
  private LocalDateTime expiresAt;

  @Column(nullable = false)
  private boolean revocado = false;

  @Column(nullable = false, updatable = false)
  private LocalDateTime creadoEn;

  @PrePersist
  public void antesDeGuardar() {
    this.creadoEn = LocalDateTime.now();
  }

  public RefreshToken() {}

  public boolean esValido() {
    return !revocado && LocalDateTime.now().isBefore(expiresAt);
  }

  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getToken() {
    return token;
  }

  public void setToken(String token) {
    this.token = token;
  }

  public Usuario getUsuario() {
    return usuario;
  }

  public void setUsuario(Usuario usuario) {
    this.usuario = usuario;
  }

  public LocalDateTime getExpiresAt() {
    return expiresAt;
  }

  public void setExpiresAt(LocalDateTime expiresAt) {
    this.expiresAt = expiresAt;
  }

  public boolean isRevocado() {
    return revocado;
  }

  public void setRevocado(boolean revocado) {
    this.revocado = revocado;
  }

  public LocalDateTime getCreadoEn() {
    return creadoEn;
  }
}
