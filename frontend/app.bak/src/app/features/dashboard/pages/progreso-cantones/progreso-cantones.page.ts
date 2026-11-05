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
  ProgresoCantonDto,
  ParroquiaProgresoDto,
} from '../../domain/models/dashboard.model';

/**
 * T-FE-15 C.1: Progreso de Escrutinio por Cantón — Vista territorial de avance.
 *
 * **Escenarios**:
 * - S1: Cargar datos de cantones + parroquias en un solo payload.
 * - S2: Mostrar tarjetas cantonales con barra de progreso y métricas.
 * - S3: Color dinámico según avance (green ≥80%, blue 50-80%, amber <50%).
 * - S4: Tabla de parroquias del cantón seleccionado.
 * - S5: Ordenamiento descendente por avance% en tabla.
 * - S6: Manejo de error con retry.
 *
 * **Accesibilidad**:
 * - aria-label en barras de progreso
 * - role="grid" en tabla de parroquias
 */
@Component({
  selector: 'app-progreso-cantones',
  standalone: true,
  imports: [
    CommonModule,
    CardComponent,
    LoadingStateComponent,
    ErrorStateComponent,
  ],
  templateUrl: './progreso-cantones.page.html',
  styleUrls: ['./progreso-cantones.page.scss'],
})
export class ProgresoCantonesPage implements OnInit, OnDestroy {
  private readonly dashboardService = inject(DashboardApi);
  private readonly destroy$ = new Subject<void>();

  // Estado de carga
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);

  // Datos cargados
  readonly cantonesData = signal<ProgresoCantonDto[]>([]);

  // Cantón seleccionado para ver desglose de parroquias (índice)
  readonly cantonSeleccionado = signal(0);

  ngOnInit(): void {
    this.cargarProgreso();
  }

  /**
   * Carga progreso de todos los cantones (una sola vez).
   * Implementa S1: un solo payload HTTP.
   */
  cargarProgreso(): void {
    this.loading.set(true);
    this.error.set(null);

    this.dashboardService
      .getProgresoDetalles()
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (cantones) => {
          this.cantonesData.set(cantones);
          this.cantonSeleccionado.set(0); // Preseleccionar primer cantón
          this.loading.set(false);
        },
        error: (err) => {
          console.error('Error al cargar progreso:', err);
          this.error.set(
            'No se pudieron cargar los datos de progreso. Por favor intenta nuevamente.',
          );
          this.loading.set(false);
        },
      });
  }

  /**
   * Cambia cantón seleccionado para ver desglose.
   * Implementa S4: cambiar sin nuevas llamadas HTTP.
   */
  cambiarCanton(index: number): void {
    if (index >= 0 && index < this.cantonesData().length) {
      this.cantonSeleccionado.set(index);
    }
  }

  /**
   * Retorna el cantón actualmente seleccionado.
   */
  get cantonActual(): ProgresoCantonDto | null {
    const cantones = this.cantonesData();
    const idx = this.cantonSeleccionado();
    return idx >= 0 && idx < cantones.length ? cantones[idx] : null;
  }

  /**
   * Retorna las parroquias del cantón seleccionado, ordenadas descendente por avance%.
   * Implementa S5: ordenamiento descendente.
   */
  get parroquiasOrdenadas(): ParroquiaProgresoDto[] {
    const canton = this.cantonActual;
    if (!canton || !canton.parroquias) return [];
    return [...canton.parroquias].sort((a, b) => b.avance - a.avance);
  }

  /**
   * Retorna clase CSS para color de barra según avance%.
   * Implementa S3: green ≥80%, blue 50-80%, amber <50%.
   *
   * **Mutación para test**: siempre retornar 'fill-green'
   */
  getColorClase(avance: number): string {
    if (avance >= 80) return 'fill-green';
    if (avance >= 50) return 'fill-blue';
    return 'fill-amber';
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }
}
