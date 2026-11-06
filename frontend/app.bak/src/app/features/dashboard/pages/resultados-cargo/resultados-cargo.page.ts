import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { Subject, takeUntil } from 'rxjs';

import {
  CardComponent,
  ErrorStateComponent,
  LoadingStateComponent,
} from '../../../../shared/ui';
import { DashboardApi } from '../../data/dashboard.api';
import {
  ResultadoCargoDto,
  CandidatoResultadoDto,
} from '../../domain/models/dashboard.model';

/**
 * T-FE-15 B.1: Resultados por Cargo — Vista analítica de votos por dignidad y candidato.
 *
 * **Escenarios**:
 * - S1: Cargar datos de todos los cargos en un solo payload (sin duplicar HTTP).
 * - S2: Cambiar entre tabs sin nuevas llamadas HTTP.
 * - S3: Mostrar candidato/a líder con foto, votos, porcentaje, estado provisional.
 * - S4: Barras proporcionales reflejando votos / maxVotos.
 * - S5: Tarjetas resumen con Votos Válidos, Blancos, Nulos.
 * - S6: Manejo de error con retry.
 *
 * **Accesibilidad**:
 * - aria-label en tabs ("Resultados de {{cargoNombre}}")
 * - role="progressbar" en barras con aria-valuenow/min/max
 * - alt text en fotos
 * - role="alert" para estado de carga
 */
@Component({
  selector: 'app-resultados-cargo',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    LoadingStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './resultados-cargo.page.html',
  styleUrls: ['./resultados-cargo.page.scss'],
})
export class ResultadosCargoPage implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardApi);
  private readonly destroy$ = new Subject<void>();

  // Estado de carga
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Datos cargados
  readonly cargosData = signal<ResultadoCargoDto[]>([]);

  // Tab activo (índice)
  readonly cargoSeleccionado = signal(0);

  ngOnInit(): void {
    this.cargarResultados();
  }

  /**
   * Carga resultados de todos los cargos (una sola vez).
   * Implementa S1: un solo payload HTTP para todos los cargos.
   */
  cargarResultados(): void {
    this.loading.set(true);
    this.error.set(null);

    this.dashboardService
      .getResultadosDetalles()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cargos) => {
          this.cargosData.set(cargos);
          this.cargoSeleccionado.set(0); // Preseleccionar primera pestaña
          this.loading.set(false);
        },
        error: (err) => {
          console.error('Error al cargar resultados:', err);
          this.error.set(
            'No se pudieron cargar los resultados. Por favor intenta nuevamente.',
          );
          this.loading.set(false);
        },
      });
  }

  /**
   * Cambia cargo activo (sin nuevas llamadas HTTP).
   * Implementa S2: reactividad pura basada en signals.
   */
  cambiarCargo(index: number): void {
    if (index >= 0 && index < this.cargosData().length) {
      this.cargoSeleccionado.set(index);
    }
  }

  /**
   * Retorna el cargo actualmente seleccionado.
   */
  get cargoActual(): ResultadoCargoDto | null {
    const cargos = this.cargosData();
    const idx = this.cargoSeleccionado();
    return idx >= 0 && idx < cargos.length ? cargos[idx] : null;
  }

  /**
   * Retorna el candidato/a con más votos (líder).
   * Implementa S3: candidato puntero en tarjeta destacada.
   */
  get candidatoLider(): CandidatoResultadoDto | null {
    const cargo = this.cargoActual;
    if (!cargo || cargo.candidatos.length === 0) return null;
    return cargo.candidatos.reduce((prev, current) =>
      current.votos > prev.votos ? current : prev,
    );
  }

  /**
   * Calcula el ancho de la barra proporcional para un candidato.
   * Implementa S4: barras reflejando votos/maxVotos * 100%.
   * (Usado en template con [style.width.%])
   *
   * @param candidato Candidato a calcular
   * @returns Porcentaje de ancho (0..100)
   *
   * **Mutación para test**: cambiar a siempre retornar 100%
   */
  porcentajeAncho(candidato: CandidatoResultadoDto): number {
    const cargo = this.cargoActual;
    if (!cargo || cargo.candidatos.length === 0) return 0;
    const maxVotos = Math.max(...cargo.candidatos.map((c) => c.votos));
    if (maxVotos === 0) return 0;
    return (candidato.votos / maxVotos) * 100;
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
