package ec.twintailcs.electoral.auth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;
import org.hibernate.annotations.UuidGenerator;
import ec.twintailcs.electoral.shared.persistence.UuidV7Generator;


/**
 * Usuario del sistema. La identidad sigue siendo email + password (BCrypt).
 *
 * {@code rol} es ahora una FK a la entidad {@link Rol}.
 */
@Entity
@Table(name = "usuario", indexes = {
        @Index(name = "ix_usuario_email", columnList = "email", unique = true)
})
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @UuidGenerator(algorithm = UuidV7Generator.class)
    @Column(name = "public_id", nullable = false, unique = true, updatable = false)
    private UUID publicId;


    @Column(nullable = false, length = 254, unique = true)
    private String email;

    /** BCrypt hash. Nunca plain text. */
    @com.fasterxml.jackson.annotation.JsonIgnore
    @Column(nullable = false, length = 100)
    private String passwordHash;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "rol_id", nullable = false)
    private Rol rol;

    @Column(nullable = false)
    private boolean habilitado = true;

    @Column(name = "creado_en", nullable = false, updatable = false)
    private Instant creadoEn = Instant.now();

    @Column(nullable = false, length = 100)
    private String nombre;

    @Column(nullable = false, length = 100)
    private String apellido;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public UUID getPublicId() { return publicId; }
    public void setPublicId(UUID publicId) { this.publicId = publicId; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getPasswordHash() { return passwordHash; }
    public void setPasswordHash(String passwordHash) { this.passwordHash = passwordHash; }
    public Rol getRol() { return rol; }
    /** Devuelve el nombre del rol para uso en DTOs y JWT. */
    public String getRolNombre() { return rol != null ? rol.getNombre() : null; }
    public void setRol(Rol rol) { this.rol = rol; }
    public boolean isHabilitado() { return habilitado; }
    public void setHabilitado(boolean habilitado) { this.habilitado = habilitado; }
    public Instant getCreadoEn() { return creadoEn; }
    public void setCreadoEn(Instant creadoEn) { this.creadoEn = creadoEn; }
    public String getNombre() { return nombre; }
    public void setNombre(String nombre) { this.nombre = nombre; }
    public String getApellido() { return apellido; }
    public void setApellido(String apellido) { this.apellido = apellido; }
}
