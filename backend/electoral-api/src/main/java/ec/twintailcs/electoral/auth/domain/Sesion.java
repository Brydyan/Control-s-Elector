package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Sesion activa de un usuario.
 *
 * <p>Cada login crea una fila aqui y un par (access token, refresh token).
 */
@Entity
@Table(
    name = "sesion",
    indexes = {
      @Index(name = "ix_sesion_usuario", columnList = "usuario_id"),
      @Index(name = "ix_sesion_refresh", columnList = "refresh_hash", unique = true),
      @Index(name = "ix_sesion_revocado", columnList = "revocado")
    })
@Getter
@Setter
@NoArgsConstructor
public class Sesion {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(name = "usuario_id", nullable = false)
  private Long usuarioId;

  /** Identificador unico embebido en el JWT (claim jti). */
  @Column(name = "sesion_id", nullable = false, unique = true, length = 36)
  private String sesionId;

  /** BCrypt / HMAC hash del refresh token. Nunca se guarda el token plano. */
  @Column(name = "refresh_hash", nullable = false, length = 100, unique = true)
  private String refreshHash;

  @Column(nullable = false)
  private boolean revocado = false;

  @Column(name = "ip_cliente", length = 64)
  private String ipCliente;

  @Column(name = "user_agent", length = 256)
  private String userAgent;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn = Instant.now();

  @Column(name = "ultimo_uso_en", nullable = false)
  private Instant ultimoUsoEn = Instant.now();

  @Column(name = "expira_en", nullable = false)
  private Instant expiraEn;

  public void validarDatosObligatorios() {
    if (this.usuarioId == null) {
      throw new IllegalArgumentException("El ID de usuario es obligatorio para la sesión");
    }
    if (this.sesionId == null || this.sesionId.trim().isEmpty()) {
      throw new IllegalArgumentException("El sesionId es obligatorio");
    }
    if (this.refreshHash == null || this.refreshHash.trim().isEmpty()) {
      throw new IllegalArgumentException("El refreshHash es obligatorio");
    }
    if (this.expiraEn == null) {
      throw new IllegalArgumentException("La fecha de expiración es obligatoria");
    }
  }

  public boolean activa() {
    return !revocado && expiraEn != null && expiraEn.isAfter(Instant.now());
  }
}
