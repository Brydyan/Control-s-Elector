package ec.twintailcs.electoral.evidencia.domain;

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

/**
 * Observación marcada por el OBSERVER sobre su evidencia. Junction entity entre {@link Evidencia}
 * y {@link TipoObservacion}.
 *
 * <p>Diseño (per design.md D1): tabla junction propia en vez de columna jsonb en evidencia.
 * La restricción de «máximo 2» se aplica en {@link #MAX_POR_EVIDENCIA} (RF-OBS-02) y se
 * respalda con trigger/constraint a nivel de BD (cambset 020).
 */
@Entity
@Table(name = "evidencia_observacion")
@Getter
@Setter
@NoArgsConstructor
public class ObservacionEvidencia {

  /** Máximo de observaciones por evidencia (RF-OBS-02). Validado en servicio. */
  public static final int MAX_POR_EVIDENCIA = 2;

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(fetch = FetchType.LAZY, optional = false)
  @JoinColumn(name = "evidencia_id", nullable = false)
  private Evidencia evidencia;

  @Enumerated(EnumType.STRING)
  @Column(name = "tipo", nullable = false, length = 32)
  private TipoObservacion tipo;

  @Column(name = "creado_en", nullable = false, updatable = false)
  private Instant creadoEn;
}
