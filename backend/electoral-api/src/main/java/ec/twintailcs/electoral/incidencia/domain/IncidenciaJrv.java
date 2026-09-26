package ec.twintailcs.electoral.incidencia.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

/**
 * Incidencia operativa reportada por un veedor sobre una JRV (T-BE-19). Aislada del acta porque
 * representa un evento, no votos.
 */
@Entity
@Table(name = "incidencia_jrv")
@Getter
@Setter
@NoArgsConstructor
public class IncidenciaJrv {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "jrv_id", nullable = false)
  private ec.twintailcs.electoral.geografia.domain.Jrv jrv;

  @Enumerated(EnumType.STRING)
  @Column(nullable = false, length = 50)
  private TipoIncidenciaJrv tipo;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String descripcion;

  @Column(name = "evidencia_url", length = 500)
  private String evidenciaUrl;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "reportada_por_id", nullable = false)
  private ec.twintailcs.electoral.auth.domain.Usuario reportadaPor;

  @CreationTimestamp
  @Column(name = "fecha_registro", nullable = false, updatable = false)
  private Instant fechaRegistro;
}
