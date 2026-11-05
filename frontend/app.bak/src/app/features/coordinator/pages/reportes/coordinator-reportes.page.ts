import { CommonModule } from '@angular/common';
import { Component, OnInit, OnDestroy, inject, signal } from '@angular/core';
import { Subject, takeUntil, forkJoin } from 'rxjs';
import { CardComponent, LoadingStateComponent } from '../../../../shared/ui';
import { CoordinatorApi } from '../../data/coordinator.api';
import { ActaItem } from '../../domain/models/coordinator.model';

interface MetricasPanel {
  jrvsTotal: number;
  jrvsProcessed: number;
  percentageProgress: number;
  actasPorHora: number;
  realtimeStatus: 'connected' | 'disconnected';
}

interface EstadosCount {
  ENVIADA: number;
  EN_DISPUTA: number;
  CORREGIDA: number;
  APROBADA: number;
  ANULADA: number;
}

interface CantonProgress {
  canton: string;
  percentage: number;
}

interface CargoProgress {
  cargo: string;
  percentage: number;
}

/**
 * T-FE-17 Fase B: CoordinatorReportesPage — Centro de Reportes y Estadísticas
 *
 * Pantalla centralizada de métricas operativas para COORDINATOR, MASTER, ADMIN.
 * - Banner de progreso global en vivo
 * - Grid 3 columnas: progreso por cantón, estados actas, progreso por cargo
 * - Tabla de actas con botón descarga expediente PDF
 * - Métricas de resoluciones del día y tiempo promedio
 */
@Component({
  selector: 'app-coordinator-reportes',
  standalone: true,
  imports: [CommonModule, CardComponent, LoadingStateComponent],
  templateUrl: './coordinator-reportes.page.html',
  styleUrls: ['./coordinator-reportes.page.scss'],
})
export class CoordinatorReportesPage implements OnInit, OnDestroy {
  private readonly api = inject(CoordinatorApi);
  private readonly destroy$ = new Subject<void>();
  private readonly logger = {
    info: console.log,
    error: console.error,
    warn: console.warn,
  };

  // Estado de carga
  readonly loading = signal(true);
  readonly error = signal<string | null>(null);
  readonly downloadingId = signal<number | null>(null);

  // Métricas principales
  readonly metricas = signal<MetricasPanel>({
    jrvsTotal: 0,
    jrvsProcessed: 0,
    percentageProgress: 0,
    actasPorHora: 0,
    realtimeStatus: 'disconnected',
  });

  // Actas listadas
  readonly actas = signal<ActaItem[]>([]);

  // Progreso por cantón
  readonly cantonProgress = signal<CantonProgress[]>([]);

  // Progreso por cargo
  readonly cargoProgress = signal<CargoProgress[]>([]);

  // Conteo de estados
  readonly estadosCount = signal<EstadosCount>({
    ENVIADA: 0,
    EN_DISPUTA: 0,
    CORREGIDA: 0,
    APROBADA: 0,
    ANULADA: 0,
  });

  // Métricas de resolución
  readonly resolucionesDelDia = signal(0);
  readonly tiempoPromedioHoras = signal(0);

  ngOnInit(): void {
    this.cargarMetricas();
  }

  ngOnDestroy(): void {
    this.destroy$.next();
    this.destroy$.complete();
  }

  /**
   * Carga todas las métricas en paralelo.
   * Usa forkJoin para ejecutar getCargos y getPartidos simultáneamente.
   */
  cargarMetricas(): void {
    this.loading.set(true);
    this.error.set(null);

    forkJoin({
      actas: this.api.getActas(),
      jrvs: this.api.getJrvs(),
      cargos: this.api.getCargos(),
    })
      .pipe(takeUntil(this.destroy$))
      .subscribe({
        next: (data) => {
          // Procesar actas
          const actasData = data.actas.data || [];
          this.actas.set(actasData);

          // Calcular progreso JRVs
          const jrvsTotal = data.jrvs.length;
          const jrvsProcessed = Math.floor(jrvsTotal * 0.68); // Simulado: 219/322
          this.metricas.set({
            jrvsTotal,
            jrvsProcessed,
            percentageProgress: Math.round((jrvsProcessed / jrvsTotal) * 100),
            actasPorHora: this.calcularTasaPorHora(actasData),
            realtimeStatus: 'connected',
          });

          // Calcular estados
          this.calcularEstados(actasData);

          // Calcular progreso por cantón (simulado)
          this.cantonProgress.set([
            { canton: 'Santa Elena', percentage: 70 },
            { canton: 'La Libertad', percentage: 65 },
            { canton: 'Salinas', percentage: 60 },
          ]);

          // Calcular progreso por cargo
          const cargoMap = new Map<number, number>();
          actasData.forEach((a) => {
            if (a.cargoId) {
              cargoMap.set(a.cargoId, (cargoMap.get(a.cargoId) ?? 0) + 1);
            }
          });
          this.cargoProgress.set(
            data.cargos.map((c) => ({
              cargo: c.nombre,
              percentage: Math.min(
                100,
                Math.round(
                  ((cargoMap.get(c.id) ?? 0) / actasData.length) * 100,
                ),
              ),
            })),
          );

          // Métricas de resolución
          this.resolucionesDelDia.set(
            actasData.filter((a) => a.estado === 'APROBADA').length,
          );
          this.tiempoPromedioHoras.set(2.5); // Simulado

          this.loading.set(false);

          this.logger.info('Métricas cargadas exitosamente', {
            totalActas: actasData.length,
            porcentajeAvance: this.metricas().percentageProgress,
          });
        },
        error: (err) => {
          this.loading.set(false);
          this.error.set(
            'No se pudieron cargar las métricas. Intenta nuevamente.',
          );
          this.logger.error('Error cargando métricas', {
            error: err.message,
            status: err.status,
          });
        },
      });
  }

  /**
   * Calcula la tasa de actas procesadas por hora basado en timestamps.
   * Implementación simplificada: cuenta actas con estado APROBADA / últimas 4 horas.
   */
  private calcularTasaPorHora(actas: ActaItem[]): number {
    if (actas.length === 0) return 0;

    const ahora = new Date();
    const hace4Horas = new Date(ahora.getTime() - 4 * 60 * 60 * 1000);

    const actasRecientes = actas.filter((a) => {
      const fecha = new Date(a.fechaRegistro);
      return fecha >= hace4Horas && a.estado === 'APROBADA';
    });

    return Math.round((actasRecientes.length / 4) * 10) / 10;
  }

  /**
   * Calcula conteo de actas por estado.
   */
  private calcularEstados(actas: ActaItem[]): void {
    const count: EstadosCount = {
      ENVIADA: 0,
      EN_DISPUTA: 0,
      CORREGIDA: 0,
      APROBADA: 0,
      ANULADA: 0,
    };

    actas.forEach((a) => {
      if (a.estado in count) {
        count[a.estado as keyof EstadosCount]++;
      }
    });

    this.estadosCount.set(count);
  }

  /**
   * Descarga expediente PDF de un acta.
   * Valida MIME type, genera Object URL temporal y revoca tras descarga.
   * CRITICAL: Siempre revocar URL para evitar memory leak.
   */
  descargarExpediente(acta: ActaItem): void {
    if (!acta || !acta.id) {
      this.error.set('Acta inválida.');
      return;
    }

    this.downloadingId.set(acta.id);

    this.api.descargarExpedientePdf(acta.id).subscribe({
      next: (blob: Blob) => {
        // Validar MIME type
        if (
          !['application/pdf', 'application/octet-stream'].includes(blob.type)
        ) {
          this.error.set('Archivo descargado no es PDF válido.');
          this.logger.warn('Invalid MIME type received', {
            actaId: acta.id,
            type: blob.type,
          });
          this.downloadingId.set(null);
          return;
        }

        // Crear Object URL temporal
        const url = window.URL.createObjectURL(blob);

        // Simular clic en <a> para disparar descarga
        const link = document.createElement('a');
        link.href = url;
        link.download = `expediente-cne-acta-${acta.id}.pdf`;
        link.click();

        // CRITICAL: Revocar URL para liberar memoria
        window.URL.revokeObjectURL(url);

        this.logger.info('Expediente descargado exitosamente', {
          actaId: acta.id,
        });
        this.downloadingId.set(null);
      },
      error: (err) => {
        this.downloadingId.set(null);
        if (err.status === 401) {
          this.error.set('Sesión expirada. Vuelve a autenticarte.');
        } else if (err.status === 403) {
          this.error.set('No tienes permiso para descargar este expediente.');
        } else if (err.status === 404) {
          this.error.set('Acta no encontrada.');
        } else {
          this.error.set('Error al descargar expediente. Intenta nuevamente.');
        }
        this.logger.error('Download failed', {
          actaId: acta.id,
          status: err.status,
          message: err.message,
        });
      },
    });
  }

  /**
   * Retorna color de estado según estado del acta.
   */
  obtenerColorEstado(estado: string): string {
    const colores: Record<string, string> = {
      ENVIADA: '--color-status-info',
      EN_DISPUTA: '--color-status-danger',
      CORREGIDA: '--color-status-warning',
      APROBADA: '--color-status-success',
      ANULADA: '--color-status-neutral',
    };
    return colores[estado] || '--color-text-secondary';
  }

  /**
   * Retorna símbolo de estado.
   */
  obtenerSimboloEstado(estado: string): string {
    const simbolos: Record<string, string> = {
      ENVIADA: '📤',
      EN_DISPUTA: '⚠️',
      CORREGIDA: '✏️',
      APROBADA: '✅',
      ANULADA: '❌',
    };
    return simbolos[estado] || '•';
  }

  /**
   * Helper para acceder a conteo de estados en template.
   */
  obtenerCountEstado(estado: string): number {
    return this.estadosCount()[estado as keyof EstadosCount] || 0;
  }

  /**
   * Helper para calcular porcentaje de estado.
   */
  obtenerPorcentajeEstado(estado: string): number {
    const total = this.actas().length || 1;
    const count = this.obtenerCountEstado(estado);
    return Math.round((count / total) * 100);
  }
}
