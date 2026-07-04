package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import java.time.Instant;

/**
 * Usuario del sistema. La identidad sigue siendo email + password (BCrypt).
 *
 * El campo {@code rol} es el nombre del rol (no FK). Esto acopla
 * {@code Usuario} y {@code RolPermiso} por String, no por ID. Es
 * suficiente para esta version: los nombres de rol se siembran en
 * {@code DataSeeder} y no se editan en runtime.
 */
@Entity
@Table(name = "usuario", indexes = {
        @Index(name = "ix_usuario_email", columnList = "email", unique = true),
        @Index(name = "ix_usuario_rol", columnList = "rol")
})
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 254, unique = true)
    private String email;

    /** BCrypt hash. Nunca plain text. */
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @Column(nullable = false, length = 32)
    private String rol;

    @Column(nullable = false)
    private boolean habilitado = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public String getRol() { return rol; }
    public void setRol(String rol) { this.rol = rol; }
    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
}
