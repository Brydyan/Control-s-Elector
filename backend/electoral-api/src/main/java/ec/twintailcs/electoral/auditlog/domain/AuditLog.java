package ec.twintailcs.electoral.auditlog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Map;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Entrada de auditoría para operaciones críticas (registro, disputa,
 * aprobación, anulación de actas; CRUD de configuración).
 *
 * <p>La columna {@code detalle} es JSONB en Postgres: almacenamos
 * un payload arbitrario tipado como {@code Map<String,Object>} que el
 * {@code AuditLogService} construye como JSON estructurado.
 * Hibernate 6 lo mapea vía {@link SqlTypes#JSON} → JSONB nativo.
 *
 * <p>La columna {@code ip_address} se completa en el servicio leyendo
 * de {@code request.getRemoteAddr()} o de un header confiable
 * configurado por el gateway (en PR γ se reemplaza
 * {@code X-Forwarded-For} ciego por la IP real del proxy).
 */
@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 64)
    private String entidad;

    @Column(name = "entidad_id", nullable = false)
    private Long entidadId;

    @Column(nullable = false, length = 32)
    private String accion;

    /**
     * Payload arbitrario en JSONB. Hibernate 6 lo mapea a JSONB nativo
     * de Postgres vía {@link SqlTypes#JSON}: el {@code Map} se serializa
     * a JSON al persistir y se deserializa al leer.
     *
     * <p>El {@code AuditLogService} construye el payload (por ejemplo
     * {@code {"message": texto}}) para que la columna jsonb acepte el
     * detalle sin coercionar a texto crudo.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private Map<String, Object> detalle;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "registrado_por")
    private Long registradoPor;

    @CreationTimestamp
    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    public AuditLog() {
    }

    public Long getId() {
        return id;
    }

    public String getEntidad() {
        return entidad;
    }

    public void setEntidad(String entidad) {
        this.entidad = entidad;
    }

    public Long getEntidadId() {
        return entidadId;
    }

    public void setEntidadId(Long entidadId) {
        this.entidadId = entidadId;
    }

    public String getAccion() {
        return accion;
    }

    public void setAccion(String accion) {
        this.accion = accion;
    }

    public Map<String, Object> getDetalle() {
        return detalle;
    }

    public void setDetalle(Map<String, Object> detalle) {
        this.detalle = detalle;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public Long getRegistradoPor() {
        return registradoPor;
    }

    public void setRegistradoPor(Long registradoPor) {
        this.registradoPor = registradoPor;
    }

    public Instant getTimestamp() {
        return timestamp;
    }
}